"use strict";
const test=require("node:test"),assert=require("node:assert/strict"),fs=require("node:fs"),os=require("node:os"),path=require("node:path"),vm=require("node:vm"),cp=require("node:child_process");
const core=require("./audit-core.cjs"),{fixture,KEY}=require("./synthetic-fixture.cjs");
function removeTemp(directory){const root=fs.realpathSync(os.tmpdir()),resolved=fs.realpathSync(directory);assert.ok(resolved.toLowerCase().startsWith(root.toLowerCase()+path.sep));assert.ok(path.basename(resolved).startsWith("reps-a7-"));fs.rmSync(resolved,{recursive:true});}
test("same-name schools are discovered but no owner is inferred",()=>{const f=fixture(),s=core.summarize(f.snapshot);assert.equal(s.duplicateSchoolNameGroups.length,1);assert.ok(f.snapshot.records.every(r=>r.tenantSchoolId===null));assert.equal(s.counts.students.ambiguousName,2);});
test("reviewed pilot mapping preserves counts and leaves other-school data unmapped",()=>{const f=fixture(),p=core.plan(f.snapshot,f.manifest);assert.equal(p.updates.length,f.manifest.entries.filter(e=>e.mode==="bind-root").length);assert.ok(p.updates.every(u=>u.set.tenantSchoolId==="school-A"));for(const count of Object.values(p.counts))assert.equal(count.expectedBefore,count.expectedAfter);assert.equal(p.executable,false);});
test("rehearsal is conditional/idempotent and does not mutate inventory or originals",()=>{const f=fixture(),before=JSON.stringify(f),p=core.plan(f.snapshot,f.manifest),first=core.rehearse(f.snapshot,p),again=core.rehearse(first.after,p);assert.equal(again.plan.updates.length,0);assert.equal(JSON.stringify(f),before);assert.deepEqual(again.after,first.after);});
test("plaintext student/account/provider information never appears in artifacts",()=>{const f=fixture(),out=JSON.stringify([f.snapshot,core.summarize(f.snapshot),core.plan(f.snapshot,f.manifest)]);for(const value of ["@example.test","PrivateSynthetic","private-synthetic-password","Same School"])assert.ok(!out.includes(value));});
for(const kind of ["school-name","unique-name","spotter"] )test("name/subscription evidence is never approval: "+kind,()=>{const f=fixture();f.manifest.entries[0].evidence.kind=kind;assert.throws(()=>core.plan(f.snapshot,f.manifest));});
test("explicit existing-ID evidence cannot be invented for a name-only record",()=>{const f=fixture();f.manifest.entries.find(e=>e.recordKey===f.key("users","A-staff")).evidence.kind="verified-existing-id";assert.throws(()=>core.plan(f.snapshot,f.manifest));});
for(const invalid of ["unknown","school-a"," school-A",null])test("unknown/normalized/cleared owner rejected: "+invalid,()=>{const f=fixture();f.manifest.entries[0].tenantSchoolId=invalid;assert.throws(()=>core.plan(f.snapshot,f.manifest));});
test("forged arbitrary update properties and duplicate entries rejected",()=>{const f=fixture();f.manifest.entries[0].update={$set:{currency:999}};assert.throws(()=>core.plan(f.snapshot,f.manifest));delete f.manifest.entries[0].update;f.manifest.entries.push(f.manifest.entries[0]);assert.throws(()=>core.plan(f.snapshot,f.manifest));});
test("cross-school actor/student/roster/template/currency/grant/billing references rejected",()=>{for(const collection of core.COLLECTIONS){const f=fixture();const e=f.manifest.entries.find(e=>f.snapshot.records.find(r=>r.recordKey===e.recordKey).collection===collection);e.tenantSchoolId="school-B";f.manifest.schoolIds.push("school-B");assert.throws(()=>core.plan(f.snapshot,f.manifest),collection);}});
test("orphaned and duplicate identities are reported, never first-match resolved",()=>{for(const duplicate of [false,true]){const f=fixture();if(duplicate)f.docs.users.push({...f.docs.users[0],_id:"duplicate"});else f.docs.users=f.docs.users.filter(r=>r._id!=="A-staff");const snapshot=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic","mongodb://synthetic:27017/synthetic");const manifest={...f.manifest,inventoryDigest:snapshot.inventoryDigest,entries:f.manifest.entries.filter(e=>snapshot.records.some(r=>r.recordKey===e.recordKey))};assert.throws(()=>core.plan(snapshot,manifest));assert.ok(duplicate?snapshot.duplicateIdentityGroups.length:core.summarize(snapshot).counts.employee.orphanedReferences);}});
test("historical cases are not reassigned using a student's current school",()=>{const f=fixture();f.docs.students[0].tenantSchoolId="school-B";const snapshot=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic","mongodb://synthetic:27017/synthetic");assert.throws(()=>core.plan(snapshot,{...f.manifest,inventoryDigest:snapshot.inventoryDigest}));});
test("changed records, counts, target and review binding fail closed",()=>{const f=fixture(),p=core.plan(f.snapshot,f.manifest);const other=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic","mongodb://OTHER:27017/synthetic");assert.throws(()=>core.plan(other,f.manifest));assert.throws(()=>core.rehearse(other,p));f.docs.employee[0].rosterRevision=99;const changed=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic","mongodb://synthetic:27017/synthetic");assert.throws(()=>core.rehearse(changed,p));f.docs.students.pop();const missing=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic","mongodb://synthetic:27017/synthetic");assert.throws(()=>core.rehearse(missing,p));});
test("genuine global defaults remain global; malformed global ownership rejected",()=>{const f=fixture();f.docs.assignment_templates.push({_id:"global",createdBySystem:true,scope:"SYSTEM_DEFAULT"});let s=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic");assert.ok(s.records.find(r=>r.recordKey===f.key("assignment_templates","global")).globalEligible);f.docs.assignment_templates.at(-1).schoolId="school-B";s=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic");assert.equal(s.records.find(r=>r.recordKey===f.key("assignment_templates","global")).globalEligible,false);});
async function exporter({fail=false, privacy=true, approved=true}={}) {
 const safety=require("./inventory-safety.cjs"),inventory=require("./inventory-aggregate.cjs");
 const reads=[],logs=[];
 let report,code,connections=0;
 const fakeDb={
   auth(){return {ok:1};},
   runCommand(command){
     if(command.hello)return {setName:"repsinventory",isWritablePrimary:true,hosts:["127.0.0.1:27031"]};
     assert.equal(command.connectionStatus,1);
     assert.equal(command.showPrivileges,true);
     return {ok:1,authInfo:{
       authenticatedUsers:[{user:safety.principal,db:safety.database}],
       authenticatedUserPrivileges:inventory.collections.map(collection=>({resource:{db:safety.database,collection},actions:["find"]}))
     }};
   },
   getCollection(collection){
     return {find(filter,fields){
       assert.equal(Object.keys(filter).length,0);
       assert.ok(Object.keys(fields).length);
       assert.ok(!Object.keys(fields).some(field=>/password|reset|setupTicket|encryptionKey|answers|ciphertext/i.test(field)));
       reads.push(collection);
       if(fail)throw Error("synthetic credential must never be echoed");
       return {limit(){return this;},maxTimeMS(){return this;},toArray(){return [];}};
     }};
   }
 };
 const env={REPS_TENANT_AUDIT_APPROVED:approved?"read-only-inventory-approved":"",
   REPS_TENANT_AUDIT_TOOL_DIR:__dirname,REPS_TENANT_AUDIT_URI:safety.uri,REPS_TENANT_AUDIT_DATABASE:safety.database,
   REPS_TENANT_AUDIT_PASSWORD:"synthetic",REPS_TENANT_AUDIT_OUT:"synthetic-output"};
 const context={
   config:{get:name=>privacy?({disableLogging:true,enableTelemetry:false,disableSchemaSampling:true,historyLength:0,updateURL:"",snippetAutoload:false,browser:false})[name]:null},
   require:name=>name.endsWith("inventory-safety.cjs")?{...safety,publish:(_output,value)=>{report=value;}}:require(name),
   process:{env},Mongo:class{constructor(){connections++;}getDB(name){assert.equal(name,safety.database);return fakeDb;}},
   EJSON:{serialize:value=>value},print:text=>logs.push(text),exit:value=>{code=value;}
 };
 await vm.runInNewContext(fs.readFileSync(path.join(__dirname,"inventory.mongosh.js"),"utf8"),context);
 return {reads,logs,connections,report,code};
}
test("inventory uses bounded projected finds and publishes aggregate counts",async()=>{
 const result=await exporter();
 assert.equal(result.code,undefined);
 assert.deepEqual(result.reads,require("./inventory-aggregate.cjs").collections);
 assert.equal(result.report.schemaVersion,"reps-inventory-aggregate/v1");
});
test("missing approval or shell privacy refuses before connecting",async()=>{
 for(const options of [{approved:false},{privacy:false}]){
   const result=await exporter(options);
   assert.equal(result.connections,0);
   assert.equal(result.code,1);
 }
});
test("database errors are sanitized and leave no published artifact",async()=>{
 const result=await exporter({fail:true});
 assert.equal(result.connections,1);
 assert.equal(result.code,1);
 assert.equal(result.report,undefined);
 assert.ok(!result.logs.join("").includes("credential must never"));
});
test("offline CLI only writes non-executable plans outside repository",()=>{const f=fixture(),dir=fs.mkdtempSync(path.join(os.tmpdir(),"reps-a7-cli-"));try{fs.writeFileSync(path.join(dir,"snapshot.json"),JSON.stringify(f.snapshot));fs.writeFileSync(path.join(dir,"manifest.json"),JSON.stringify(f.manifest));const result=cp.spawnSync(process.execPath,[path.join(__dirname,"plan.cjs"),"plan",path.join(dir,"snapshot.json"),path.join(dir,"manifest.json"),path.join(dir,"plan.json")],{encoding:"utf8"});assert.equal(result.status,0,result.stderr);assert.equal(JSON.parse(fs.readFileSync(path.join(dir,"plan.json"))).executable,false);const repeat=cp.spawnSync(process.execPath,[path.join(__dirname,"plan.cjs"),"plan",path.join(dir,"snapshot.json"),path.join(dir,"manifest.json"),path.join(dir,"plan.json")],{encoding:"utf8"});assert.notEqual(repeat.status,0);}finally{removeTemp(dir);}});

test("case and whitespace variants are flagged without becoming authority",()=>{const f=fixture();f.schools[1].schoolName=" same   school ";const snapshot=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic");assert.equal(core.summarize(snapshot).duplicateSchoolNameGroups.length,1);assert.ok(snapshot.records.every(r=>!r.tenantSchoolId));});

test("conflicting snapshot/template identity is reported without changing assignment history",()=>{const f=fixture();f.docs.Punishments[0].assignmentWork={template:{id:"B-template",tenantSchoolId:"school-B"}};const snapshot=core.buildSnapshot(f.schools,f.docs,KEY,"synthetic","mongodb://synthetic:27017/synthetic");assert.equal(core.summarize(snapshot).counts.Punishments.structuralConflicts,1);const manifest={...f.manifest,inventoryDigest:snapshot.inventoryDigest,entries:f.manifest.entries.map(e=>({...e,beforeDigest:snapshot.records.find(r=>r.recordKey===e.recordKey).beforeDigest}))};assert.throws(()=>core.plan(snapshot,manifest));});

test("unsupported or colliding typed school IDs cannot be coerced into identity",()=>{const f=fixture();assert.throws(()=>core.buildSnapshot([{_id:123,schoolName:"Same"}],{},KEY,"synthetic"));assert.throws(()=>core.buildSnapshot([{_id:"abcdefabcdefabcdefabcdef",schoolName:"Same"},{_id:{$oid:"abcdefabcdefabcdefabcdef"},schoolName:"Same"}],{},KEY,"synthetic"));});
