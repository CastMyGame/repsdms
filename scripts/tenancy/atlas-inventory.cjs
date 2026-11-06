"use strict";
const dns = require("node:dns").promises;
const {policy,validateDns,connectionOptions,topologyValid} = require("./atlas-inventory-policy.cjs");
const aggregate = require("./dev-inventory.cjs");
const {publish} = require("./inventory-safety.cjs");
const {requirePrivileges} = require("./dev-inventory-policy.cjs");
const roots = aggregate.collections.filter(name=>name!=="schools");
const zero = {total:0,missing:0,invalid:0,conflicting:0,present:0};
const schema = {version:"reps-atlas-inventory/v1",consistency:"non-transactional-scan",
    schools:{total:0,exactCollisionGroups:0,normalizedCollisionGroups:0},
    ownership:Object.fromEntries(roots.map(name=>[name,zero]))};

function grantsValid(status) {
    // Reuse the exact-resource validator after checking the real authenticated identity.
    if (status?.ok!==1 || status.authInfo?.authenticatedUsers?.length!==1 ||
        status.authInfo.authenticatedUsers[0].user!==policy.principal ||
        status.authInfo.authenticatedUsers[0].db!==policy.authDatabase) throw Error();
    const local = require("./dev-inventory-policy.cjs");
    const translated = status.authInfo.authenticatedUserPrivileges?.map(({resource,actions})=>{
        if (resource?.db!==policy.database) throw Error();
        return {resource:{...resource,db:local.database},actions};
    });
    requirePrivileges({ok:1,authInfo:{authenticatedUsers:[{user:local.principal,db:local.authDatabase}],
        authenticatedUserPrivileges:translated}},aggregate.collections);
}

async function run({acknowledged,password,output,makeClient,resolver=dns,publishReport=publish,deadlineMS=90000}) {
    let client, stage="preflight", stopped=false,lastTopology;
    const abort=()=>{stopped=true;void client?.close().catch(()=>{});};
    const timer=setTimeout(abort,deadlineMS);
    try {
        // Accidental-run safeguard. Owner approval is documented separately.
        if(acknowledged!==true) throw Error();
        if (typeof password!=="string" || !password || password.length>1024) throw Error();
        stage="DNS validation";
        const addresses=await validateDns(resolver);
        if(stopped) throw Error();
        stage="TLS and authentication";
        const options=connectionOptions(addresses,password,abort,resolver);
        client=await makeClient("mongodb+srv://"+policy.seed+"/"+policy.database+"?loadBalanced=false",options);
        client.on("topologyDescriptionChanged",event=>{
            lastTopology=event.newDescription;
            if(!topologyValid(event.newDescription,true)) abort();
        });
        client.on("serverDescriptionChanged",event=>{
            const server=event.newDescription;
            if(!topologyValid({servers:new Map([[event.address,server]])})) abort();
        });
        await client.connect();
        stage="replica identity";
        const hello=await client.db("admin").command({hello:1});
        if(hello.setName!==policy.replicaSet || !hello.isWritablePrimary ||
            !policy.members.some(host=>hello.me===host+":27017") ||
            hello.hosts?.length!==3 ||
            new Set(hello.hosts).size!==3 ||
            hello.hosts.some(host=>!policy.members.some(member=>host===member+":27017")) ||
            hello.passives?.length || hello.arbiters?.length) throw Error();
        stage="effective privileges";
        grantsValid(await client.db("admin").command({connectionStatus:1,showPrivileges:true}));
        stage="aggregate scan";
        const report={...schema,ownership:{}};
        const db=client.db(policy.database);
        for(const name of aggregate.collections) {
            if(stopped) throw Error();
            const shape=name==="schools"?schema.schools:zero;
            const pipeline=name==="schools"?aggregate.schoolPipeline():aggregate.ownershipPipeline();
            const rows=await db.collection(name).aggregate(pipeline,{
                maxTimeMS:5000,allowDiskUse:false,collation:{locale:"simple"}
            }).toArray();
            const row=rows[0]??shape;
            if(rows.length>1 || Object.keys(row).length!==Object.keys(shape).length ||
                !Object.keys(shape).every(key=>Object.hasOwn(row,key)&&Number.isSafeInteger(row[key])&&row[key]>=0)) throw Error();
            if(name==="schools") report.schools=row;
            else {
                if(row.total!==row.missing+row.invalid+row.conflicting+row.present) throw Error();
                report.ownership[name]=row;
            }
        }
        stage="final validation";
        const finalAddresses=await validateDns(resolver);
        for(const host of policy.members) {
            const values=rows=>rows.map(row=>row.address+"/"+row.family).sort().join(",");
            if(values(addresses[host])!==values(finalAddresses[host])) throw Error();
        }
        if(stopped || !lastTopology || !topologyValid(lastTopology,true)) throw Error();
        stage="publication";
        publishReport(output,report,schema);
    } catch(_) {throw Error("Inventory failed during "+stage+".");}
    finally {clearTimeout(timer);await client?.close().catch(()=>{});}
}
module.exports={run,grantsValid,schema};
