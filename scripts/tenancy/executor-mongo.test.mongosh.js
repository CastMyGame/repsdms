// Real local MongoDB rehearsal only; no Spring Boot, credentials or external providers.
(async function(){
    const fs=require("node:fs"),path=require("node:path"),crypto=require("node:crypto"),assert=require("node:assert/strict"),cp=require("node:child_process");
    const base=process.env.REPS_LOCAL_EXECUTOR_TOOL_DIR||"/audit/scripts/tenancy";const {clone:structuredClone}=require(path.join(base,"shell-runtime.cjs"));
    const ex=require(path.join(base,"local-executor.cjs")),core=require(path.join(base,"audit-core.cjs")),{fixture,KEY}=require(path.join(base,"synthetic-fixture.cjs")),{adapter}=require(path.join(base,"mongo-executor-adapter.cjs"));
    const schema=JSON.parse(fs.readFileSync(path.resolve(base,"../../docs/tenancy/manifest.schema.json"),"utf8"));
    const directory=fs.mkdtempSync("/tmp/reps-a7-executor-");fs.chmodSync(directory,0o700);let passed=0,failed=0;const details=[];
    const connection=await new Mongo(ex.URI);
    async function setup(atomicity="per-record"){
        const database="reps_a7_executor_"+crypto.randomBytes(16).toString("hex"),db=connection.getDB(database),f=fixture();
        for(const t of ["A","B"]){
            const email=t.toLowerCase()+"-guide@example.test",roles=[{role:"GUIDANCE"}];
            f.docs.users.push({_id:t+"-guide",username:email,password:"synthetic-password",school:"Same School",roles});
            f.docs.employee.push({_id:t+"-guide",email,school:"Same School",roles,classes:[]});
            f.docs.students.find(d=>d._id===t+"-child").guidanceEmail=email;
            f.docs.Guidance.find(d=>d._id===t+"-guidance").responsibleStaffEmail=email;
            f.docs.Punishments.find(d=>d._id===t+"-case").historicalAccess=[{formatVersion:1,tenantSchoolId:"school-"+t,accountId:t+"-staff",profileId:t+"-staff",basis:"AUTHOR",caseId:t+"-case",kind:"punishment",capturedAt:new Date("2026-01-01T00:00:00Z"),initialDescription:["Synthetic historical facts"]}];
        }
        // Independent B-school duplicates/orphans remain preserved/quarantined, never first-match adopted.
        f.docs.students.push({...f.docs.students[1],_id:"ambiguous-B-child"});
        f.docs.Punishments.push({_id:"orphan-case",studentEmail:"missing@example.test",school:"Same School"});
        f.docs.students.push({_id:"conflicting-child",studentEmail:"conflict@example.test",tenantSchoolId:"nonexistent-school",school:"Same School"});
        for(const c of core.ROOTS)for(const doc of f.docs[c])doc.revision=0;
        await db.getCollection("schools").insertMany(f.schools);
        for(const c of core.COLLECTIONS)if(f.docs[c].length)await db.getCollection(c).insertMany(f.docs[c]);
        const marker={_id:"local-disposable/v1",purpose:"a7-phase5c-synthetic",database,nonce:crypto.randomBytes(32).toString("hex"),expiresAt:Date.now()+3600000};
        await db.getCollection(ex.CONTROL).insertOne(marker);
        const r={mode:"dry-run",atomicity,uri:ex.URI,database,auditKey:KEY,approvalKey:crypto.randomBytes(32),schema},a=adapter(connection,database,EJSON);
        const raw=await a.read();r.snapshot=ex.inventory(raw,{...r,snapshot:{sourceId:"synthetic-mongo-rehearsal"}});
        r.manifest=structuredClone(f.manifest);Object.assign(r.manifest,{sourceId:r.snapshot.sourceId,inventoryDigest:r.snapshot.inventoryDigest,targetTag:r.snapshot.targetTag,keyTag:r.snapshot.keyTag});
        for(const c of ["users","employee"]){const key=f.key(c,"A-guide"),row=r.snapshot.records.find(x=>x.recordKey===key);r.manifest.entries.push({recordKey:key,beforeDigest:row.beforeDigest,protectedDigest:row.protectedDigest,tenantSchoolId:"school-A",mode:"bind-root",evidence:{kind:"reviewed-historical-record",reference:"synthetic-reviewed-guidance-membership",validationStatus:"reviewed"}});}
        const history=r.snapshot.records.find(x=>x.recordKey===f.key("Punishments","A-case")).components.find(c=>c.kind==="historical-access");
        r.manifest.components.push({componentKey:history.componentKey,beforeDigest:history.beforeDigest,tenantSchoolId:"school-A",accountKey:f.key("users","A-staff"),profileKey:f.key("employee","A-staff"),evidence:{kind:"reviewed-historical-record",reference:"synthetic-reviewed-immutable-as-of-provenance",validationStatus:"reviewed"}});
        for(const e of r.manifest.entries){const row=r.snapshot.records.find(x=>x.recordKey===e.recordKey);e.beforeDigest=row.beforeDigest;e.protectedDigest=row.protectedDigest;}
        for(const e of r.manifest.components)e.beforeDigest=r.snapshot.records.flatMap(x=>x.components).find(x=>x.componentKey===e.componentKey).beforeDigest;
        r.manifest=structuredClone(r.manifest);r.plan=core.plan(r.snapshot,r.manifest);
        const p={schema:"reps-local-migration-approval/v1",scope:"validate-only",reference:"approved-synthetic-local-rehearsal",runId:crypto.randomUUID(),issuedAt:Date.now(),expiresAt:Date.now()+1800000,uri:r.uri,database,
            markerDigest:core.sha(structuredClone(EJSON.serialize(marker,{relaxed:false}))),manifestDigest:core.sha(r.manifest),planDigest:r.plan.planDigest,schemaDigest:core.sha(structuredClone(schema)),sourceDigest:ex.sourceState(raw,r),atomicity,writersStopped:true};
        r.approval=ex.sign(p,r.approvalKey);let fd;
        const journal=path.join(directory,crypto.randomBytes(12).toString("hex")+".ndjson");
        fd=fs.openSync(journal,"wx",0o600);const sink={write:e=>{fs.writeSync(fd,JSON.stringify(e)+"\n");fs.fsyncSync(fd);}};
        return {r,a,raw,db,journal,sink,approve(){r.mode="execute";r.approval.payload.scope="execute-root-ownership";r.approval=ex.sign(r.approval.payload,r.approvalKey);},async cleanup(){try{await a.close();fs.closeSync(fd);}finally{if(/^reps_a7_executor_[0-9a-f]{32}$/.test(database))await db.dropDatabase();}}};
    }
    async function test(name,body,atomicity){let f;try{f=await setup(atomicity);await body(f);passed++;print("PASS "+name);details.push({name,status:"passed"});}catch(error){failed++;const frames=String(error.stack||"").split("\n").filter(line=>line.includes("at ")&&line.includes("/audit/")).slice(0,3);print("FAIL "+name+" "+JSON.stringify({type:error.name,code:error.safeCode||error.code||null,actual:typeof error.actual==="number"||typeof error.actual==="string"&&/^[A-Z_]+$/.test(error.actual)?error.actual:null,frames}));details.push({name,status:"failed"});}finally{if(f)await f.cleanup();}}
    function publicEvents(f){return fs.readFileSync(f.journal,"utf8");}
    await test("dry-run does not mutate any document",async f=>{const before=core.sha(f.raw),report=await ex.run(f.r,f.a,f.sink);assert.equal(report.status,"DRY_RUN_VALIDATED");assert.equal(report.committed,0);assert.equal(core.sha(await f.a.read()),before);});
    for(const mode of ["per-record","transaction"])await test("successful "+mode+" execution, preserved BSON and idempotent rerun",async f=>{
        f.approve();const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.status,"COMPLETED");assert.equal(out.committed,12);
        const after=await f.a.read();assert.deepEqual(after.immutableHashes,f.raw.immutableHashes);assert.equal(ex.sourceState(after,f.r),ex.sourceState(f.raw,f.r));
        for(const c of ["currency_operations","oauth2_authorized_clients","registration_intents"])assert.deepEqual(after[c],f.raw[c]);
        const again=await ex.run(f.r,f.a,f.sink);assert.equal(again.status,"COMPLETED");assert.equal(again.committed,0);assert.equal(again.alreadyBound,12);
        assert.ok(f.r.plan.quarantine.length>=18);const own=f.r.plan.updates.map(x=>x.recordKey);
        for(const c of core.ROOTS)for(const doc of after[c]){const old=f.raw[c].find(d=>core.stable(d._id)===core.stable(doc._id));const clone=structuredClone(doc);if(own.includes(ex.recordKey(c,doc._id,f.r.auditKey)))delete clone.tenantSchoolId;assert.deepEqual(clone,old);}
    },mode);
    await test("changed full account field rejects stale approval",async f=>{f.approve();await f.db.getCollection("users").updateOne({_id:"A-staff"},{$set:{password:"different-synthetic-password"}});const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.status,"FAILED_PRECONDITION");assert.equal(out.committed,0);});
    await test("missing or forged disposable marker stops before source reads",async f=>{f.approve();await f.db.getCollection(ex.CONTROL).deleteOne({_id:"local-disposable/v1"});let reads=0;const read=f.a.read;f.a.read=async()=>{reads++;return read();};const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.reason,"UNKNOWN_DISPOSABLE_TARGET");assert.equal(reads,0);});
    await test("concurrent target revision is rejected by exact BSON CAS",async f=>{f.approve();const update=f.a.update;let changed=false;f.a.update=async u=>{if(!changed){changed=true;await f.db.getCollection(u.collection).updateOne({_id:EJSON.deserialize(u.document)._id},{$set:{revision:999}});}return update(u);};const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.reason,"CONDITIONAL_CONFLICT");assert.equal(out.committed,0);});
    await test("numerically equal BSON type changes cannot bypass CAS",async f=>{f.approve();const update=f.a.update;let changed=false;f.a.update=async u=>{if(!changed){changed=true;await f.db.getCollection(u.collection).updateOne({_id:EJSON.deserialize(u.document)._id},[{$set:{revision:{$toDecimal:"$revision"}}}]);}return update(u);};const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.reason,"CONDITIONAL_CONFLICT");assert.equal(out.committed,0);});
    await test("transactional concurrent writer causes rollback without overwriting its revision",async f=>{f.approve();const update=f.a.update;let target;f.a.update=async u=>{if(!target){target={collection:u.collection,id:EJSON.deserialize(u.document)._id};await f.db.getCollection(target.collection).updateOne({_id:target.id},{$set:{revision:999}});}return update(u);};const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.status,"ROLLED_BACK");assert.equal(out.committed,0);const changed=await f.db.getCollection(target.collection).findOne({_id:target.id});assert.equal(changed.revision,999);assert.equal(changed.tenantSchoolId,undefined);},"transaction");
    await test("historical receipt drift blocks execution without a new award",async f=>{f.approve();const id=f.raw.currency_operations[0]._id;await f.db.getCollection("currency_operations").updateOne({_id:EJSON.deserialize(id)},{$set:{teacherBalance:999}});const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.status,"FAILED_PRECONDITION");assert.equal(out.committed,0);});
    await test("nontransactional interruption reports two commits and resumes the remaining ten",async f=>{f.approve();const update=f.a.update;let n=0;f.a.update=async u=>{if(n===2){const e=new Error();e.safeCode="SIMULATED_INTERRUPT";throw e;}n++;return update(u);};const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.status,"FAILED_PARTIAL");assert.equal(out.committed,2);f.a.update=update;const again=await ex.run(f.r,f.a,f.sink);assert.equal(again.alreadyBound,2);assert.equal(again.committed,10);});
    await test("transaction failure rolls back staged ownership",async f=>{f.approve();const update=f.a.update;let n=0;f.a.update=async u=>{if(n===2){const e=new Error();e.safeCode="SIMULATED_INTERRUPT";throw e;}n++;return update(u);};const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.status,"ROLLED_BACK");assert.equal(out.attempted,3);assert.equal(out.committed,0);assert.deepEqual(await f.a.read(),f.raw);},"transaction");
    await test("unknown commit after real commit never reruns body",async f=>{f.approve();const commit=f.a.commit;f.a.commit=async()=>{await commit();const e=new Error();e.errorLabels=["UnknownTransactionCommitResult"];throw e;};const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.status,"UNCERTAIN");assert.equal(out.committed,null);f.a.commit=commit;const again=await ex.run(f.r,f.a,f.sink);assert.equal(again.committed,0);assert.equal(again.alreadyBound,12);},"transaction");
    await test("a normal rerun cannot move ownership to another school",async f=>{f.approve();assert.equal((await ex.run(f.r,f.a,f.sink)).status,"COMPLETED");await f.db.getCollection("employee").updateOne({_id:"A-staff"},{$set:{tenantSchoolId:"school-B"}});assert.equal((await ex.run(f.r,f.a,f.sink)).status,"FAILED_PRECONDITION");});
    await test("notification state drift blocks source preconditions",async f=>{f.approve();await f.db.getCollection("Punishments").updateOne({_id:"A-case"},{$set:{"requiredNotifications.0.state":"ACCEPTED"}});assert.equal((await ex.run(f.r,f.a,f.sink)).status,"FAILED_PRECONDITION");});
    await test("unknown collection cannot be silently omitted",async f=>{f.approve();await f.db.getCollection("unexpected_records").insertOne({_id:"synthetic"});assert.equal((await ex.run(f.r,f.a,f.sink)).reason,"UNKNOWN_COLLECTION");});
    await test("actual process exit after two writes resumes from BSON, not journal",async f=>{
        f.approve();const file=path.join(directory,"crash-request.json");fs.writeFileSync(file,JSON.stringify({...f.r,auditKey:undefined,approvalKey:undefined}),{mode:0o600});
        const childScript=path.join(directory,"crash-child.js");fs.writeFileSync(childScript,`(async()=>{const fs=require('fs');require('${base}/shell-runtime.cjs');const ex=require('${base}/local-executor.cjs'),{adapter}=require('${base}/mongo-executor-adapter.cjs');const r=JSON.parse(fs.readFileSync('${file}','utf8'));r.auditKey=ex.key(process.env.REPS_TENANT_AUDIT_KEY);r.approvalKey=ex.key(process.env.REPS_LOCAL_EXECUTOR_APPROVAL_KEY);const conn=await new Mongo(r.uri),a=adapter(conn,r.database,EJSON);let n=0;const write=a.update;a.update=async u=>{const result=await write(u);if(++n===2)process.exit(82);return result;};const fd=fs.openSync('${f.journal}.crash','wx',0o600);await ex.run(r,a,{write:e=>{fs.writeSync(fd,JSON.stringify(e)+'\\n');fs.fsyncSync(fd);}});process.exit(0);})();`,{mode:0o600});
        const child=cp.spawnSync("mongosh",["--nodb","--norc","--quiet","--file",childScript],{encoding:"utf8",timeout:60000,env:{...process.env,REPS_TENANT_AUDIT_KEY:f.r.auditKey.toString("base64"),REPS_LOCAL_EXECUTOR_APPROVAL_KEY:f.r.approvalKey.toString("base64")}});
        if(child.status!==82)print(JSON.stringify({crashChildStatus:child.status,errorKind:String(child.stderr||child.stdout).match(/[A-Za-z]+Error/)?.[0]||null}));assert.equal(child.status,82);const out=await ex.run(f.r,f.a,f.sink);assert.equal(out.status,"COMPLETED");assert.equal(out.alreadyBound,2);assert.equal(out.committed,10);
        const journal=fs.readFileSync(f.journal+".crash","utf8");assert.equal(journal.trim().split("\n").map(JSON.parse).filter(e=>e.event==="APPLIED").length,1);
    });
    await test("real CLI default, missing execution approval refusal and explicit signed execution",async f=>{
        const files={};for(const field of ["snapshot","manifest","plan","approval"]){files[field]=path.join(directory,field+".json");fs.writeFileSync(files[field],JSON.stringify(f.r[field]),{mode:0o600});}
        const report=path.join(directory,"cli-report.ndjson"),env={...process.env,REPS_LOCAL_EXECUTOR_TOOL_DIR:base,REPS_LOCAL_EXECUTOR_URI:f.r.uri,REPS_LOCAL_EXECUTOR_DATABASE:f.r.database,REPS_TENANT_AUDIT_KEY:f.r.auditKey.toString("base64"),REPS_LOCAL_EXECUTOR_APPROVAL_KEY:f.r.approvalKey.toString("base64"),REPS_LOCAL_EXECUTOR_ATOMICITY:"per-record",REPS_LOCAL_EXECUTOR_REPORT:report};delete env.REPS_LOCAL_EXECUTOR_MODE;
        for(const field of ["snapshot","manifest","plan","approval"])env["REPS_LOCAL_EXECUTOR_"+field.toUpperCase()]=files[field];
        const child=cp.spawnSync("mongosh",["--nodb","--norc","--quiet","--file",path.join(base,"execute-local.mongosh.js")],{encoding:"utf8",timeout:60000,env});assert.equal(child.status,0);assert.ok(child.stdout.includes("DRY_RUN_VALIDATED"));assert.deepEqual(await f.a.read(),f.raw);
        env.REPS_LOCAL_EXECUTOR_MODE="execute";env.REPS_LOCAL_EXECUTOR_REPORT=path.join(directory,"must-not-execute.ndjson");
        const denied=cp.spawnSync("mongosh",["--nodb","--norc","--quiet","--file",path.join(base,"execute-local.mongosh.js")],{encoding:"utf8",timeout:60000,env});assert.equal(denied.status,1);assert.ok(!fs.existsSync(env.REPS_LOCAL_EXECUTOR_REPORT));assert.deepEqual(await f.a.read(),f.raw);
        f.approve();fs.writeFileSync(files.approval,JSON.stringify(f.r.approval),{mode:0o600});env.REPS_LOCAL_EXECUTOR_REPORT=path.join(directory,"approved-cli-execution.ndjson");
        const executed=cp.spawnSync("mongosh",["--nodb","--norc","--quiet","--file",path.join(base,"execute-local.mongosh.js")],{encoding:"utf8",timeout:60000,env});assert.equal(executed.status,0);assert.ok(executed.stdout.includes("COMPLETED"));const after=await f.a.read();assert.equal(ex.validateSource(after,f.r).already,12);
        for(const secret of [f.r.approvalKey.toString("base64"),"PrivateSynthetic","@example.test"])assert.ok(!child.stdout.includes(secret)&&!fs.readFileSync(report,"utf8").includes(secret));
    });
    const summary={schema:"reps-local-executor-rehearsal/v1",passed,failed,details,dbReads:"disposable-local-only",realProviderCalls:0,canonicalActivation:false};
    fs.writeFileSync("/tmp/a7-executor-results.json",JSON.stringify(summary,null,2)+"\n",{mode:0o600});print(JSON.stringify({passed,failed}));if(failed)exit(1);
})().catch(_=>{print("Rehearsal failed safely; source details withheld.");exit(1);});
