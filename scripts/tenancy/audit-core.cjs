"use strict";
// Offline preparation only: no MongoDB driver, network, credentials, or database write capability.
const crypto = require("node:crypto");
const COLLECTIONS = ["users", "employee", "students", "Punishments", "OfficeReferrals", "Guidance", "TrackedBehaviorEvents", "assignment_templates", "assignment_template_bindings", "currency_operations", "oauth2_authorized_clients", "registration_intents"];
const ROOTS = COLLECTIONS.slice(0,9);
const VERSION = "reps-tenant-inventory/v2";
const MANIFEST = "reps-tenant-manifest/v2";
const contract = require("./ownership-contract.cjs");
function stable(value) {
  if(typeof value==="number"&&!Number.isFinite(value) || value&&typeof value==="object"&&!Array.isArray(value)&&![Object.prototype,null].includes(Object.getPrototypeOf(value)))throw new Error("Canonical EJSON evidence required");
  if (Array.isArray(value)) return "[" + value.map(stable).join(",") + "]";
  if (value && typeof value === "object") return "{" + Object.keys(value).sort().map(k => JSON.stringify(k) + ":" + stable(value[k])).join(",") + "}";
  return JSON.stringify(value === undefined ? null : value);
}
const sha = value => crypto.createHash("sha256").update(stable(value)).digest("hex");
function identifier(value) {
  if(value===null || value===undefined)return "";
  if(typeof value==="string")return value;
  if(typeof value==="object" && Object.keys(value).length===1 && typeof value.$oid==="string" && /^[0-9a-f]{24}$/.test(value.$oid))return value.$oid;
  throw new Error("Unsupported identifier representation requires review");
}
function name(value) { return String(value ?? "").trim().replace(/\s+/g, " ").toLowerCase(); }
function email(value) { return String(value ?? "").trim().toLowerCase(); }
function buildSnapshot(schools, documents, key, sourceId, target = "synthetic") {
  if (!Buffer.isBuffer(key) || key.length !== 32 || !/^[A-Za-z0-9._:/-]{1,120}$/.test(sourceId)) throw new Error("Invalid audit configuration");
  const h = (kind, value) => crypto.createHmac("sha256", key).update(kind + ":" + stable(value)).digest("hex");
  const ids = schools.map(s => identifier(s._id));
  if (ids.some(id => !id || id.trim() !== id) || new Set(ids).size !== ids.length) throw new Error("Ambiguous school identifier representation");
  const schoolIdSet=new Set(ids);
  const index = new Map(), identityKinds = new Map(), entries = [];
  function addIdentity(kind, value, recordKey) {
    if (!value) return;
    const token = h(kind, value); identityKinds.set(token,kind); const found = index.get(token) || []; found.push(recordKey); index.set(token, found);
  }
  const schoolRows = schools.map(s => ({id:identifier(s._id),nameGroup:h("school-name",name(s.schoolName))})).sort((a,b)=>a.id.localeCompare(b.id));
  const names=new Map();for(const school of schoolRows){const group=names.get(school.nameGroup)||[];group.push(school.id);names.set(school.nameGroup,group);}
  for (const collection of COLLECTIONS) for (const d of documents[collection] || []) {
    const recordKey = h("record/" + collection, d._id);
    const rawBefore = {...d}; if(ROOTS.includes(collection)) delete rawBefore.tenantSchoolId;
    const rawOwner = d.tenantSchoolId;
    const ownerValid = typeof rawOwner === "string" && schoolIdSet.has(rawOwner);
    const row = {collection,recordKey,beforeDigest:h("source/"+collection,rawBefore),tenantSchoolId:ownerValid?rawOwner:null,
      invalidOwner:rawOwner !== undefined && !ownerValid, globalEligible:collection === "assignment_templates" && d.createdBySystem === true && d.scope === "SYSTEM_DEFAULT" && !d.createdByUserId && !d.schoolId && !d.tenantSchoolId && !d.school && !d.schoolIdNumber && !d.schoolName,
      explicitSchoolIds:[], nameCandidates:[], references:[], _pending:[]};
    if(["users","employee"].includes(collection)) {
      const rawRoles=d.roles||[];
      row.membership={roles:[...new Set(rawRoles.map(r=>typeof r.role==="string"&&r.role===r.role.trim()?r.role.toUpperCase():"UNSUPPORTED"))].sort(),enabled:d.enabled!==false,archived:d.archived===true};
    }
    row.contract = ROOTS.includes(collection)?"root-metadata":"compatibility-only";
    row.protectedDigest=h("protected/"+collection,contract.protectedValue(collection,d));
    row.components=contract.components(collection,d,h,identifier);
    row.receipt=collection==="currency_operations"?contract.receipt(d,h):null;
    const legacyNames = [d.school, d.schoolName];
    for (const candidate of [d.schoolIdNumber,d.schoolId,d.assignmentWork?.template?.schoolId,d.assignmentWork?.template?.tenantSchoolId]) {
      if (!candidate) continue;
      if (schoolIdSet.has(candidate)) row.explicitSchoolIds.push(candidate); else legacyNames.push(candidate);
    }
    row.explicitSchoolIds = [...new Set(row.explicitSchoolIds)];
    row.structuralConflict=!!(d.assignmentTemplateId && d.assignmentWork?.template?.id && d.assignmentTemplateId!==d.assignmentWork.template.id) || !!(d.assignmentWork?.template?.tenantSchoolId && !schoolIdSet.has(d.assignmentWork.template.tenantSchoolId));
    row.structuralConflict ||= row.components.some(c=>c.conflict);
    const groups = legacyNames.filter(Boolean).map(v=>h("school-name",name(v)));
    row.nameCandidates = [...new Set(groups.flatMap(group=>names.get(group)||[]))]; // Discovery ONLY. Never ownership evidence.
    row.unresolvedLegacySchool = legacyNames.some(v=>v && !names.has(h("school-name",name(v))));
    addIdentity("id/"+collection,identifier(d._id),recordKey);
    if (collection === "users") {addIdentity("account",email(d.username),recordKey);addIdentity("stripe-subscription",d.stripeSubscriptionId,recordKey);}
    if (collection === "employee") {addIdentity("staff",email(d.email),recordKey);addIdentity("profile-id",identifier(d._id),recordKey);}
    if (collection === "students") {addIdentity("student",email(d.studentEmail),recordKey);addIdentity("profile-id",identifier(d._id),recordKey);}
    const ref = (relation,kind,value) => {if(value) row._pending.push({relation,token:h(kind,value)});};
    if (collection === "users") ref("profile",(d.roles||[]).some(r=>r.role === "STUDENT")?"student":"staff",email(d.username));
    if (collection === "employee") {
      ref("account","account",email(d.email));
      for(const c of d.classes||[]) for(const value of c.classRoster||[]) ref("roster","student",email(value));
    }
    if(collection === "students") {
      ref("account","account",email(d.studentEmail)); ref("assigned-guidance","staff",email(d.guidanceEmail));ref("administrator","staff",email(d.adminEmail));
      for(const value of d.spotters||[]) ref("subscription-not-authority","staff",email(value));
    }
    if(["Punishments","OfficeReferrals","Guidance","TrackedBehaviorEvents"].includes(collection)) {
      ref("student","student",email(d.studentEmail));
      for(const value of [d.teacherEmail,d.originalAuthorEmail,d.responsibleStaffEmail]) ref("staff-participant","staff",email(value));
      ref("linked-case","id/Punishments",identifier(d.linkToPunishment));
      ref("assignment-template","id/assignment_templates",identifier(d.assignmentTemplateId));
      for(const intent of d.requiredNotifications||[])ref("notification-actor","account",email(intent.actor));
    }
    if(collection === "assignment_templates") ref("creator","account",email(d.createdByUserId));
    if(collection === "assignment_template_bindings") {ref("teacher","staff",email(d.teacherEmail));ref("template","id/assignment_templates",identifier(d.assignmentTemplateId));}
    if(collection === "currency_operations") {
      ref("historical-actor-hint","account",email(d.actor));
      ref("recorded-account","id/users",identifier(d.actorAccountId));ref("recorded-profile","profile-id",identifier(d.actorProfileId));
      for(const id of d.caseIds||[]) ref("recognition","id/Punishments",identifier(id));
      for(const id of d.studentIds||[]) ref("wallet","id/students",identifier(id));
      for(const id of d.employeeIds||[]) ref("wallet","id/employee",identifier(id));
    }
    if(collection === "oauth2_authorized_clients") {ref("account","account",email(d._id));ref("owner-account-id","id/users",identifier(d.ownerAccountId));}
    if(collection === "registration_intents") {ref("account","account",email(d.email));ref("subscription-owner","stripe-subscription",d.stripeSubscriptionId);}
    for(const component of row.components)for(const pending of component.pending)row._pending.push({...pending,componentKey:component.componentKey});
    entries.push(row);
  }
  for(const row of entries) {
    for(const ref of row._pending) {
      const value={relation:ref.relation,targets:[...(index.get(ref.token)||[])].sort()};
      if(ref.componentKey)row.components.find(c=>c.componentKey===ref.componentKey).references.push(value);else row.references.push(value);
    }
    for(const component of row.components)delete component.pending;
    delete row._pending;
  }
  entries.sort((a,b)=>a.recordKey.localeCompare(b.recordKey));
  if(new Set(entries.map(r=>r.recordKey)).size !== entries.length) throw new Error("Duplicate inventory records");
  const snapshot={schemaVersion:VERSION,fingerprintContract:"canonical-ejson-hmac-sha256/v2",sourceId,targetTag:h("target",target),keyTag:h("key","binding"),schools:schoolRows,records:entries,duplicateIdentityGroups:[...index.entries()].filter(([,keys])=>keys.length>1).map(([token,keys])=>({kind:identityKinds.get(token),records:[...keys].sort()})).sort((a,b)=>a.records.join().localeCompare(b.records.join())),consistency:"non-transactional-scan"};
  snapshot.inventoryDigest=sha(snapshot); return snapshot;
}
function validateSnapshot(snapshot) {
  if(snapshot.schemaVersion!==VERSION || snapshot.fingerprintContract!=="canonical-ejson-hmac-sha256/v2" || typeof snapshot.inventoryDigest!=="string") throw new Error("Invalid inventory version");
  if(!Array.isArray(snapshot.schools)||snapshot.schools.some(s=>typeof s.id!=="string"||!s.id||s.id!==s.id.trim()||!/^[0-9a-f]{64}$/.test(s.nameGroup))||new Set(snapshot.schools.map(s=>s.id)).size!==snapshot.schools.length)throw new Error("Invalid canonical school inventory");
  const original={...snapshot};delete original.inventoryDigest;
  if(sha(original)!==snapshot.inventoryDigest) throw new Error("Inventory integrity mismatch");
  if(!Array.isArray(snapshot.records)||!Array.isArray(snapshot.schools)||snapshot.records.some(r=>!COLLECTIONS.includes(r.collection)||!r.recordKey?.match(/^[0-9a-f]{64}$/)||!r.beforeDigest?.match(/^[0-9a-f]{64}$/)||!r.protectedDigest?.match(/^[0-9a-f]{64}$/)||(r.tenantSchoolId!==null&&(!snapshot.schools.some(s=>s.id===r.tenantSchoolId)))||r.contract!==(ROOTS.includes(r.collection)?"root-metadata":"compatibility-only")||!Array.isArray(r.references)||!Array.isArray(r.components)||r.components.some(c=>!contract.KINDS.includes(c.kind)||!c.componentKey?.match(/^[0-9a-f]{64}$/)||!c.beforeDigest?.match(/^[0-9a-f]{64}$/)||!Array.isArray(c.references))))throw new Error("Unsupported inventory contract");
  if(new Set(snapshot.records.flatMap(r=>r.components.map(c=>c.componentKey))).size!==snapshot.records.reduce((n,r)=>n+r.components.length,0))throw new Error("Duplicate embedded inventory keys");
  if(new Set(snapshot.records.map(r=>r.recordKey)).size!==snapshot.records.length) throw new Error("Duplicate inventory records");
}
function summarize(snapshot) {
  validateSnapshot(snapshot);
  const groups=new Map();for(const s of snapshot.schools){const group=groups.get(s.nameGroup)||[];group.push(s.id);groups.set(s.nameGroup,group);}
  const counts={};
  for(const collection of COLLECTIONS){const rows=snapshot.records.filter(r=>r.collection===collection);counts[collection]={total:rows.length,bound:rows.filter(r=>r.tenantSchoolId).length,unmapped:rows.filter(r=>!r.tenantSchoolId&&!r.globalEligible).length,invalidOwner:rows.filter(r=>r.invalidOwner).length,structuralConflicts:rows.filter(r=>r.structuralConflict).length,orphanedSchoolReferences:rows.filter(r=>r.invalidOwner||r.unresolvedLegacySchool).length,ambiguousName:rows.filter(r=>r.nameCandidates.length>1).length,orphanedReferences:rows.filter(r=>contract.allReferences(r).some(ref=>ref.targets.length===0)).length,ambiguousReferences:rows.filter(r=>contract.allReferences(r).some(ref=>ref.targets.length>1)).length};}
  return {schoolCount:snapshot.schools.length,sourceId:snapshot.sourceId,inventoryDigest:snapshot.inventoryDigest,consistency:snapshot.consistency,duplicateSchoolNameGroups:[...groups.values()].filter(g=>g.length>1),duplicateIdentityGroups:snapshot.duplicateIdentityGroups,counts,componentCounts:Object.fromEntries(contract.KINDS.map(kind=>{const rows=snapshot.records.flatMap(r=>r.components).filter(c=>c.kind===kind);return [kind,{total:rows.length,missingOwner:rows.filter(c=>!c.tenantSchoolId&&!c.globalEligible&&!c.inheritsRootOwner).length,conflicting:rows.filter(c=>c.conflict).length,missingHistoricalVersion:rows.filter(c=>kind==="notification"&&c.credentialVersion===null).length}];})),quarantine:contract.inspect(snapshot)};
}
function exactKeys(object, allowed) {if(!object || typeof object!=="object" || Object.keys(object).some(k=>!allowed.includes(k)))throw new Error("Unsupported manifest property");}
function plan(snapshot,manifest) {
  validateSnapshot(snapshot);
  return contract.plan(snapshot,manifest,{COLLECTIONS,ROOTS,MANIFEST,sha,exactKeys});
}
// Pure in-memory rehearsal of conditional/idempotent field updates. NEVER a database executor.
function rehearse(snapshot,prepared) {
  validateSnapshot(snapshot);const checked={...prepared};delete checked.planDigest;
  if(prepared.schemaVersion!=="reps-tenant-plan/v2" || sha(checked)!==prepared.planDigest || prepared.sourceId!==snapshot.sourceId || prepared.keyTag!==snapshot.keyTag || prepared.targetTag!==snapshot.targetTag || prepared.schoolSetDigest!==sha(snapshot.schools))throw new Error("Invalid prepared plan");
  contract.validateUpdates(prepared,ROOTS,snapshot);
  for(const collection of COLLECTIONS)if(snapshot.records.filter(r=>r.collection===collection).length!==prepared.counts[collection].expectedBefore)throw new Error("Record counts changed");
  // Re-validate every conditional fingerprint and reference using the current sanitized inventory.
  const manifest={schemaVersion:MANIFEST,sourceId:snapshot.sourceId,targetTag:snapshot.targetTag,keyTag:snapshot.keyTag,inventoryDigest:snapshot.inventoryDigest,reviewedBy:"rehearsal-only",schoolIds:prepared.schoolIds,entries:prepared.entries,components:prepared.components,currencyActors:prepared.currencyActors};
  const current=plan(snapshot,manifest),after=structuredClone(snapshot);
  if(prepared.inventoryDigest===snapshot.inventoryDigest && sha(current.updates)!==sha(prepared.updates))throw new Error("Prepared updates differ from reviewed contract");
  const afterByKey=new Map(after.records.map(r=>[r.recordKey,r]));
  for(const update of current.updates)afterByKey.get(update.recordKey).tenantSchoolId=update.set.tenantSchoolId;
  delete after.inventoryDigest;after.inventoryDigest=sha(after);
  return {plan:current,after};
}
function simulate(snapshot,manifest){return rehearse(snapshot,plan(snapshot,manifest));}
module.exports={stable,sha,COLLECTIONS,ROOTS,VERSION,MANIFEST,buildSnapshot,validateSnapshot,summarize,plan,rehearse,simulate};
