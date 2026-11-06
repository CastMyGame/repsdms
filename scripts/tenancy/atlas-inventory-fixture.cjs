async function runFixture(){
    const assert=require("node:assert/strict"),fs=require("node:fs"),dns=require("node:dns");
    const {policy,digest}=require("/tools/atlas-inventory-policy.cjs");
    // Synthetic DNS injection is confined to this offline test process.
    dns.promises.resolveSrv=async()=>policy.members.map(name=>({name,port:27017,priority:0,weight:0}));
    dns.promises.resolveTxt=async()=>[["authSource=admin&replicaSet="+policy.replicaSet]];
    dns.promises.resolve=async(name,type)=>{
        if(type==="SRV" && name==="_mongodb._tcp."+policy.seed) return dns.promises.resolveSrv(name);
        if(type==="TXT" && name===policy.seed) return dns.promises.resolveTxt(name);
        throw Error("Unexpected synthetic DNS query");
    };
    const {MongoClient}=require("/tools/atlas-runtime/node_modules/mongodb");
    const {run}=require("/tools/atlas-inventory.cjs");
    const {collections}=require("/tools/dev-inventory.cjs");
    const ca=fs.readFileSync("/tmp/atlas-fixture/ca.crt");
    const direct=new MongoClient("mongodb://"+policy.members[0]+":27017/admin",{
        directConnection:true,tls:true,ca,serverSelectionTimeoutMS:5000});
    await direct.connect();
    await direct.db("admin").command({replSetInitiate:{_id:policy.replicaSet,
        settings:{electionTimeoutMillis:1000,catchUpTakeoverDelayMillis:1000},members:policy.members.map((host,i)=>({_id:i,host:host+":27017",priority:i===0?2:1}))}});
    let primary=false;
    for(let i=0;i<100;i++){
        if((await direct.db("admin").command({hello:1})).isWritablePrimary){primary=true;break;}
        await new Promise(resolve=>setTimeout(resolve,200));
    }
    assert(primary);
    await direct.db("admin").command({createUser:"fixture_admin",pwd:process.env.REPS_ATLAS_FIXTURE_ADMIN,roles:["root"]});
    await direct.close();
    const admin=new MongoClient("mongodb://"+policy.members.join(",")+"/admin",{
        replicaSet:policy.replicaSet,tls:true,ca,auth:{username:"fixture_admin",password:process.env.REPS_ATLAS_FIXTURE_ADMIN}});
    await admin.connect();
    try{
        const db=admin.db(policy.database);
        for(const name of collections) await db.createCollection(name);
        await db.collection("schools").insertMany([{_id:"A",schoolName:"private-marker"},{_id:"B",schoolName:"private-marker"}]);
        await db.collection("students").insertMany([{tenantSchoolId:"A",email:"private-marker@example.invalid"},
            {tenantSchoolId:"B"},{tenantSchoolId:"unknown"},{}]);
        await admin.db("admin").command({createRole:"fixture_find",privileges:collections.map(collection=>({
            resource:{db:policy.database,collection},actions:["find"]})),roles:[]});
        await admin.db("admin").command({createUser:policy.principal,pwd:process.env.REPS_ATLAS_FIXTURE_READER,roles:["fixture_find"]});
        const snapshot=async()=>JSON.stringify({hash:(await db.command({dbHash:1})).md5,
            indexes:await Promise.all(collections.map(name=>db.collection(name).listIndexes().toArray()))});
        const before=await snapshot();
        const approval={version:"reps-atlas-approval/v1",policyDigest:digest,runId:"synthetic-run",
            reviewerRef:"test-reviewer",attestationDigest:"a".repeat(64),issuedAt:new Date(Date.now()-1000).toISOString(),
            expiresAt:new Date(Date.now()+60000).toISOString(),clusterRestrictedTemporaryUser:true};
        fs.mkdirSync("/tmp/atlas-approval",{mode:0o700});
        const {reserve}=require("/tools/atlas-run-files.cjs");
        reserve(approval,"/tmp/atlas-approval/approval.json","/tmp/atlas-output/report.json");
        assert.throws(()=>reserve(approval,"/tmp/atlas-approval/approval.json","/tmp/atlas-output/report.json"));
        assert.equal(fs.statSync("/tmp/atlas-approval/.run-"+approval.runId).mode&0o777,0o600);
        const args={approval,password:process.env.REPS_ATLAS_FIXTURE_READER,output:"/tmp/atlas-output/report.json",
            makeClient:(uri,options)=>new MongoClient(uri,{...options,ca})};
        await run(args);
        const raw=fs.readFileSync(args.output,"utf8"),report=JSON.parse(raw);
        assert(!raw.includes("private-marker"));
        assert.deepEqual(report.ownership.students,{total:4,missing:1,invalid:1,conflicting:0,present:2});
        assert.equal(fs.statSync(args.output).mode&0o777,0o600);
        await assert.rejects(run(args),{message:"Inventory failed during publication."});
        assert.equal(raw,fs.readFileSync(args.output,"utf8"));
        assert(!fs.readdirSync("/tmp/atlas-output").some(name=>name.startsWith(".inventory-")));
        const rejected={...args,output:"/tmp/atlas-output/refused.json"};
        await assert.rejects(run({...rejected,password:"incorrect-fixture-input"}));
        await assert.rejects(run({...rejected,makeClient:(uri,options)=>new MongoClient(uri,{
            ...options,ca:fs.readFileSync("/tmp/atlas-fixture/wrong.crt")})}));
        await admin.db("admin").command({grantRolesToUser:policy.principal,roles:[{role:"readWrite",db:policy.database}]});
        await assert.rejects(run(rejected),{message:"Inventory failed during effective privileges."});
        assert(!fs.existsSync(rejected.output));
        assert.equal(await snapshot(),before);
        console.log("Atlas Option B synthetic replica-set rehearsal passed.");
    }finally{await admin.close();}
}
module.exports={runFixture};
