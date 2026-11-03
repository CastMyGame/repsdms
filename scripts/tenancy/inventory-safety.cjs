"use strict";

const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

// Release policy. Deployed targets require a separately reviewed code change.
const database = "reps_inventory_fixture";
const principal = "inventory_reader";
const uri = "mongodb://127.0.0.1:27031/reps_inventory_fixture?replicaSet=repsinventory&directConnection=true&serverSelectionTimeoutMS=3000";

function requireTarget(candidate, requestedDatabase) {
    const parsed = new URL(candidate);
    if (parsed.protocol !== "mongodb:" || parsed.pathname !== "/" + database ||
        requestedDatabase !== database || candidate !== uri) {
        throw Error("Unapproved inventory target");
    }
}

function requirePrivileges(status, collections) {
    const { authenticatedUsers, authenticatedUserPrivileges } = status.authInfo || {};
    if (status.ok !== 1 || !Array.isArray(authenticatedUsers) ||
        authenticatedUsers.length !== 1 || authenticatedUsers[0].user !== principal ||
        authenticatedUsers[0].db !== database ||
        !Array.isArray(authenticatedUserPrivileges)) throw Error("Unverified inventory identity");
    const remaining = new Set(collections);
    for (const {resource, actions} of authenticatedUserPrivileges) {
        if (!resource || Object.keys(resource).sort().join(",") !== "collection,db" ||
            resource.db !== database || !remaining.delete(resource.collection) ||
            !Array.isArray(actions) || actions.length !== 1 || actions[0] !== "find") {
            throw Error("Excessive inventory privileges");
        }
    }
    if (remaining.size) throw Error("Incomplete inventory privileges");
}

function publish(output, report, schema, write = fs.writeFileSync) {
    // POSIX permission checks cannot attest a Windows ACL.
    if (typeof process.getuid !== "function" || !path.isAbsolute(output)) throw Error("Private POSIX output required");
    function validate(value, shape) {
        if (typeof shape === "number") return Number.isSafeInteger(value) && value >= 0;
        if (typeof shape === "string") return value === shape;
        return value && typeof value === "object" && !Array.isArray(value) &&
            Object.keys(value).length === Object.keys(shape).length &&
            Object.entries(shape).every(([key, child]) => Object.hasOwn(value, key) && validate(value[key], child));
    }
    if (!validate(report, schema)) throw Error("Invalid aggregate report");
    const parent = fs.realpathSync(path.dirname(output));
    const stat = fs.statSync(parent);
    if (stat.uid !== process.getuid() || (stat.mode & 0o077) !== 0) throw Error("Private output directory required");
    for (let dir = parent; ; dir = path.dirname(dir)) {
        if (fs.existsSync(path.join(dir, ".git"))) throw Error("Output must be outside Git");
        if (dir === path.dirname(dir)) break;
    }
    const temporary = path.join(parent, ".inventory-" + crypto.randomUUID());
    const fd = fs.openSync(temporary, "wx", 0o600);
    try {
        try {
            write(fd, JSON.stringify(report, null, 2) + "\n");
            fs.fsyncSync(fd);
        } finally {
            fs.closeSync(fd);
        }
        fs.linkSync(temporary, path.join(parent, path.basename(output)));
    } finally {
        fs.unlinkSync(temporary);
    }
}

module.exports = {database, principal, uri, requireTarget, requirePrivileges, publish};
