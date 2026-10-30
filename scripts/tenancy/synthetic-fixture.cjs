"use strict";
const crypto=require("node:crypto"),core=require("./audit-core.cjs");
const KEY=Buffer.alloc(32,7); // Synthetic, public test-only audit key. Never use for a real inventory.
function fixture(){
  const schools=[{_id:"school-A",schoolName:"Same School"},{_id:"school-B",schoolName:"Same School"}];
  const docs=Object.fromEntries(core.COLLECTIONS.map(c=>[c,[]]));
  for(const t of ["A","B"]){
    const staff=t.toLowerCase()+"-staff@example.test",child=t.toLowerCase()+"-student@example.test",school="Same School";
    docs.users.push({_id:t+"-staff",username:staff,school,stripeSubscriptionId:t+"-subscription",roles:[{role:"ADMIN"}],password:"private-synthetic-password"},{_id:t+"-child",username:child,school,roles:[{role:"STUDENT"}]});
    docs.employee.push({_id:t+"-staff",email:staff,school,roles:[{role:"ADMIN"}],classes:[{classRoster:[child]}],currency:19});
    docs.students.push({_id:t+"-child",studentEmail:child,school,adminEmail:staff,firstName:"PrivateSyntheticName",currency:3});
    docs.Punishments.push({_id:t+"-case",studentEmail:child,teacherEmail:staff,school,revision:2,assignmentTemplateId:t+"-template",assignmentWork:{template:{_id:t+"-template",questions:[{id:"q1",prompt:"PrivateSyntheticQuestion"}]},answers:{q1:{text:"PrivateSyntheticAnswer"}},submissions:[{id:"submission",answers:{q1:{text:"PrivateSyntheticPriorAnswer"}},template:{id:t+"-template"},outcome:"REJECTED"}]},requiredNotifications:[{notificationId:t+"-notification",actor:staff,actorAccountId:t+"-staff",actorProfileId:t+"-staff",actorCredentialVersion:0,state:"UNCERTAIN",body:"PrivateSyntheticBody",providerMessageId:"PrivateSyntheticProviderId"}]});
    docs.OfficeReferrals.push({_id:t+"-office",studentEmail:child,teacherEmail:staff,school});
    docs.Guidance.push({_id:t+"-guidance",studentEmail:child,teacherEmail:staff,school,linkToPunishment:t+"-case"});
    docs.TrackedBehaviorEvents.push({_id:t+"-behavior",studentEmail:child,teacherEmail:staff,school});
    docs.assignment_templates.push({_id:t+"-template",createdByUserId:staff,schoolId:"school-"+t,scope:"TEACHER_DEFAULT"});
    docs.assignment_template_bindings.push({_id:t+"-binding",teacherEmail:staff,schoolId:"school-"+t,assignmentTemplateId:t+"-template"});
    docs.currency_operations.push({_id:crypto.createHash("sha256").update(t+"-receipt").digest("hex"),kind:"AWARD",payloadHash:crypto.createHash("sha256").update(t+"-payload").digest("hex"),actor:staff,school,caseIds:[t+"-case"],studentIds:[t+"-child"],employeeIds:[t+"-staff"],amounts:{private:3}});
    docs.oauth2_authorized_clients.push({_id:staff,ownerAccountId:t+"-staff",ownerProfileId:t+"-staff",gmailAuthorization:{state:"CONNECTED",version:"PrivateSyntheticVersion",subject:"PrivateSyntheticSubject",cipher:"PrivateSyntheticCipher"}});
    docs.registration_intents.push({_id:t+"-intent",email:staff,schoolIdNumber:"school-"+t,schoolName:school,stripeSubscriptionId:t+"-subscription"});
  }
  const snapshot=core.buildSnapshot(schools,docs,KEY,"synthetic", "mongodb://synthetic:27017/synthetic");
  const key=(collection,id)=>crypto.createHmac("sha256",KEY).update("record/"+collection+":"+JSON.stringify(id)).digest("hex");
  const entries=[];
  for(const [collection,rows] of Object.entries(docs))for(const row of rows){
    const t=String(row._id).startsWith("A-")||String(row._id).startsWith("a-")||row.actor?.startsWith("a-")?"A":"B";
    if(t!=="A")continue;
    const found=snapshot.records.find(r=>r.recordKey===key(collection,row._id));
    entries.push({recordKey:found.recordKey,beforeDigest:found.beforeDigest,tenantSchoolId:"school-A",protectedDigest:found.protectedDigest,mode:found.contract==="root-metadata"?"bind-root":"compatibility-only",evidence:{kind:"reviewed-historical-record",reference:"synthetic-approved-A",validationStatus:"reviewed"},...(collection==="currency_operations"?{accountKey:key("users","A-staff"),profileKey:key("employee","A-staff")}: {})});
  }
  const components=[];
  for(const entry of entries){const row=snapshot.records.find(r=>r.recordKey===entry.recordKey);for(const c of row.components)components.push({componentKey:c.componentKey,beforeDigest:c.beforeDigest,tenantSchoolId:c.globalEligible?null:entry.tenantSchoolId,evidence:{kind:"reviewed-historical-record",reference:"synthetic-component-review",validationStatus:"reviewed"},...(["notification","gmail-grant","historical-access"].includes(c.kind)?{accountKey:key("users","A-staff"),profileKey:key("employee","A-staff")}:{}),...(c.kind==="notification"?{credentialVersion:0}:{})});}
  const currencyActors=[{accountKey:key("users","A-staff"),profileKey:key("employee","A-staff"),tenantSchoolId:"school-A",namespaceKeys:[snapshot.records.find(r=>r.collection==="currency_operations"&&entries.some(e=>e.recordKey===r.recordKey)).receipt.namespaceKey],historyComplete:true,evidence:{kind:"reviewed-historical-record",reference:"synthetic-complete-ledger-review",validationStatus:"reviewed"}}];
  const manifest={components,currencyActors,schemaVersion:core.MANIFEST,sourceId:snapshot.sourceId,targetTag:snapshot.targetTag,keyTag:snapshot.keyTag,inventoryDigest:snapshot.inventoryDigest,schoolIds:["school-A"],reviewedBy:"synthetic-reviewer",entries};
  return {schools,docs,snapshot,manifest,key};
}
module.exports={fixture,KEY};
