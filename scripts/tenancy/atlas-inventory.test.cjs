"use strict";
const test=require("node:test");
const assert=require("node:assert/strict");
const {EventEmitter}=require("node:events");
const p=require("./atlas-inventory-policy.cjs");
const {run,grantsValid,schema}=require("./atlas-inventory.cjs");
const collections=["schools",...Object.keys(schema.ownership)];
const resolver=()=>({
    resolveSrv:async()=>p.policy.members.map(name=>({name,port:27017})),
    resolveTxt:async()=>[["replicaSet="+p.policy.replicaSet+"&authSource=admin"]],
    lookup:async()=>[{address:"127.0.0.2",family:4}]
});
const status=()=>({ok:1,authInfo:{authenticatedUsers:[{user:p.policy.principal,db:"admin"}],
    authenticatedUserPrivileges:collections.map(collection=>({resource:{db:"repsdevdb",collection},actions:["find"]}))}});

test("exact SRV/TXT policy rejects routing changes",async()=>{
    await p.validateDns(resolver());
    for(const changed of [
        {resolveSrv:async()=>[{name:"reps.risjic2.mongodb.net",port:27017}]},
        {resolveSrv:async()=>p.policy.members.map(name=>({name,port:27018}))},
        {resolveTxt:async()=>[["authSource=admin&replicaSet=wrong"]]},
        {resolveTxt:async()=>[["authSource=admin&replicaSet="+p.policy.replicaSet+"&loadBalanced=true"]]},
        {resolveTxt:async()=>[]}
    ]) await assert.rejects(p.validateDns({...resolver(),...changed}));
});

test("missing operator acknowledgement refuses before DNS or connections",async()=>{
    await assert.rejects(run({resolver:{resolveSrv(){assert.fail("DNS accessed");}},
        makeClient(){assert.fail("connected");}}),{message:"Inventory failed during preflight."});
});

test("all socket lookups reject unknown hosts and changed IPs",async()=>{
    const addresses=await p.validateDns(resolver());
    let aborted=0;
    const options=p.connectionOptions(addresses,"fixture",()=>aborted++,resolver());
    const lookup=host=>new Promise(resolve=>options.lookup(host,{all:true},error=>resolve(error)));
    assert.equal(await lookup(p.policy.members[0]),null);
    assert(await lookup("reps.risjic2.mongodb.net"));
    const changed=p.connectionOptions(addresses,"fixture",()=>aborted++,{lookup:async()=>[{address:"127.0.0.9",family:4}]});
    assert(await new Promise(resolve=>changed.lookup(p.policy.members[0],{},error=>resolve(error))));
    assert.equal(aborted,2);
    assert(options.checkServerIdentity("unknown.invalid",{}));
    assert.equal(options.tls,true);
    assert.equal(options.rejectUnauthorized,true);
});

test("reject missing, excessive, wildcard and forged effective grants",()=>{
    grantsValid(status());
    for(const change of [
        s=>{s.authInfo.authenticatedUsers[0].user="forged";},
        s=>{s.authInfo.authenticatedUserPrivileges.pop();},
        s=>{s.authInfo.authenticatedUserPrivileges[0].resource.collection="";},
        s=>{s.authInfo.authenticatedUserPrivileges[0].actions.push("insert");},
        s=>{s.authInfo.authenticatedUserPrivileges[0].resource.db="other";}
    ]) {const s=status();change(s);assert.throws(()=>grantsValid(s));}
});

test("topology rejects unexpected members, routers and wrong replica sets",()=>{
    assert.equal(p.topologyValid({servers:new Map()},true),false);
    for(const description of [{setName:"wrong"},{type:"Mongos"},{hosts:["unapproved:27017"]}]) {
        assert.equal(p.topologyValid({servers:new Map([[p.policy.members[0]+":27017",description]])}),false);
    }
});

test("unexpected report fields are sanitized and cannot publish",async()=>{
    class Client extends EventEmitter {
        async connect(){this.emit("topologyDescriptionChanged",{newDescription:{
            servers:new Map(p.policy.members.map(host=>[host+":27017",{}]))}});}
        async close(){}
        db(){return {command:async command=>command.hello?{setName:p.policy.replicaSet,isWritablePrimary:true,
            me:p.policy.members[0]+":27017",hosts:p.policy.members.map(host=>host+":27017")}:status(),
            collection:()=>({aggregate:()=>({toArray:async()=>[{privateMarker:"never-publish"}]})})};}
    }
    await assert.rejects(run({acknowledged:true,password:"fixture",resolver:resolver(),makeClient:()=>new Client(),
        publishReport(){assert.fail("published");}}),{message:"Inventory failed during aggregate scan."});
});
