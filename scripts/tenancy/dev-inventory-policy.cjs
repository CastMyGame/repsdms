"use strict";

// No deployed target is authorized by this implementation.
const database = "reps_dev_inventory_fixture";
const authDatabase = "admin";
const principal = "dev_inventory_reader";
const member = "localhost:27033";
const replicaSet = "repsdevinventory";
const uri = "mongodb://" + member + "/" + database +
    "?tls=true&tlsCAFile=/tmp/dev-inventory/ca.crt&directConnection=true&replicaSet=" +
    replicaSet + "&serverSelectionTimeoutMS=3000";

function requireTarget(input, env) {
    if (env.REPS_DEV_INVENTORY_SYNTHETIC !== "approved-disposable-fixture" ||
        Object.keys(env).some(key => /^REPS_(?:DEV_INVENTORY|TENANT_AUDIT)_(?:URI|DATABASE|EXPECTED_HOST|HOST|TARGET)/.test(key)) ||
        input !== uri) throw Error("Inventory target refused");
}

function requireReplica(hello) {
    if (hello.ok !== 1 || hello.setName !== replicaSet || hello.isWritablePrimary !== true ||
        hello.me !== member || hello.primary !== member || hello.hosts?.length !== 1 ||
        hello.hosts[0] !== member || hello.passives?.length || hello.arbiters?.length) {
        throw Error("Inventory topology refused");
    }
}

function requirePrivileges(status, collections) {
    const info = status.authInfo;
    if (status.ok !== 1 || info?.authenticatedUsers?.length !== 1 ||
        info.authenticatedUsers[0].user !== principal || info.authenticatedUsers[0].db !== authDatabase ||
        !Array.isArray(info.authenticatedUserPrivileges)) throw Error("Inventory identity refused");
    const remaining = new Set(collections);
    for (const {resource, actions} of info.authenticatedUserPrivileges) {
        if (!resource || Object.keys(resource).sort().join(",") !== "collection,db" ||
            resource.db !== database || !remaining.delete(resource.collection) ||
            !Array.isArray(actions) || actions.length !== 1 || actions[0] !== "find") {
            throw Error("Inventory privileges refused");
        }
    }
    if (remaining.size) throw Error("Inventory privileges incomplete");
}

module.exports = {database, authDatabase, principal, member, replicaSet, uri, requireTarget, requireReplica, requirePrivileges};
