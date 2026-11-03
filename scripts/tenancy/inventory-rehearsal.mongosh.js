(async function () {
    const assert = require("node:assert/strict");
    const safety = require("/tools/inventory-safety.cjs");
    const inventory = require("/tools/inventory-aggregate.cjs");
    const fs = require("node:fs");
    const adminConnection = await new Mongo(safety.uri);
    const admin = adminConnection.getDB("admin");
    await admin.createUser({user:"fixture_admin",pwd:"synthetic-admin-only",roles:["root"]});
    await admin.auth("fixture_admin","synthetic-admin-only");
    const fixture = adminConnection.getDB(safety.database);
    for (const collection of inventory.collections) await fixture.createCollection(collection);
    await fixture.schools.insertMany([{_id:"A",schoolName:"private-marker School"},{_id:"B",schoolName:" PRIVATE-MARKER SCHOOL "}]);
    await fixture.students.insertOne({_id:"private-marker-student",studentEmail:"private-marker@example.invalid",tenantSchoolId:"B"});
    await fixture.employee.insertOne({_id:"staff-A",email:"staff@example.invalid",tenantSchoolId:"A",classes:[{classRoster:["private-marker@example.invalid"]}]});
    await fixture.Punishments.insertOne({_id:"private-marker-case",tenantSchoolId:"A",infractionDescription:"private-marker",
        requiredNotifications:[{state:"UNCERTAIN",body:"private-marker"}]});
    await fixture.createRole({role:"inventory_find",privileges:inventory.collections.map(collection=>({
        resource:{db:safety.database,collection},actions:["find"]
    })),roles:[]});
    await fixture.createRole({role:"inventory_inherited",privileges:[],roles:["inventory_find"]});
    await fixture.createUser({user:safety.principal,pwd:process.env.REPS_TENANT_AUDIT_PASSWORD,roles:["inventory_inherited"]});
    const reader = (await new Mongo(safety.uri)).getDB(safety.database);
    await reader.auth(safety.principal,process.env.REPS_TENANT_AUDIT_PASSWORD);
    const status = await reader.runCommand({connectionStatus:1,showPrivileges:true});
    safety.requirePrivileges(status,inventory.collections);
    const incomplete = await reader.runCommand({connectionStatus:1});
    assert.throws(()=>safety.requirePrivileges(incomplete,inventory.collections));
    async function requireDenied(command) {
        try { const result = await reader.runCommand(command); assert.equal(13,result.code); }
        catch (error) { assert.equal(13,error.code); }
    }
    await requireDenied({insert:"schools",documents:[{_id:"forbidden"}]});
    await requireDenied({createIndexes:"schools",indexes:[{key:{schoolName:1},name:"forbidden"}]});
    fs.writeFileSync("/tmp/inventory-output/hash",(await fixture.runCommand({dbHash:1})).md5,{mode:0o600});
    const output = "/tmp/inventory-output/interrupted.json";
    const empty = inventory.aggregate(Object.fromEntries(inventory.collections.map(c=>[c,[]])));
    assert.throws(()=>safety.publish(output,empty,inventory.schema,fd=>{
        fs.writeFileSync(fd,"incomplete");
        throw Error("simulated interruption");
    }));
    assert(!fs.existsSync(output));
    assert(!fs.readdirSync("/tmp/inventory-output").some(name=>name.startsWith(".inventory-")));
    assert.throws(()=>safety.publish(output,{...empty,student:"private-marker"},inventory.schema));
})();
