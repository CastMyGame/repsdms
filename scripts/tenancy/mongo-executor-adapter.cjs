"use strict";
const core=require("./audit-core.cjs"),exec=require("./local-executor.cjs");
const IMMUTABLE=["currency_operations","oauth2_authorized_clients","registration_intents"];
function typeChecks(expression,value,checks=[]){
    let type=Array.isArray(value)?"array":value===null?"null":typeof value==="object"?"object":{string:"string",boolean:"bool",number:"double"}[typeof value];
    if(type==="object"){
        const keys=Object.keys(value),tags={$numberInt:"int",$numberLong:"long",$numberDouble:"double",$numberDecimal:"decimal",$date:"date",$oid:"objectId",$binary:"binData",$timestamp:"timestamp",$regularExpression:"regex",$minKey:"minKey",$maxKey:"maxKey",$undefined:"undefined"};
        if(keys.length===1&&tags[keys[0]])type=tags[keys[0]];
        if(Object.hasOwn(value,"$code"))type=Object.hasOwn(value,"$scope")?"javascriptWithScope":"javascript";
    }
    if(!type||checks.length>10000)throw Error("Unsupported typed CAS source");
    checks.push({$eq:[{$type:expression},type]});
    if(type==="array")value.forEach((v,i)=>typeChecks({$arrayElemAt:[expression,i]},v,checks));
    if(type==="object")for(const [k,v]of Object.entries(value))typeChecks({$getField:{field:{$literal:k},input:expression}},v,checks);
    return checks;
}
function adapter(connection,database,EJSON){
    const db=connection.getDB(database);let session=null,current=db,hashes=null;
    const fail=code=>{const e=new Error(code);e.safeCode=code;throw e;};
    async function verifyTarget(expected){
        const hello=await db.runCommand({hello:1});
        if(hello.maxWireVersion<13||hello.setName!=="repsa7exec"||!Array.isArray(hello.hosts)||hello.hosts.some(h=>h!=="127.0.0.1:27029"))fail("UNKNOWN_SERVER");
        const marker=await db.getCollection(exec.CONTROL).findOne({_id:"local-disposable/v1"});
        if(!marker||marker.purpose!=="a7-phase5c-synthetic"||marker.database!==database||marker.expiresAt<=Date.now()||core.sha(structuredClone(EJSON.serialize(marker,{relaxed:false})))!==expected)fail("UNKNOWN_DISPOSABLE_TARGET");
    }
    async function counts(){const result={};for(const c of ["schools",...core.COLLECTIONS])result[c]=await db.getCollection(c).countDocuments({});return result;}
    async function immutableHashes(){const result=await db.runCommand({dbHash:1,collections:IMMUTABLE});if(result.ok!==1)fail("IMMUTABLE_HASH_FAILED");return Object.fromEntries(IMMUTABLE.map(c=>[c,result.collections[c]||"absent"]));}
    async function read(){
        const names=session?[]:await current.getCollectionNames();if(names.some(c=>!["schools",exec.CONTROL,...core.COLLECTIONS].includes(c)))fail("UNKNOWN_COLLECTION");
        const result={};let total=0;
        for(const c of ["schools",...core.COLLECTIONS]){const cursor=await current.getCollection(c).find({});await cursor.limit(1001);await cursor.maxTimeMS(5000);const documents=await cursor.toArray();total+=documents.length;if(documents.length>1000||total>5000)fail("LOCAL_LIMIT_EXCEEDED");result[c]=structuredClone(EJSON.serialize(documents,{relaxed:false}));}
        if(!session)hashes=await immutableHashes();result.immutableHashes=hashes;return result;
    }
    async function update(u){
        if(!exec.ROOTS.includes(u.collection)||Object.keys(u.set).join()!=="tenantSchoolId")fail("FORBIDDEN_UPDATE");
        const original=EJSON.deserialize(u.document),filter={_id:original._id,tenantSchoolId:{$exists:false},$expr:{$and:[{$eq:["$$ROOT",{$literal:original}]},...typeChecks("$$ROOT",u.document)]}};
        for(const field of ["revision","persistenceRevision","profileRevision","rosterRevision","credentialVersion"])filter[field]=Object.hasOwn(original,field)?{$eq:original[field]}:{$exists:false};
        const result=await current.getCollection(u.collection).updateOne(filter,{$set:{tenantSchoolId:u.set.tenantSchoolId}},{upsert:false});
        return {matchedCount:result.matchedCount,modifiedCount:result.modifiedCount};
    }
    async function begin(){session=await connection.startSession({causalConsistency:false});await session.startTransaction({readConcern:{level:"snapshot"},writeConcern:{w:"majority"}});current=await session.getDatabase(database);}
    async function commit(){await session.commitTransaction();await session.endSession();session=null;current=db;}
    async function abort(){await session.abortTransaction();await session.endSession();session=null;current=db;}
    async function close(){if(session){await session.endSession();session=null;current=db;}}
    return {verifyTarget,counts,read,update,begin,commit,abort,close};
}
module.exports={adapter,IMMUTABLE,typeChecks};
