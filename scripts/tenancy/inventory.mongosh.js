// READ ONLY. Execute only after separate authorization with a read-only MongoDB principal.
// No application startup, cloud imports, token decryption, Mongo write methods or backfill capability.
(async function () {
  try {
    const fs = require("node:fs"), path = require("node:path");
    if(process.env.REPS_TENANT_AUDIT_APPROVED !== "read-only-inventory-approved") throw new Error();
    // Require operator-preconfigured shell privacy; this read-only tool never changes shell settings.
    for(const [name,value] of [["disableLogging",true],["enableTelemetry",false],["disableSchemaSampling",true],["historyLength",0],["updateURL",""],["snippetAutoload",false],["browser",false]])
      if(await config.get(name)!==value)throw new Error();
    if(Object.keys(process.env).some(key=>key.startsWith("MONGODB_LOG_") && process.env[key]))throw new Error();
    const directory=process.env.REPS_TENANT_AUDIT_TOOL_DIR;
    const core=require(path.join(directory,"audit-core.cjs"));
    const uri=process.env.REPS_TENANT_AUDIT_URI, databaseName=process.env.REPS_TENANT_AUDIT_DATABASE;
    const parsed=new (require("node:url").URL)(uri);
    if(!["mongodb:","mongodb+srv:"].includes(parsed.protocol) || parsed.host!==process.env.REPS_TENANT_AUDIT_EXPECTED_HOST || !databaseName)throw new Error();
    const raw=process.env.REPS_TENANT_AUDIT_KEY || "", key=Buffer.from(raw,"base64");
    if(key.length!==32 || key.toString("base64")!==raw)throw new Error();
    const output=process.env.REPS_TENANT_AUDIT_OUT;
    if(!output || !path.isAbsolute(output))throw new Error();
    const parent=fs.realpathSync(path.dirname(output));
    const repo=fs.realpathSync(path.resolve(directory,"../.."));
    if(parent.toLowerCase()===repo.toLowerCase() || parent.toLowerCase().startsWith(repo.toLowerCase()+path.sep))throw new Error();
    const connection=await new Mongo(uri), database=connection.getDB(databaseName);
    const status=await database.runCommand({connectionStatus:1,showPrivileges:true});
    const privileges=status.authInfo?.authenticatedUserPrivileges;
    const readActions=new Set(["find","listCollections","listIndexes","collStats","dbStats","dbHash","planCacheRead","killCursors","changeStream","listSearchIndexes"]);
    if(!Array.isArray(privileges) || !privileges.length || privileges.some(p=>!Array.isArray(p.actions) || p.actions.some(a=>!readActions.has(a))))throw new Error();
    const projection={_id:1,tenantSchoolId:1,school:1,schoolName:1,schoolId:1,schoolIdNumber:1,username:1,email:1,studentEmail:1,
      teacherEmail:1,originalAuthorEmail:1,responsibleStaffEmail:1,guidanceEmail:1,adminEmail:1,spotters:1,"roles.role":1,"classes.classRoster":1,
      createdByUserId:1,createdBySystem:1,scope:1,assignmentTemplateId:1,linkToPunishment:1,actor:1,caseIds:1,studentIds:1,employeeIds:1,
      revision:1,profileRevision:1,rosterRevision:1,credentialVersion:1,ownerAccountId:1,stripeSubscriptionId:1,"assignmentWork.template.id":1,"assignmentWork.template.schoolId":1,"assignmentWork.template.tenantSchoolId":1,"requiredNotifications.actor":1,"requiredNotifications.state":1,"requiredNotifications.notificationId":1,"gmailAuthorization.state":1};
    // Projection never reads passwords, reset tickets, OAuth ciphertext, recipient/message content or student names.
    const schools=EJSON.serialize(await database.getCollection("schools").find({},{_id:1,schoolName:1}).limit(10001).maxTimeMS(30000).toArray(),{relaxed:false});
    if(schools.length>10000)throw new Error();
    const documents={};let total=0;
    for(const collection of core.COLLECTIONS){
      documents[collection]=EJSON.serialize(await database.getCollection(collection).find({},projection).limit(100001).maxTimeMS(30000).toArray(),{relaxed:false});
      total+=documents[collection].length;
      if(documents[collection].length>100000 || total>200000)throw new Error();
    }
    const snapshot=core.buildSnapshot(schools,documents,key,process.env.REPS_TENANT_AUDIT_SOURCE_ID,parsed.protocol+"//"+parsed.host+"/"+databaseName);
    key.fill(0);
    // Exclusive output; no overwriting prior evidence. HMAC metadata stays private and outside Git.
    fs.writeFileSync(path.join(parent,path.basename(output)),JSON.stringify(snapshot,null,2)+"\n",{flag:"wx",mode:0o600});
    print(JSON.stringify({readOnlyPrivilegesVerified:true,...core.summarize(snapshot)},null,2));
  } catch (_) {
    print("Tenant inventory failed safely. No database writes were attempted. Check approved target, read-only permissions, tool path and private output configuration; credentials/details are withheld.");
    exit(1);
  }
})();
