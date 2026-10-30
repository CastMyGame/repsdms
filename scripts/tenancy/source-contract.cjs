"use strict";
// Shared v2 projection: executor full-source CAS is additional local approval evidence, not a v2 format change.
const projection={_id:1,tenantSchoolId:1,school:1,schoolName:1,schoolId:1,schoolIdNumber:1,username:1,email:1,studentEmail:1,
      teacherEmail:1,originalAuthorEmail:1,responsibleStaffEmail:1,guidanceEmail:1,adminEmail:1,spotters:1,"roles.role":1,"classes.classRoster":1,
      createdByUserId:1,createdBySystem:1,scope:1,assignmentTemplateId:1,linkToPunishment:1,
      revision:1,persistenceRevision:1,profileRevision:1,rosterRevision:1,credentialVersion:1,ownerAccountId:1,ownerProfileId:1,
      stripeCustomerId:1,stripeSubscriptionId:1,stripeCheckoutSessionId:1,priceId:1,createdAt:1,currency:1,status:1,enabled:1,archived:1,assignmentWork:1,requiredNotifications:1,historicalAccess:1,gmailAuthorization:1};
function project(value,fields=projection){
    const tree={};for(const field of Object.keys(fields)){let node=tree;const parts=field.split(".");parts.forEach((part,i)=>{if(i===parts.length-1)node[part]=true;else node=node[part]??={};});}
    function select(value,node){if(node===true)return structuredClone(value);if(Array.isArray(value))return value.map(v=>select(v,node));const out={};for(const [key,child]of Object.entries(node))if(value&&Object.hasOwn(value,key))out[key]=select(value[key],child);return out;}
    return select(value,tree);
}
module.exports={projection,project};
