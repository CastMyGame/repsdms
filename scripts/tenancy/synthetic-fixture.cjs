"use strict";
const crypto=require("node:crypto"),core=require("./audit-core.cjs");
const KEY=Buffer.alloc(32,7); // Synthetic, public test-only audit key. Never use for a real inventory.
function fixture(){
  const schools=[{_id:"school-A",schoolName:"Same School"},{_id:"school-B",schoolName:"Same School"}];
  const docs=Object.fromEntries(core.COLLECTIONS.map(c=>[c,[]]));
  for(const t of ["A","B"]){
    const staff=t.toLowerCase()+"-staff@example.test",child=t.toLowerCase()+"-student@example.test",school="Same School";
    docs.users.push({_id:t+"-staff",username:staff,school,roles:[{role:"ADMIN"}],password:"private-synthetic-password"},{_id:t+"-child",username:child,school,roles:[{role:"STUDENT"}]});
    docs.employee.push({_id:t+"-staff",email:staff,school,classes:[{classRoster:[child]}],currency:19});
    docs.students.push({_id:t+"-child",studentEmail:child,school,adminEmail:staff,firstName:"PrivateSyntheticName",currency:3});
    docs.Punishments.push({_id:t+"-case",studentEmail:child,teacherEmail:staff,school,revision:2,assignmentTemplateId:t+"-template",requiredNotifications:[{notificationId:t+"-notification",actor:staff,state:"UNCERTAIN",body:"PrivateSyntheticBody"}]});
    docs.OfficeReferrals.push({_id:t+"-office",studentEmail:child,teacherEmail:staff,school});
    docs.Guidance.push({_id:t+"-guidance",studentEmail:child,teacherEmail:staff,school,linkToPunishment:t+"-case"});
    docs.TrackedBehaviorEvents.push({_id:t+"-behavior",studentEmail:child,teacherEmail:staff,school});
    docs.assignment_templates.push({_id:t+"-template",createdByUserId:staff,schoolId:"school-"+t,scope:"TEACHER_DEFAULT"});
    docs.assignment_template_bindings.push({_id:t+"-binding",teacherEmail:staff,schoolId:"school-"+t,assignmentTemplateId:t+"-template"});
    docs.currency_operations.push({_id:t+"-receipt",actor:staff,school,caseIds:[t+"-case"],studentIds:[t+"-child"],employeeIds:[t+"-staff"],amounts:{private:3}});
    docs.oauth2_authorized_clients.push({_id:staff,gmailAuthorization:{state:"CONNECTED",cipher:"PrivateSyntheticCipher"}});
    docs.registration_intents.push({_id:t+"-intent",email:staff,schoolIdNumber:"school-"+t,schoolName:school,stripeSubscriptionId:t+"-subscription"});
  }
  const snapshot=core.buildSnapshot(schools,docs,KEY,"synthetic", "mongodb://synthetic:27017/synthetic");
  const key=(collection,id)=>crypto.createHmac("sha256",KEY).update("record/"+collection+":"+JSON.stringify(id)).digest("hex");
  const entries=[];
  for(const [collection,rows] of Object.entries(docs))for(const row of rows){
    const t=String(row._id).startsWith("A-")||String(row._id).startsWith("a-")?"A":"B";
    if(t!=="A")continue;
    const found=snapshot.records.find(r=>r.recordKey===key(collection,row._id));
    entries.push({recordKey:found.recordKey,beforeDigest:found.beforeDigest,tenantSchoolId:"school-A",evidence:{kind:"reviewed-historical-record",reference:"synthetic-approved-A"}});
  }
  const manifest={schemaVersion:core.MANIFEST,sourceId:snapshot.sourceId,targetTag:snapshot.targetTag,keyTag:snapshot.keyTag,inventoryDigest:snapshot.inventoryDigest,schoolIds:["school-A"],reviewedBy:"synthetic-reviewer",entries};
  return {schools,docs,snapshot,manifest,key};
}
module.exports={fixture,KEY};
