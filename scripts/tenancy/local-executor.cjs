"use strict";
const crypto=require("node:crypto"),core=require("./audit-core.cjs"),{project}=require("./source-contract.cjs"),{validateSchema}=require("./schema-check.cjs");
const URI="mongodb://127.0.0.1:27029/?replicaSet=repsa7exec&directConnection=true&serverSelectionTimeoutMS=5000";
const ROOTS=Object.freeze(["users","employee","students","Punishments","OfficeReferrals","Guidance","TrackedBehaviorEvents","assignment_templates","assignment_template_bindings"]);
const CONTROL="a7_executor_control", HEX=/^[0-9a-f]{64}$/;
function fail(code){const error=new Error(code);error.safeCode=code;throw error;}
function exact(o,keys){if(!o||typeof o!=="object"||Array.isArray(o)||Object.keys(o).some(k=>!keys.includes(k))||keys.some(k=>!Object.hasOwn(o,k)))fail("INVALID_APPROVAL");}
function mac(value,key,domain){return crypto.createHmac("sha256",key).update(domain+":"+core.stable(value)).digest("hex");}
function sign(payload,key){payload=structuredClone(payload);return {payload,signature:mac(payload,key,"reps-local-approval/v1")};}
function key(value){const b=Buffer.from(value||"","base64");if(b.length!==32||b.toString("base64")!==value)fail("INVALID_KEY");return b;}
function preflight(r,now=Date.now()){
    for(const field of ["schema","snapshot","manifest","plan","approval"])r[field]=structuredClone(r[field]); // Normalize the shell VM boundary without weakening canonical-EJSON validation.
    if(core.ROOTS.join()!==ROOTS.join())fail("UNSUPPORTED_WRITE_CONTRACT");
    if(!["dry-run","execute"].includes(r.mode)||!["transaction","per-record"].includes(r.atomicity))fail("INVALID_MODE");
    if(r.uri!==URI||!/^reps_a7_executor_[0-9a-f]{32}$/.test(r.database))fail("LOCAL_TARGET_REQUIRED");
    if(!Buffer.isBuffer(r.auditKey)||r.auditKey.length!==32||!Buffer.isBuffer(r.approvalKey)||r.approvalKey.length!==32||r.auditKey.equals(r.approvalKey))fail("INDEPENDENT_APPROVAL_KEY_REQUIRED");
    validateSchema(r.manifest,r.schema);core.validateSnapshot(r.snapshot);
    const prepared=core.plan(r.snapshot,r.manifest);if(core.stable(prepared)!==core.stable(r.plan)||r.plan.updates.length>100)fail("UNREVIEWED_PLAN");
    exact(r.approval,["payload","signature"]);const p=r.approval.payload;
    exact(p,["schema","scope","reference","runId","issuedAt","expiresAt","uri","database","markerDigest","manifestDigest","planDigest","schemaDigest","sourceDigest","atomicity","writersStopped"]);
    if(p.schema!=="reps-local-migration-approval/v1"||!["validate-only","execute-root-ownership"].includes(p.scope)||r.mode==="execute"&&p.scope!=="execute-root-ownership"
        ||!/^[-A-Za-z0-9._:/]{1,120}$/.test(p.reference)||!/^[-0-9a-f]{36}$/.test(p.runId)||!Number.isSafeInteger(p.issuedAt)||!Number.isSafeInteger(p.expiresAt)||p.issuedAt>now+60000||p.expiresAt<=now||p.expiresAt-p.issuedAt>3600000
        ||p.uri!==r.uri||p.database!==r.database||p.planDigest!==r.plan.planDigest||p.manifestDigest!==core.sha(r.manifest)||p.schemaDigest!==core.sha(r.schema)||p.atomicity!==r.atomicity||p.writersStopped!==true||![p.markerDigest,p.sourceDigest].every(x=>HEX.test(x||"")))fail("APPROVAL_PRECONDITION");
    const signature=mac(p,r.approvalKey,"reps-local-approval/v1");if(!HEX.test(r.approval.signature||"")||!crypto.timingSafeEqual(Buffer.from(signature,"hex"),Buffer.from(r.approval.signature,"hex")))fail("APPROVAL_SIGNATURE");
    return p;
}
function recordKey(collection,id,auditKey){return mac(id,auditKey,"record/"+collection);}
function sourceState(raw,r){
    const collections=["schools",...core.COLLECTIONS];
    const normalized={};const permitted=new Map(r.plan.updates.map(u=>[u.recordKey,u]));
    for(const c of collections){if(!Array.isArray(raw[c]))fail("INCOMPLETE_SOURCE");normalized[c]=raw[c].map(doc=>{const d=structuredClone(doc),u=permitted.get(recordKey(c,d._id,r.auditKey));if(u){if(Object.hasOwn(d,"tenantSchoolId")&&d.tenantSchoolId!==u.set.tenantSchoolId)fail("OWNERSHIP_CONFLICT");delete d.tenantSchoolId;}return d;}).sort((a,b)=>core.stable(a._id).localeCompare(core.stable(b._id)));}
    if(!raw.immutableHashes||Object.keys(raw.immutableHashes).sort().join()!=="currency_operations,oauth2_authorized_clients,registration_intents")fail("IMMUTABLE_HASHES_REQUIRED");
    normalized.immutableHashes=raw.immutableHashes;
    return crypto.createHmac("sha256",r.auditKey).update("reps-executor-full-source/v1:"+JSON.stringify(normalized)).digest("hex");
}
function inventory(raw,r){return core.buildSnapshot(raw.schools.map(d=>project(d,{_id:1,schoolName:1})),Object.fromEntries(core.COLLECTIONS.map(c=>[c,raw[c].map(d=>c==="currency_operations"?structuredClone(d):project(d))])),r.auditKey,r.snapshot.sourceId,"mongodb://127.0.0.1:27029/"+r.database);}
function validateSource(raw,r){
    if(sourceState(raw,r)!==r.approval.payload.sourceDigest)fail("SOURCE_DRIFT");
    const snapshot=inventory(raw,r);core.rehearse(snapshot,r.plan);
    const pending=[];let already=0;
    for(const u of r.plan.updates){const matches=raw[u.collection].filter(d=>recordKey(u.collection,d._id,r.auditKey)===u.recordKey);if(matches.length!==1)fail("IDENTIFIER_CONFLICT");const doc=matches[0];if(Object.hasOwn(doc,"tenantSchoolId")){if(doc.tenantSchoolId!==u.set.tenantSchoolId)fail("OWNERSHIP_CONFLICT");already++;}else pending.push({...u,document:doc});}
    return {pending,already,counts:Object.fromEntries(["schools",...core.COLLECTIONS].map(c=>[c,raw[c].length]))};
}
async function run(r,adapter,sink){
    const report={attemptId:crypto.randomUUID(),runId:null,baselineAlreadyBound:r.plan?.alreadyBound||0,plannedUpdates:r.plan?.updates?.length||0,quarantined:r.plan?.quarantine?.length||0,cutoverHolds:r.plan?.cutoverHolds?.length||0,schema:"reps-local-migration-report/v1",mode:r.mode,atomicity:r.atomicity,status:"FAILED_PRECONDITION",reason:null,planDigest:r.plan?.planDigest||null,attempted:0,committed:0,confirmedBeforeUncertainty:0,alreadyBound:0,countsBefore:null,countsAfter:null,activationReady:false};
    let transaction=false,committed=false,commitInFlight=false,writeInFlight=false,targetVerified=false;
    const emit=(event,fields={})=>sink.write({event,...fields});
    try{
        const approval=preflight(r);report.runId=approval.runId;emit("START",{attemptId:report.attemptId,runId:approval.runId,mode:r.mode,atomicity:r.atomicity,planDigest:report.planDigest});
        await adapter.verifyTarget(approval.markerDigest);targetVerified=true;
        let state=validateSource(await adapter.read(),r);report.alreadyBound=state.already;report.countsBefore=state.counts;
        if(r.mode==="dry-run"){report.status="DRY_RUN_VALIDATED";report.countsAfter=state.counts;emit("FINAL",report);return report;}
        if(r.atomicity==="transaction"){await adapter.begin();transaction=true;state=validateSource(await adapter.read(),r);}
        for(const u of state.pending){
            await adapter.verifyTarget(approval.markerDigest);
            preflight(r); // Expired approval stops further writes; partial progress is retained accurately.
            if(!transaction)validateSource(await adapter.read(),r);
            emit("BEFORE_UPDATE",{recordKey:u.recordKey,collection:u.collection});report.attempted++;writeInFlight=true;
            const result=await adapter.update(u);writeInFlight=false;
            if(result.matchedCount!==1||result.modifiedCount!==1)fail("CONDITIONAL_CONFLICT");
            if(!transaction)report.committed++;
            emit(transaction?"STAGED":"APPLIED",{recordKey:u.recordKey,collection:u.collection});
        }
        validateSource(await adapter.read(),r);
        if(transaction){commitInFlight=true;await adapter.commit();commitInFlight=false;committed=true;transaction=false;report.committed=state.pending.length;emit("COMMITTED",{committed:report.committed});}
        const after=validateSource(await adapter.read(),r);report.countsAfter=after.counts;report.status="COMPLETED";
    }catch(error){
        const uncertain=commitInFlight||error?.errorLabels?.includes("UnknownTransactionCommitResult")||writeInFlight&&(/Network|Timeout|WriteConcern/.test(error?.name||"")||error?.writeConcernError||[6,7,64,89,91,189,262,9001].includes(error?.code)||error?.errorLabels?.includes("RetryableWriteError")||!error?.safeCode&&!error?.code);
        if(committed){report.status="COMMITTED_VALIDATION_OR_REPORT_FAILURE";}
        else if(transaction&&!uncertain){try{await adapter.abort();report.status="ROLLED_BACK";report.committed=0;}catch(_){report.status="UNCERTAIN";report.committed=null;}}
        else if(uncertain){report.status="UNCERTAIN";report.confirmedBeforeUncertainty=report.committed;report.committed=null;}
        else report.status=report.committed?"FAILED_PARTIAL":"FAILED_PRECONDITION";
        report.reason=error.safeCode||"DATABASE_OR_REPORT_FAILURE";
        try{report.countsAfter=targetVerified?(await adapter.counts()):null;}catch(_){report.countsAfter=null;}
    }finally{try{await adapter.close();}catch(_){}}
    emit("FINAL",report);return report;
}
module.exports={URI,CONTROL,ROOTS,key,sign,mac,preflight,recordKey,sourceState,inventory,validateSource,run};
