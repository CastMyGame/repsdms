"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const policy = require("./dev-inventory-policy.cjs");
const tool = require("./dev-inventory.cjs");
const env = {REPS_DEV_INVENTORY_SYNTHETIC:"approved-disposable-fixture"};
const hello = {ok:1,setName:policy.replicaSet,isWritablePrimary:true,me:policy.member,
    primary:policy.member,hosts:[policy.member]};
const privileges = tool.collections.map(collection=>({resource:{db:policy.database,collection},actions:["find"]}));
const status = {ok:1,authInfo:{authenticatedUsers:[{user:policy.principal,db:"admin"}],
    authenticatedUserPrivileges:privileges}};

test("refuses target overrides and execution without disposable approval before connecting", async () => {
    for (const invalid of [{},{...env,REPS_DEV_INVENTORY_URI:policy.uri},
        {...env,REPS_TENANT_AUDIT_EXPECTED_HOST:"localhost"}]) {
        await assert.rejects(tool.run({env:invalid,connect(){assert.fail("connected");}}), /target refused/);
    }
    for (const uri of ["mongodb+srv://repsdevdb.xgrmfbb.mongodb.net/repsdevdb",
        "mongodb+srv://reps.risjic2.mongodb.net/repsdevdb",policy.uri.replace("tls=true","tls=false"),
        policy.uri.replace(policy.database,"other"),policy.uri+"&tlsAllowInvalidHostnames=true"]) {
        assert.throws(()=>policy.requireTarget(uri,env), /target refused/);
    }
});

test("wrong replica identity stops before password authentication", async () => {
    policy.requireReplica(hello);
    await assert.rejects(tool.run({env,checkPrivacy(){},connect:()=>({
        getDB:()=>({runCommand:()=>({...hello,hosts:[policy.member,"extra:27017"]}),
            auth(){assert.fail("authenticated");}})
    })}), /TLS and topology/);
});

test("only the exact authenticated principal and exact effective find grants pass", () => {
    policy.requirePrivileges(status,tool.collections);
    for (const extra of [{resource:{db:"",collection:""},actions:["find"]},
        {resource:{db:policy.database,collection:""},actions:["find"]},
        {resource:{db:"other",collection:"schools"},actions:["find"]},
        {resource:{db:policy.database,collection:"schools"},actions:["insert"]}]) {
        assert.throws(()=>policy.requirePrivileges({ok:1,authInfo:{...status.authInfo,
            authenticatedUserPrivileges:[...privileges,extra]}},tool.collections));
    }
    for (const changed of [{authenticatedUserPrivileges:undefined},
        {authenticatedUserPrivileges:privileges.slice(1)},
        {authenticatedUsers:[{user:"other",db:"admin"}]}]) {
        assert.throws(()=>policy.requirePrivileges({ok:1,authInfo:{...status.authInfo,...changed}},tool.collections));
    }
});

test("rejects unexpected aggregate fields before publication and sanitizes failure", async () => {
    const db = {runCommand:({hello:probe})=>probe?hello:status, auth:()=>1,
        getCollection:()=>({aggregate:()=>({toArray:()=>[{unexpected:"private-marker"}]})})};
    await assert.rejects(tool.run({env,checkPrivacy(){},connect:()=>({getDB:()=>db})}),
        {message:"Inventory refused or failed during aggregate scan."});
});
