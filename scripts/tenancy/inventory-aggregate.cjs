"use strict";

const {randomBytes} = require("node:crypto");
const core = require("./audit-core.cjs");
const {projection} = require("./source-contract.cjs");

const collections = ["schools", ...core.COLLECTIONS, "currency_replay_manifests", "stripe_event_log"];
const fields = {...projection};
for (const key of ["assignmentWork", "requiredNotifications", "historicalAccess", "gmailAuthorization"]) delete fields[key];
for (const key of [
    "assignmentWork.template.id", "assignmentWork.template.schoolId", "assignmentWork.template.tenantSchoolId",
    "requiredNotifications.tenantSchoolId", "requiredNotifications.actorAccountId", "requiredNotifications.actorProfileId",
    "requiredNotifications.actorCredentialVersion", "requiredNotifications.state",
    "assignmentWork.submissions.template.id", "assignmentWork.submissions.template.tenantSchoolId",
    "caseIds", "studentIds", "employeeIds", "actorAccountId", "actorProfileId", "receiptVersion", "payloadHash", "kind", "state"
]) fields[key] = 1;

function projectionFor(collection) {
    if (collection === "schools") return {_id:1, schoolName:1};
    if (["currency_replay_manifests", "stripe_event_log"].includes(collection)) return {_id:1};
    return fields;
}

function collisions(names) {
    const counts = new Map();
    for (const name of names) counts.set(name, (counts.get(name) || 0) + 1);
    return [...counts.values()].filter(count => count > 1).length;
}

function aggregate(input) {
    const schools = input.schools;
    const key = randomBytes(32);
    let snapshot;
    try { snapshot = core.buildSnapshot(schools, input, key, "aggregate-only"); }
    finally { key.fill(0); }
    const summary = core.summarize(snapshot);
    const counts = Object.fromEntries(collections.map(collection => [collection, {total:input[collection].length}]));
    const ownership = Object.fromEntries(core.COLLECTIONS.map(collection => {
        const rows = snapshot.records.filter(row => row.collection === collection);
        return [collection, {
            missing: rows.filter(row => !row.tenantSchoolId && !row.invalidOwner && !row.globalEligible).length,
            invalid: summary.counts[collection].invalidOwner,
            conflicting: rows.filter(row => row.structuralConflict ||
                row.tenantSchoolId && row.explicitSchoolIds.some(id => id !== row.tenantSchoolId)).length,
            disabled: input[collection].filter(row => row.enabled === false).length,
            archived: input[collection].filter(row => row.archived === true).length
        }];
    }));
    const owners = new Map(snapshot.records.map(row => [row.recordKey, row.tenantSchoolId]));
    const relationships = {missing:0, ambiguous:0, crossSchool:0};
    const quarantined = new Set(summary.quarantine.map(row => row.recordKey));
    for (const row of snapshot.records) {
        for (const ref of [...row.references, ...row.components.flatMap(component => component.references)]) {
            if (!ref.targets.length) relationships.missing++;
            if (ref.targets.length > 1) relationships.ambiguous++;
            if (row.tenantSchoolId && ref.targets.some(id => owners.get(id) && owners.get(id) !== row.tenantSchoolId)) {
                relationships.crossSchool++;
                quarantined.add(row.recordKey);
            }
        }
    }
    const names = schools.map(school => school.schoolName).filter(name => typeof name === "string" && name.trim());
    const notificationBuckets = new Map(Object.entries({PENDING:"pending", FAILED:"failed", RECONNECT_REQUIRED:"failed", UNCERTAIN:"uncertain", ACCEPTED:"completed", RESOLVED:"completed"}));
    const notifications = {pending:0, failed:0, uncertain:0, completed:0, other:0};
    let notificationIncomplete = 0;
    let assignmentIncomplete = 0;
    for (const collection of ["Punishments", "OfficeReferrals", "Guidance"]) {
        for (const row of input[collection]) {
            if ((row.assignmentTemplateId || row.assignmentWork) && (!row.tenantSchoolId ||
                !row.assignmentWork?.template?.id ||
                (row.assignmentWork.template.tenantSchoolId && row.assignmentWork.template.tenantSchoolId !== row.tenantSchoolId))) assignmentIncomplete++;
            for (const intent of row.requiredNotifications || []) {
                const bucket = notificationBuckets.get(intent.state) || "other";
                notifications[bucket]++;
                if (!intent.tenantSchoolId || intent.tenantSchoolId !== row.tenantSchoolId ||
                    !intent.actorAccountId || !intent.actorProfileId || intent.actorCredentialVersion == null) notificationIncomplete++;
            }
        }
    }
    return {
        schemaVersion:"reps-inventory-aggregate/v1",
        consistency:"non-transactional-scan",
        readiness:"requires-reviewed-mapping",
        counts, ownership, relationships, notifications,
        schoolNames:{exactCollisionGroups:collisions(names), normalizedCollisionGroups:collisions(names.map(n => n.trim().replace(/\s+/g," ").toLowerCase())), missing:schools.length - names.length},
        quarantineRecords:quarantined.size,
        incompleteEvidence:{
            currency:snapshot.records.filter(row => row.receipt && (!row.tenantSchoolId || row.receipt.version !== 2 ||
                !row.receipt.receiptIdValid || !row.receipt.payloadHashPresent || !row.receipt.kindValid || !row.receipt.committed ||
                !["recorded-account", "recorded-profile"].every(relation => row.references.some(ref => ref.relation === relation && ref.targets.length === 1)))).length,
            assignments:assignmentIncomplete + ownership.assignment_template_bindings.missing + ownership.assignment_templates.missing,
            notifications:notificationIncomplete,
            gmail:input.oauth2_authorized_clients.filter(row => !row.tenantSchoolId || !row.ownerAccountId || !row.ownerProfileId).length,
            billing:input.registration_intents.filter(row => !row.schoolIdNumber || !row.stripeCustomerId || !row.stripeSubscriptionId).length
        }
    };
}

const schema = aggregate(Object.fromEntries(collections.map(collection => [collection, []])));
module.exports = {collections, projectionFor, aggregate, schema};
