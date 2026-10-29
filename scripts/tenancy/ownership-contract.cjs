"use strict";
// Offline contracts only. No driver, network, provider, decryption, ledger writer or executor.
const HEX=/^[0-9a-f]{64}$/, REF=/^[A-Za-z0-9._:/-]{1,120}$/;
const CASES=["Punishments","OfficeReferrals","Guidance","TrackedBehaviorEvents"];
const STATES=["PENDING","DISPATCHING","ACCEPTED","FAILED","RECONNECT_REQUIRED","UNCERTAIN","RESOLVED"];
const KINDS=["assignment-snapshot","submission-snapshot","notification","historical-access","gmail-grant","billing-association"];
const text=v=>typeof v==="string" && v.length>0 && v===v.trim();
function number(v){if(v && typeof v==="object" && Object.keys(v).length===1 && /^-?[0-9]+$/.test(v.$numberLong||v.$numberInt||""))v=Number(v.$numberLong||v.$numberInt);return Number.isSafeInteger(v)&&v>=0?v:null;}
function protectedValue(collection,d){const out=structuredClone(d);if(["users","employee","students",...CASES,"assignment_templates","assignment_template_bindings"].includes(collection))delete out.tenantSchoolId;return out;}
function receipt(d,h){
    const version=d.receiptVersion===undefined?1:number(d.receiptVersion);
    return {namespaceKey:text(d.actor)&&text(d.school)?h("currency-namespace",[d.actor.toLowerCase(),d.school.toLowerCase()]):null,
        receiptIdValid:version===1?HEX.test(d._id||""):version===2&&/^v2-[0-9a-f]{64}$/.test(d._id||""),
        payloadHashPresent:HEX.test(d.payloadHash||""),kindValid:["AWARD","REDEEM","RESET_STAFF_BUDGETS"].includes(d.kind),version,
        committed:version===1?d.state===undefined:version===2&&d.state==="COMMITTED"};
}
function components(collection,d,h,identifier){
    const result=[];
    function add(kind,path,value){
        const component={kind,path,componentKey:h("component/"+collection,[d._id,path]),beforeDigest:h("component-source/"+collection,[path,value]),
            tenantSchoolId:typeof value.tenantSchoolId==="string"?value.tenantSchoolId:null,conflict:value.tenantSchoolId!==undefined&&!text(value.tenantSchoolId),references:[],pending:[],globalEligible:false};
        const ref=(relation,type,value)=>{if(value)component.pending.push({relation,token:h(type,value)});};
        result.push(component);return {component,ref};
    }
    function template(kind,path,t,pinned){
        const {component:c,ref}=add(kind,path,t);
        const a=identifier(t.id),b=identifier(t._id),id=a||b;
        c.conflict ||= !!(a&&b&&a!==b) || !id || !!(pinned&&id!==identifier(pinned));
        c.globalEligible=t.createdBySystem===true && t.scope==="SYSTEM_DEFAULT" && !t.createdByUserId && !t.schoolId && !t.schoolIdNumber && !t.tenantSchoolId && !t.school && !t.schoolName;
        ref("snapshot-template","id/assignment_templates",id);
    }
    if(CASES.includes(collection)){
        const work=d.assignmentWork;
        if(work?.template)template("assignment-snapshot","assignmentWork.template",work.template,d.assignmentTemplateId);
        for(const [i,submission]of (work?.submissions||[]).entries()){
            if(submission.template)template("submission-snapshot",`assignmentWork.submissions[${i}].template`,submission.template,null);
            else {const {component}=add("submission-snapshot",`assignmentWork.submissions[${i}].template`,{});component.conflict=true;}
        }
        const notificationIds=new Set();
        for(const [i,n]of (d.requiredNotifications||[]).entries()){
            const {component:c,ref}=add("notification",`requiredNotifications[${i}]`,n);
            c.state=STATES.includes(n.state)?n.state:"UNSUPPORTED";c.credentialVersion=number(n.actorCredentialVersion);
            c.conflict ||= c.state==="UNSUPPORTED" || !text(n.notificationId) || notificationIds.has(n.notificationId);notificationIds.add(n.notificationId);
            ref("recorded-account","id/users",identifier(n.actorAccountId));ref("recorded-profile","profile-id",identifier(n.actorProfileId));
        }
        for(const [i,entry]of (d.historicalAccess||[]).entries()){
            const {component:c,ref}=add("historical-access",`historicalAccess[${i}]`,entry);
            c.conflict ||= number(entry.formatVersion)!==1 || !["AUTHOR","ASSIGNEE"].includes(entry.basis) || identifier(entry.caseId)!==identifier(d._id);
            ref("recorded-account","id/users",identifier(entry.accountId));ref("recorded-profile","profile-id",identifier(entry.profileId));
            if(!entry.accountId || !entry.profileId || !entry.tenantSchoolId)c.conflict=true; // Never manufacture historical access.
        }
    }
    if(collection==="oauth2_authorized_clients"){
        const value={...d.gmailAuthorization,tenantSchoolId:d.tenantSchoolId,ownerAccountId:d.ownerAccountId,ownerProfileId:d.ownerProfileId};
        const {component:c,ref}=add("gmail-grant","gmailAuthorization",value);
        c.pendingFlowPresent=!!value.pending;
        c.versionTag=text(value.version)?h("gmail-version",value.version):null;c.cipherPresent=text(value.cipher);
        c.state=["CONNECTED","RECONNECT_REQUIRED","DISCONNECTED","NEVER_CONNECTED","UNKNOWN"].includes(value.state)?value.state:"UNSUPPORTED";
        c.conflict ||= !d.gmailAuthorization || c.state==="UNSUPPORTED" || c.state==="CONNECTED" && (!c.versionTag || !c.cipherPresent);
        ref("recorded-account","id/users",identifier(d.ownerAccountId));ref("recorded-profile","profile-id",identifier(d.ownerProfileId));
    }
    if(collection==="registration_intents" || collection==="users" && (d.stripeCustomerId||d.stripeSubscriptionId)){
        const value={schoolIdNumber:d.schoolIdNumber,stripeCustomerId:d.stripeCustomerId,stripeSubscriptionId:d.stripeSubscriptionId,stripeCheckoutSessionId:d.stripeCheckoutSessionId,priceId:d.priceId,createdAt:d.createdAt,status:d.status};
        const {component:c}=add("billing-association","billingAssociation",value);
        c.inheritsRootOwner=true;
        c.conflict ||= !!d.schoolIdNumber && !text(d.schoolIdNumber);
    }
    return result;
}
function evidence(value,exactKeys,historical=false){
    exactKeys(value,["kind","reference","validationStatus"]);
    if(value.validationStatus!=="reviewed" || !REF.test(value.reference||"") || !["reviewed-onboarding","reviewed-historical-record","verified-existing-id"].includes(value.kind)
        || historical && value.kind!=="reviewed-historical-record")throw Error("Reviewed independent provenance required");
}
function allReferences(row){return [...row.references.filter(r=>r.relation!=="historical-actor-hint"),...row.components.flatMap(c=>c.references)];}
function inspect(snapshot,owners=new Map(),reviewed=new Set()){
    const rows=new Map(snapshot.records.map(r=>[r.recordKey,r])),reasons=new Map();
    const owner=r=>owners.get(r.recordKey)||r.tenantSchoolId;
    const add=(key,reason)=>{if(!reasons.has(key))reasons.set(key,new Set());reasons.get(key).add(reason);};
    for(const row of rows.values()){
        if(!reviewed.has(row.recordKey)&&!row.globalEligible)add(row.recordKey,"UNREVIEWED_RECORD");
        if(row.invalidOwner||row.structuralConflict||row.components.some(c=>c.conflict))add(row.recordKey,"INVALID_OR_CONFLICTING_STRUCTURE");
        for(const component of row.components)if(component.tenantSchoolId && (!snapshot.schools.some(s=>s.id===component.tenantSchoolId) || owner(row)&&component.tenantSchoolId!==owner(row)))add(row.recordKey,"EMBEDDED_OWNER_CONFLICT");
        for(const reference of allReferences(row)){
            if(reference.targets.length!==1){add(row.recordKey,reference.targets.length?"AMBIGUOUS_REFERENCE":"ORPHANED_REFERENCE");continue;}
            const target=rows.get(reference.targets[0]);
            if(!target){add(row.recordKey,"ORPHANED_REFERENCE");continue;}
            if(row.collection==="users"&&reference.relation==="profile"){
                const roles=row.membership?.roles||[],profileRoles=target.membership?.roles||[];
                if(!roles.length||roles.some(r=>!["STUDENT","ADMIN","TEACHER","GUIDANCE"].includes(r))||roles.includes("STUDENT")&&roles.length!==1||target.collection==="employee"&&JSON.stringify(roles)!==JSON.stringify(profileRoles))add(row.recordKey,"ACCOUNT_PROFILE_ROLE_CONFLICT");
            }
            if(!target.globalEligible && owner(row)&&owner(target)&&owner(row)!==owner(target))add(row.recordKey,"CROSS_TENANT_REFERENCE");
        }
    }
    let changed=true;
    while(changed){changed=false;for(const row of rows.values())if(!reasons.has(row.recordKey)&&allReferences(row).some(ref=>ref.targets.some(key=>reasons.has(key)))){add(row.recordKey,"QUARANTINED_DEPENDENCY");changed=true;}}
    return [...reasons.entries()].map(([recordKey,set])=>({recordKey,collection:rows.get(recordKey).collection,validationStatus:reviewed.has(recordKey)?"blocked":"unreviewed",reasons:[...set].sort()})).sort((a,b)=>a.recordKey.localeCompare(b.recordKey));
}
function validateUpdates(prepared,ROOTS,snapshot){
    if(prepared.executable!==false || !Array.isArray(prepared.updates))throw Error("Non-executable plan required");
    for(const update of prepared.updates){
        if(!ROOTS.includes(update.collection) || Object.keys(update).sort().join(",")!=="beforeDigest,collection,expectedOwner,protectedDigest,recordKey,set" || update.expectedOwner!=="absent"
            || !update.set || Object.keys(update.set).join(",")!=="tenantSchoolId" || !text(update.set.tenantSchoolId))throw Error("Historical/embedded changes are forbidden");
        if(snapshot){const row=snapshot.records.find(r=>r.recordKey===update.recordKey),entry=prepared.entries.find(e=>e.recordKey===update.recordKey);if(!row||row.collection!==update.collection||row.beforeDigest!==update.beforeDigest||row.protectedDigest!==update.protectedDigest||!entry||entry.mode!=="bind-root"||entry.tenantSchoolId!==update.set.tenantSchoolId)throw Error("Update does not match reviewed root contract");}
    }
}
function plan(snapshot,manifest,{COLLECTIONS,ROOTS,MANIFEST,sha,exactKeys}){
    exactKeys(manifest,["schemaVersion","sourceId","targetTag","keyTag","inventoryDigest","reviewedBy","schoolIds","entries","components","currencyActors"]);
    if(manifest.schemaVersion!==MANIFEST || manifest.sourceId!==snapshot.sourceId || manifest.keyTag!==snapshot.keyTag || manifest.targetTag!==snapshot.targetTag || manifest.inventoryDigest!==snapshot.inventoryDigest || !/^[A-Za-z0-9._-]{1,80}$/.test(manifest.reviewedBy||""))throw Error("Manifest binding or reviewer missing");
    if(!Array.isArray(manifest.entries)||!Array.isArray(manifest.components)||!Array.isArray(manifest.currencyActors)||!Array.isArray(manifest.schoolIds)||!manifest.schoolIds.length||new Set(manifest.schoolIds).size!==manifest.schoolIds.length||manifest.schoolIds.some(id=>!snapshot.schools.some(s=>s.id===id)))throw Error("Explicit reviewed pilot scope required");
    const rows=new Map(snapshot.records.map(r=>[r.recordKey,r])),owners=new Map(),reviews=new Map(),embedded=new Map();
    for(const entry of manifest.entries){
        exactKeys(entry,["recordKey","beforeDigest","protectedDigest","tenantSchoolId","mode","evidence","accountKey","profileKey"]);
        const row=rows.get(entry.recordKey);evidence(entry.evidence,exactKeys,!!row && (CASES.includes(row.collection)||row.contract==="compatibility-only"));
        if(!row || owners.has(entry.recordKey)||entry.beforeDigest!==row.beforeDigest||entry.protectedDigest!==row.protectedDigest||!manifest.schoolIds.includes(entry.tenantSchoolId))throw Error("Missing duplicate or stale mapping");
        if(row.collection==="registration_intents"&&!row.explicitSchoolIds.length)throw Error("Billing association requires an existing exact School ID");
        if(entry.mode!==(ROOTS.includes(row.collection)?"bind-root":"compatibility-only"))throw Error("Historical receipt/grant/billing writes prohibited");
        if(row.invalidOwner||row.structuralConflict||row.globalEligible||row.tenantSchoolId&&row.tenantSchoolId!==entry.tenantSchoolId||row.explicitSchoolIds.some(id=>id!==entry.tenantSchoolId))throw Error("Conflicting or global ownership");
        if(entry.evidence.kind==="verified-existing-id"&&row.tenantSchoolId!==entry.tenantSchoolId&&!row.explicitSchoolIds.includes(entry.tenantSchoolId))throw Error("Existing ID evidence does not bind record");
        if(row.collection!=="currency_operations"&&entryIdentity(entry))throw Error("Unsupported root identity fields");
        owners.set(entry.recordKey,entry.tenantSchoolId);reviews.set(entry.recordKey,entry);
    }
    const componentRows=new Map(snapshot.records.flatMap(row=>row.components.map(c=>[c.componentKey,{row,c}])));
    for(const entry of manifest.components){
        exactKeys(entry,["componentKey","beforeDigest","tenantSchoolId","evidence","accountKey","profileKey","credentialVersion"]);evidence(entry.evidence,exactKeys,true);
        const found=componentRows.get(entry.componentKey);
        if(!found||embedded.has(entry.componentKey)||entry.beforeDigest!==found.c.beforeDigest||!owners.has(found.row.recordKey))throw Error("Missing duplicate stale or detached component review");
        const {row,c}=found;
        if(c.conflict || (c.globalEligible?entry.tenantSchoolId!==null:entry.tenantSchoolId!==owners.get(row.recordKey)) || c.tenantSchoolId&&c.tenantSchoolId!==entry.tenantSchoolId)throw Error("Embedded ownership conflict");
        embedded.set(entry.componentKey,entry);
    }
    function identity(entry,owner){
        const account=rows.get(entry.accountKey),profile=rows.get(entry.profileKey);
        if(!account||account.collection!=="users"||!profile||!["employee","students"].includes(profile.collection)||owners.get(entry.accountKey)!==owner||owners.get(entry.profileKey)!==owner)throw Error("Reviewed account/profile agreement required");
        const ref=account.references.find(r=>r.relation==="profile");
        if(!ref||ref.targets.length!==1||ref.targets[0]!==profile.recordKey)throw Error("Account/profile relationship mismatch");
    }
    for(const root of manifest.entries){
        const row=rows.get(root.recordKey);
        for(const c of row.components){
            const review=embedded.get(c.componentKey);if(!review)throw Error("Complete embedded review required");
            if(["notification","gmail-grant","historical-access"].includes(c.kind)){
                if(c.kind!=="notification"&&review.credentialVersion!==undefined)throw Error("Unsupported credential version");
                identity(review,root.tenantSchoolId);
                if(c.kind==="gmail-grant"&&rows.get(review.profileKey).collection!=="employee")throw Error("Gmail requires a staff profile");
                for(const field of ["account","profile"]){const ref=c.references.find(r=>r.relation==="recorded-"+field);if(ref&&(ref.targets.length!==1||ref.targets[0]!==review[field+"Key"]))throw Error("Recorded immutable identity conflicts");}
                if(c.kind==="notification" && (!Number.isSafeInteger(review.credentialVersion)||review.credentialVersion<0 || c.credentialVersion!==null&&c.credentialVersion!==review.credentialVersion))throw Error("Historical credential version evidence required");
            }else if(entryIdentity(review))throw Error("Unsupported component identity fields");
        }
    }
    const namespaces=new Set(),actors=new Map();
    for(const actor of manifest.currencyActors){
        exactKeys(actor,["accountKey","profileKey","tenantSchoolId","namespaceKeys","historyComplete","evidence"]);evidence(actor.evidence,exactKeys,true);identity(actor,actor.tenantSchoolId);
        if(rows.get(actor.profileKey).collection!=="employee"||actors.has(actor.accountKey)||actor.historyComplete!==true||!Array.isArray(actor.namespaceKeys)||actor.namespaceKeys.some(k=>!HEX.test(k)||namespaces.has(k))||new Set(actor.namespaceKeys).size!==actor.namespaceKeys.length)throw Error("Ambiguous/incomplete historical currency coverage");
        for(const key of actor.namespaceKeys)namespaces.add(key);actors.set(actor.accountKey,actor);
    }
    for(const row of snapshot.records.filter(r=>r.collection==="currency_operations")){
        const review=reviews.get(row.recordKey);
        for(const actor of manifest.currencyActors)if(row.references.some(ref=>["historical-actor-hint","recorded-account"].includes(ref.relation)&&ref.targets.includes(actor.accountKey))&&(!review||review.accountKey!==actor.accountKey))throw Error("Observed historical receipt lacks reviewed actor mapping");
        if(review){
            identity(review,review.tenantSchoolId);const actor=actors.get(review.accountKey);
            for(const field of ["account","profile"]){const ref=row.references.find(r=>r.relation==="recorded-"+field);if(ref&&(ref.targets.length!==1||ref.targets[0]!==review[field+"Key"]))throw Error("Canonical receipt identity conflicts");}
            if(!row.receipt.receiptIdValid||!row.receipt.kindValid||!row.receipt.payloadHashPresent||!row.receipt.committed||!actor||actor.profileKey!==review.profileKey||actor.tenantSchoolId!==review.tenantSchoolId||row.receipt.version===1&&(!row.receipt.namespaceKey||!actor.namespaceKeys.includes(row.receipt.namespaceKey)))throw Error("Historical receipt requires exact complete reviewed replay evidence");
        }else if(namespaces.has(row.receipt.namespaceKey))throw Error("Historical namespace has an unreviewed receipt");
    }
    for(const actor of manifest.currencyActors)for(const row of snapshot.records.filter(r=>r.collection==="currency_operations"&&actor.namespaceKeys.includes(r.receipt.namespaceKey))){if(reviews.get(row.recordKey)?.accountKey!==actor.accountKey)throw Error("Cross-school receipt borrowing prohibited");}
    const quarantine=inspect(snapshot,owners,new Set(owners.keys()));
    if(quarantine.some(q=>owners.has(q.recordKey)))throw Error("Reviewed mappings have quarantined dependencies or conflicting existing ownership");
    const updates=manifest.entries.filter(e=>ROOTS.includes(rows.get(e.recordKey).collection)&&!rows.get(e.recordKey).tenantSchoolId).map(e=>({collection:rows.get(e.recordKey).collection,recordKey:e.recordKey,beforeDigest:e.beforeDigest,protectedDigest:e.protectedDigest,expectedOwner:"absent",set:{tenantSchoolId:e.tenantSchoolId}}));
    const counts={schools:{expectedBefore:snapshot.schools.length,expectedAfter:snapshot.schools.length,fieldUpdates:0}};
    for(const collection of COLLECTIONS){const total=snapshot.records.filter(r=>r.collection===collection).length;counts[collection]={expectedBefore:total,expectedAfter:total,fieldUpdates:updates.filter(u=>u.collection===collection).length};}
    const result={schemaVersion:"reps-tenant-plan/v2",sourceId:snapshot.sourceId,targetTag:snapshot.targetTag,keyTag:snapshot.keyTag,schoolIds:manifest.schoolIds,schoolSetDigest:sha(snapshot.schools),inventoryDigest:snapshot.inventoryDigest,
        entries:manifest.entries,components:manifest.components,currencyActors:manifest.currencyActors,updates,alreadyBound:manifest.entries.filter(e=>ROOTS.includes(rows.get(e.recordKey).collection)&&rows.get(e.recordKey).tenantSchoolId).length,
        cutoverHolds:manifest.components.flatMap(e=>{const {row,c}=componentRows.get(e.componentKey);const reasons=[];if(c.kind==="gmail-grant"&&c.pendingFlowPresent)reasons.push("PENDING_OAUTH_FLOW_DRAIN_REQUIRED");if(c.kind==="notification"&&c.state==="DISPATCHING")reasons.push("IN_FLIGHT_NOTIFICATION_DRAIN_REQUIRED");if(!c.globalEligible&&!c.inheritsRootOwner&&!c.tenantSchoolId)reasons.push("EMBEDDED_OWNER_ADOPTION_REQUIRED");if(["notification","gmail-grant"].includes(c.kind)&&(!c.references.some(r=>r.relation==="recorded-account")||!c.references.some(r=>r.relation==="recorded-profile")))reasons.push("RECORDED_IDENTITY_ADOPTION_REQUIRED");if(c.kind==="notification"&&c.credentialVersion===null)reasons.push("HISTORICAL_VERSION_ADOPTION_REQUIRED");return reasons.length?[{recordKey:row.recordKey,componentKey:e.componentKey,kind:c.kind,reasons}]:[];}),
        compatibilityOnly:manifest.entries.filter(e=>e.mode==="compatibility-only").map(e=>e.recordKey),quarantine,counts,executable:false,
        activationReady:false,blockers:["NO_APPROVED_DATABASE_EXECUTOR","NO_DEPLOYABLE_CANONICAL_ACTIVATION","PRODUCTION_ROUTE_ACCEPTANCE_INCOMPLETE","PRIVATE_BSON_REPLAY_ARTIFACT_VALIDATION_REQUIRED","EMBEDDED_AND_GRANT_ADOPTION_EXECUTOR_NOT_APPROVED"]};
    validateUpdates(result,ROOTS,snapshot);result.planDigest=sha(result);return result;
}
function entryIdentity(e){return e.accountKey!==undefined||e.profileKey!==undefined||e.credentialVersion!==undefined;}
module.exports={KINDS,CASES,allReferences,number,protectedValue,receipt,components,inspect,validateUpdates,plan};
