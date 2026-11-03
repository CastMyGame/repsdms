#!/bin/bash
set -euo pipefail
umask 077
mkdir -m 700 /tmp/inventory-output
head -c 756 /dev/urandom | base64 -w 0 >/tmp/replica.key
mongod --fork --logpath /tmp/mongod.log --dbpath /data/db --bind_ip 127.0.0.1 --port 27031 --replSet repsinventory --keyFile /tmp/replica.key >/dev/null
echo "8d24940ebe3675959b518fd41ea16b4e2182d6a75c09777db09cfa64e18834a1  /opt/inventory-mongosh.tgz" | sha256sum -c --status
tar -xzf /opt/inventory-mongosh.tgz -C /opt
cat >/etc/mongosh.conf <<'CONFIG'
mongosh:
  disableLogging: true
  disableSchemaSampling: true
  forceDisableTelemetry: true
  historyLength: 0
  updateURL: ""
  snippetAutoload: false
  browser: false
CONFIG
shell=(/opt/mongosh-2.14.1-linux-x64/bin/mongosh --norc --quiet --nodb)
"${shell[@]}" --eval 'const c=new Mongo("mongodb://127.0.0.1:27031/?directConnection=true"); if(c.getDB("admin").runCommand({replSetInitiate:{_id:"repsinventory",members:[{_id:0,host:"127.0.0.1:27031"}]}}).ok!==1) quit(1);'
for i in $(seq 1 30); do
  if "${shell[@]}" --eval 'const c=new Mongo("mongodb://127.0.0.1:27031/?directConnection=true"); quit(c.getDB("admin").runCommand({hello:1}).isWritablePrimary ? 0 : 1)' >/dev/null 2>&1; then break; fi
  sleep 1
done
export REPS_TENANT_AUDIT_TOOL_DIR=/tools
export REPS_TENANT_AUDIT_DATABASE=reps_inventory_fixture
export REPS_TENANT_AUDIT_URI='mongodb://127.0.0.1:27031/reps_inventory_fixture?replicaSet=repsinventory&directConnection=true&serverSelectionTimeoutMS=3000'
export REPS_TENANT_AUDIT_APPROVED=read-only-inventory-approved
export REPS_TENANT_AUDIT_PASSWORD=synthetic-reader-only
export REPS_TENANT_AUDIT_OUT=/tmp/inventory-output/report.json
"${shell[@]}" --file /tools/inventory-rehearsal.mongosh.js
"${shell[@]}" --file /tools/inventory.mongosh.js
test "$(stat -c '%a' "$REPS_TENANT_AUDIT_OUT")" = 600
"${shell[@]}" --eval 'const f=require("node:fs"); const r=JSON.parse(f.readFileSync(process.env.REPS_TENANT_AUDIT_OUT)); require("node:assert/strict").equal(2,r.counts.schools.total); require("node:assert/strict").equal(1,r.schoolNames.normalizedCollisionGroups); require("node:assert/strict").equal(1,r.relationships.crossSchool); require("node:assert/strict").equal(1,r.incompleteEvidence.notifications); require("node:assert/strict")(!JSON.stringify(r).includes("private-marker"));'
expect_refusal() {
  local stage="$1" response
  shift
  if response=$(env "$@" "${shell[@]}" --file /tools/inventory.mongosh.js); then
    echo "Expected refusal during $stage" >&2
    exit 1
  fi
  test "$response" = "Inventory refused or failed during $stage."
}
expect_refusal 'aggregate publication'
export REPS_TENANT_AUDIT_OUT=/tmp/inventory-output/refused.json
expect_refusal preflight REPS_TENANT_AUDIT_DATABASE=production
expect_refusal preflight REPS_TENANT_AUDIT_EXPECTED_HOST=127.0.0.1:27031
expect_refusal preflight REPS_TENANT_AUDIT_URI=mongodb+srv://production.example.invalid/reps_inventory_fixture
expect_refusal preflight REPS_TENANT_AUDIT_URI=mongodb://127.0.0.1:27032/reps_inventory_fixture
expect_refusal authentication REPS_TENANT_AUDIT_PASSWORD=incorrect
test ! -e "$REPS_TENANT_AUDIT_OUT"
"${shell[@]}" --eval 'const c=new Mongo(process.env.REPS_TENANT_AUDIT_URI); const a=c.getDB("admin"); a.auth("fixture_admin","synthetic-admin-only"); c.getDB("reps_inventory_fixture").grantRolesToUser("inventory_reader",["readWrite"]);' >/dev/null
expect_refusal 'effective privileges'
"${shell[@]}" --eval 'const c=new Mongo(process.env.REPS_TENANT_AUDIT_URI); const a=c.getDB("admin"); a.auth("fixture_admin","synthetic-admin-only"); const d=c.getDB("reps_inventory_fixture"); d.revokeRolesFromUser("inventory_reader",["readWrite"]); d.grantRolesToUser("inventory_reader",["read"]);' >/dev/null
expect_refusal 'effective privileges'
test ! -e "$REPS_TENANT_AUDIT_OUT"
"${shell[@]}" --eval 'const c=new Mongo(process.env.REPS_TENANT_AUDIT_URI); c.getDB("admin").auth("fixture_admin","synthetic-admin-only"); const d=c.getDB("reps_inventory_fixture"); require("node:assert/strict").equal(require("node:fs").readFileSync("/tmp/inventory-output/hash","utf8"),d.runCommand({dbHash:1}).md5);'
echo "Authenticated inventory rehearsal passed."
