"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const safety = require("./inventory-safety.cjs");
const inventory = require("./inventory-aggregate.cjs");

test("launcher refuses unsafe targets and host spoofing before constructing a connection", async () => {
    const source = fs.readFileSync(path.join(__dirname,"inventory.mongosh.js"),"utf8");
    for (const override of [
        {REPS_TENANT_AUDIT_URI:"mongodb+srv://production.example.invalid/reps_inventory_fixture", REPS_TENANT_AUDIT_EXPECTED_HOST:"production.example.invalid"},
        {REPS_TENANT_AUDIT_URI:safety.uri.replace("27031","27032")},
        {REPS_TENANT_AUDIT_URI:safety.uri+"&tlsAllowInvalidCertificates=true"},
        {REPS_TENANT_AUDIT_URI:safety.uri.replace("127.0.0.1","127.0.0.1,example.invalid")},
        {REPS_TENANT_AUDIT_DATABASE:"production"},
        {REPS_TENANT_AUDIT_EXPECTED_HOST:"127.0.0.1:27031"}
    ]) {
        let connections = 0;
        let code;
        await vm.runInNewContext(source,{
            require, process:{env:{REPS_TENANT_AUDIT_TOOL_DIR:__dirname,REPS_TENANT_AUDIT_URI:safety.uri,
                REPS_TENANT_AUDIT_DATABASE:safety.database,REPS_TENANT_AUDIT_APPROVED:"read-only-inventory-approved",...override}},
            Mongo:class { constructor(){connections++;} },print:()=>{},exit:value=>{code=value;}
        });
        assert.equal(connections,0);
        assert.equal(code,1);
    }
    assert.doesNotThrow(()=>safety.requireTarget(safety.uri,safety.database));
});

test("effective grants must exactly cover required collections with find only", () => {
    const make = () => ({ok:1,authInfo:{
        authenticatedUsers:[{user:safety.principal,db:safety.database}],
        authenticatedUserPrivileges:inventory.collections.map(collection=>({
            resource:{db:safety.database,collection},actions:["find"]
        }))
    }});
    safety.requirePrivileges(make(),inventory.collections);
    for (const modify of [
        p=>p.authenticatedUserPrivileges.pop(),
        p=>delete p.authenticatedUserPrivileges,
        p=>p.authenticatedUsers[0].user="other",
        p=>p.authenticatedUserPrivileges[0].resource.collection="",
        p=>p.authenticatedUserPrivileges[0].resource.db="",
        p=>p.authenticatedUserPrivileges[0].resource.db="production",
        p=>p.authenticatedUserPrivileges[0].resource={anyResource:true},
        p=>p.authenticatedUserPrivileges[0].resource.collection="extra_collection",
        p=>p.authenticatedUserPrivileges[0].actions.push("insert"),
        p=>p.authenticatedUserPrivileges[0].actions.push("createIndex")
    ]) {
        const status = make();
        modify(status.authInfo);
        assert.throws(()=>safety.requirePrivileges(status,inventory.collections));
    }
});

test("aggregate report exposes counts without identifiers or private content", () => {
    const input = Object.fromEntries(inventory.collections.map(collection=>[collection,[]]));
    input.schools.push({_id:"private-A",schoolName:"Private Name"},{_id:"private-B",schoolName:" private name "});
    input.students.push({_id:"private-student",studentEmail:"private@example.invalid",tenantSchoolId:"private-B"});
    input.employee.push({_id:"private-employee",tenantSchoolId:"private-A",classes:[{classRoster:["private@example.invalid"]}]});
    input.Punishments.push({_id:"private-case",tenantSchoolId:"private-A",infractionDescription:"private details",
        requiredNotifications:[{state:"UNCERTAIN",body:"private message"}]});
    const result = inventory.aggregate(input);
    assert.equal(result.schoolNames.exactCollisionGroups,0);
    assert.equal(result.schoolNames.normalizedCollisionGroups,1);
    assert.equal(result.relationships.crossSchool,1);
    assert.equal(result.notifications.uncertain,1);
    assert.equal(result.incompleteEvidence.notifications,1);
    assert.doesNotMatch(JSON.stringify(result),/private/i);
});
