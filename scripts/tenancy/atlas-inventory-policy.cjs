"use strict";
const dns = require("node:dns").promises;
const tls = require("node:tls");
const crypto = require("node:crypto");

const policy = Object.freeze({
    organization:"65674bdb19ad1d08d6473d15",project:"65674bdb87e3bc06eba69262",
    cluster:"RepsDevDB",database:"repsdevdb",authDatabase:"admin",
    seed:"repsdevdb.xgrmfbb.mongodb.net",replicaSet:"atlas-bafhxz-shard-0",
    principal:"reps_a7_inventory_reader",
    members:Object.freeze([0,1,2].map(i=>"ac-fykprpn-shard-00-0"+i+".xgrmfbb.mongodb.net"))
});
const digest = crypto.createHash("sha256").update(JSON.stringify(policy)).digest("hex");
const exact = (left,right)=>JSON.stringify([...left].sort())===JSON.stringify([...right].sort());

async function validateDns(resolver=dns) {
    const srv = await resolver.resolveSrv("_mongodb._tcp."+policy.seed);
    if (srv.length!==3 || !exact(srv.map(row=>row.name),policy.members) ||
        srv.some(row=>row.port!==27017)) throw Error("DNS policy mismatch");
    const txt = await resolver.resolveTxt(policy.seed);
    if (txt.length!==1) throw Error("DNS policy mismatch");
    const parts = txt[0].join("").split("&");
    if (!exact(parts,["authSource=admin","replicaSet="+policy.replicaSet])) throw Error("DNS policy mismatch");
    const addresses = {};
    for (const host of policy.members) {
        const rows = await resolver.lookup(host,{all:true});
        if (!rows.length || rows.some(row=>![4,6].includes(row.family))) throw Error("DNS address unavailable");
        addresses[host] = rows;
    }
    return addresses;
}

function approvalValid(approval, now=Date.now()) {
    const fields = ["version","policyDigest","runId","reviewerRef","attestationDigest","issuedAt","expiresAt","clusterRestrictedTemporaryUser"];
    if (!approval || !exact(Object.keys(approval),fields) || approval.version!=="reps-atlas-approval/v1" ||
        approval.policyDigest!==digest || !/^[a-zA-Z0-9-]{8,80}$/.test(approval.runId) ||
        !/^[a-zA-Z0-9-]{3,80}$/.test(approval.reviewerRef) ||
        !/^[a-f0-9]{64}$/.test(approval.attestationDigest) || approval.clusterRestrictedTemporaryUser!==true ||
        !Number.isFinite(Date.parse(approval.issuedAt)) || !Number.isFinite(Date.parse(approval.expiresAt)) ||
        Date.parse(approval.issuedAt)>now || Date.parse(approval.expiresAt)<=now ||
        Date.parse(approval.expiresAt)-Date.parse(approval.issuedAt)>3600000) {
        throw Error("Run approval refused");
    }
}

function connectionOptions(addresses,password,abort,resolver=dns) {
    return {
        auth:{username:policy.principal,password},authSource:policy.authDatabase,
        replicaSet:policy.replicaSet,tls:true,rejectUnauthorized:true,
        directConnection:false,readPreference:"primary",
        maxPoolSize:1,maxConnecting:1,connectTimeoutMS:5000,serverSelectionTimeoutMS:10000,
        socketTimeoutMS:10000,retryReads:false,retryWrites:false,
        monitorCommands:false,mongodbLogComponentSeverities:{default:"off"},
        checkServerIdentity(host,certificate) {
            if (!policy.members.includes(host)) { abort(); return Error("TLS destination refused"); }
            return tls.checkServerIdentity(host,certificate);
        },
        lookup(host,options,callback) {
            if (!Object.hasOwn(addresses,host)) { abort(); callback(Error("Destination refused")); return; }
            resolver.lookup(host,{all:true}).then(rows=>{
                if (!exact(rows.map(row=>row.address+"/"+row.family),
                    addresses[host].map(row=>row.address+"/"+row.family))) throw Error();
                if (options?.all) callback(null,rows);
                else {
                    const row = rows.find(row=>!options?.family || row.family===options.family);
                    if (!row) throw Error();
                    callback(null,row.address,row.family);
                }
            }).catch(()=>{abort();callback(Error("DNS address changed"));});
        }
    };
}

function topologyValid(description, requireFull=false) {
    if(requireFull && description.servers.size!==3) return false;
    for (const [host,server] of description.servers) {
        if (!policy.members.some(member=>host===member+":27017")) return false;
        if (server.setName && server.setName!==policy.replicaSet) return false;
        const advertised = [...(server.hosts||[]),...(server.passives||[]),...(server.arbiters||[])];
        if (advertised.some(address=>!policy.members.some(member=>address===member+":27017"))) return false;
        if (server.setName && (advertised.length!==3 || new Set(advertised).size!==3)) return false;
        if (server.type==="Mongos" || server.type==="LoadBalancer") return false;
    }
    return true;
}
module.exports = {policy,digest,validateDns,approvalValid,connectionOptions,topologyValid};
