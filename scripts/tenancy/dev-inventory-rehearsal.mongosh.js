(async function () {
    const p = require("/tools/dev-inventory-policy.cjs");
    const {collections} = require("/tools/dev-inventory.cjs");
    const fs = require("node:fs");
    const assert = require("node:assert/strict");
    const stage = process.env.FIXTURE_STAGE;
    if (stage === "wrong-hostname") {
        await assert.rejects(async () => {
            const wrong = await new Mongo(p.uri.replace("localhost","127.0.0.1"));
            await wrong.getDB("admin").runCommand({hello:1});
        }, /does not match certificate|not in the cert/i);
        return;
    }
    const c = await new Mongo(p.uri.replace("&replicaSet="+p.replicaSet,""));
    const admin = c.getDB("admin");
    if (stage === "init") {
        assert.equal(1,(await admin.runCommand({replSetInitiate:{_id:p.replicaSet,members:[{_id:0,host:p.member}]}})).ok);
        return;
    }
    if (stage === "ready") {
        assert.equal(true,(await admin.runCommand({hello:1})).isWritablePrimary);
        return;
    }
    const db = c.getDB(p.database);
    const adminPassword = process.env.REPS_DEV_INVENTORY_ADMIN_PASSWORD;
    if (!adminPassword) throw Error("Fixture administrator credential missing");
    if (stage === "seed") await admin.createUser({user:"fixture_admin",pwd:adminPassword,roles:["root"]});
    await admin.auth("fixture_admin",adminPassword);
    async function snapshot() {
        return {hash:(await db.runCommand({dbHash:1})).md5,indexes:await Promise.all(
            collections.map(name=>db.getCollection(name).getIndexes()))};
    }
    if (stage === "seed") {
        for (const name of collections) await db.createCollection(name);
        const id = "012345678901234567890123";
        await db.schools.insertMany([{_id:"A",schoolName:"private-marker School"},
            {_id:"B",schoolName:"private-marker School"},{_id:id},{_id:ObjectId(id)}]);
        await db.students.insertMany([{tenantSchoolId:"A",email:"private-marker@example.invalid"},
            {tenantSchoolId:"missing-school"},{},{tenantSchoolId:id},{tenantSchoolId:["A"]}]);
        await admin.createRole({role:"dev_inventory_find",privileges:collections.map(collection=>({
            resource:{db:p.database,collection},actions:["find"]
        })),roles:[]});
        await admin.createUser({user:p.principal,pwd:process.env.REPS_DEV_INVENTORY_PASSWORD,roles:["dev_inventory_find"]});
        fs.writeFileSync("/tmp/dev-inventory/before.json",JSON.stringify(await snapshot()));
    } else if (stage === "extra") {
        await admin.grantRolesToUser(p.principal,[{role:"readWrite",db:p.database}]);
    } else if (stage === "restrict") {
        await admin.revokeRolesFromUser(p.principal,[{role:"readWrite",db:p.database}]);
    } else if (stage === "verify") {
        const raw = fs.readFileSync("/tmp/dev-inventory/report.json","utf8");
        assert(!raw.includes("private-marker"));
        const report = JSON.parse(raw);
        assert.deepEqual(report.schools,{total:4,exactCollisionGroups:1,normalizedCollisionGroups:1});
        assert.deepEqual(report.ownership.students,{total:5,missing:1,invalid:2,conflicting:1,present:1});
        assert.equal(JSON.stringify(await snapshot()),fs.readFileSync("/tmp/dev-inventory/before.json","utf8"));
    } else {
        throw Error("Unknown fixture stage");
    }
})();
