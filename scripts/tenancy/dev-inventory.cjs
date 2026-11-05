"use strict";

const {publish} = require("./inventory-safety.cjs");
const policy = require("./dev-inventory-policy.cjs");
const roots = Object.freeze(require("./audit-core.cjs").COLLECTIONS.slice(0, 9));
const collections = Object.freeze(["schools", ...roots]);
const zero = {total:0, missing:0, invalid:0, conflicting:0, present:0};
const schoolZero = {total:0, exactCollisionGroups:0, normalizedCollisionGroups:0};
const schema = {
    version:"reps-dev-inventory/v1",
    consistency:"non-transactional-scan",
    schools:schoolZero,
    ownership:Object.fromEntries(roots.map(root => [root, zero]))
};

// Both BSON string and ObjectId school keys can represent the same Java School ID.
// Count that ambiguity rather than selecting one. Display names never participate in ownership matching.
function ownershipPipeline() {
    return [
        {$project:{owner:"$tenantSchoolId", _id:0}},
        {$set:{type:{$type:"$owner"}, oid:{$convert:{input:"$owner",to:"objectId",onError:null,onNull:null}}}},
        {$set:{keys:{$cond:[{$eq:["$type","string"]},
            {$cond:[{$eq:["$owner",{$toString:"$oid"}]},["$owner","$oid"],["$owner"]]},[]]}}},
        {$lookup:{from:"schools",localField:"keys",foreignField:"_id",as:"matches"}},
        {$project:{bucket:{$switch:{branches:[
            {case:{$in:["$type",["missing","null"]]},then:"missing"},
            {case:{$ne:["$type","string"]},then:"invalid"},
            {case:{$eq:[{$trim:{input:{$convert:{input:"$owner",to:"string",onError:"",onNull:""}}}},""]},then:"invalid"},
            {case:{$eq:[{$size:"$matches"},0]},then:"invalid"},
            {case:{$gt:[{$size:"$matches"},1]},then:"conflicting"}
        ],default:"present"}}}},
        {$group:{_id:null,total:{$sum:1},...Object.fromEntries(
            Object.keys(zero).filter(key=>key!=="total").map(key=>[key,{$sum:{$cond:[{$eq:["$bucket",key]},1,0]}}]))}},
        {$project:{_id:0}}
    ];
}

function schoolPipeline() {
    const collisions = field => [
        {$match:{[field]:{$nin:["",null]}}},
        {$group:{_id:"$"+field,count:{$sum:1}}},
        {$match:{count:{$gt:1}}},
        {$count:"count"}
    ];
    return [
        {$project:{name:{$cond:[{$eq:[{$type:"$schoolName"},"string"]},"$schoolName",null]},_id:0}},
        {$set:{normalized:{$toLower:{$trim:{input:"$name"}}}}},
        {$facet:{total:[{$count:"count"}],exactCollisionGroups:collisions("name"),
            normalizedCollisionGroups:collisions("normalized")}},
        {$project:Object.fromEntries(Object.keys(schoolZero).map(key=>[key,
            {$ifNull:[{$arrayElemAt:["$"+key+".count",0]},0]}]))}
    ];
}

async function readCounts(db, collection, pipeline, shape) {
    const handle = await db.getCollection(collection);
    const cursor = await handle.aggregate(pipeline, {
        allowDiskUse:false, maxTimeMS:5000, collation:{locale:"simple"}
    });
    const rows = await cursor.toArray();
    if (rows.length > 1) throw Error("Unexpected aggregate rows");
    const row = rows[0] ?? shape;
    if (Object.keys(row).length !== Object.keys(shape).length ||
        !Object.keys(shape).every(key=>Object.hasOwn(row,key) && Number.isSafeInteger(row[key]) && row[key]>=0)) {
        throw Error("Unexpected aggregate fields");
    }
    return row;
}

async function run({env, connect, checkPrivacy, output}) {
    policy.requireTarget(policy.uri, env);
    await checkPrivacy();
    let stage = "TLS and topology";
    try {
        const connection = await connect(policy.uri);
        const admin = connection.getDB(policy.authDatabase);
        policy.requireReplica(await admin.runCommand({hello:1}));
        stage = "authentication";
        const authenticated = await admin.auth(policy.principal, env.REPS_DEV_INVENTORY_PASSWORD);
        if (authenticated?.ok !== 1 && authenticated !== 1) throw Error();
        stage = "effective privileges";
        policy.requirePrivileges(await admin.runCommand({connectionStatus:1,showPrivileges:true}), collections);
        stage = "aggregate scan";
        const db = connection.getDB(policy.database);
        const report = {...schema, schools:await readCounts(db,"schools",schoolPipeline(),schoolZero),ownership:{}};
        for (const collection of roots) {
            const row = await readCounts(db,collection,ownershipPipeline(),zero);
            if (row.total !== row.missing+row.invalid+row.conflicting+row.present) throw Error();
            report.ownership[collection] = row;
        }
        stage = "aggregate publication";
        publish(output, report, schema);
    } catch (_) {
        throw Error("Inventory refused or failed during " + stage + ".");
    }
}

module.exports = {collections, ownershipPipeline, schoolPipeline, run};
