# A7 Phase 5C — local conditional executor and MongoDB rehearsal

Baseline: merged #187, `e031efc`. **Local synthetic use only.** There is no deployed mode, remote allowlist override, startup backfill, HTTP endpoint or runtime activation. The v2 offline plan remains `executable:false`/`activationReady:false`; only a separate signed local execution authorization can enable its narrow root descriptors.

## Scope and gates

`Invoke-LocalTenantMigration.ps1` / `execute-local.mongosh.js` default to dry-run. Accepted URI is exactly:

```text
mongodb://127.0.0.1:27029/?replicaSet=repsa7exec&directConnection=true&serverSelectionTimeoutMS=5000
```

Only `reps_a7_executor_<32 lowercase hex>` databases are accepted. No localhost aliases, alternate ports/query options, credentials, SRV/Atlas or remote endpoints are allowed. The server must advertise the single loopback member `127.0.0.1:27029`, replica set `repsa7exec`, and MongoDB 5+ capability. A nonexpired, signed-digest-pinned `a7_executor_control` marker must identify that exact database as `a7-phase5c-synthetic`. Unknown targets are refused before application collection reads. The executor never creates the marker.

Manifest schema validation uses the committed v2 schema, with a strict built-in evaluator for its vocabulary: unsupported future keywords fail closed. The plan is recomputed and compared in full; changing a digest, update descriptor, scope, component review, source or artifact is not permission to run it.

Both modes require independent operator approval evidence. `REPS_TENANT_AUDIT_KEY` and `REPS_LOCAL_EXECUTOR_APPROVAL_KEY` are distinct canonical-Base64 32-byte process-environment values, never command arguments. Do not reuse Gmail/JWT keys. The rehearsal generates an ephemeral signing key internally; no real key is generated, uploaded or rotated.

An approval envelope contains `payload` and an HMAC-SHA256 `signature` under the independent operator key. The domain-separated payload pins:

- `schema: reps-local-migration-approval/v1`, `scope: validate-only` or `execute-root-ownership`, opaque review `reference` and `runId`;
- issue/expiry times (maximum one hour), exact URI/database, target marker digest and chosen atomicity mode;
- manifest, plan and committed schema digests, full-source precondition digest, and explicit `writersStopped: true` attestation.

`Execute` requires both explicit execution mode AND a matching `execute-root-ownership` approval. A valid manifest, validate-only approval, old checkpoint, test marker or offline plan alone cannot authorize a write. Keys/evidence need operator-controlled private custody; the HMAC is not an IAM role, proof of human review or proof writers stopped. No future deployed authorization is granted by this local scheme.

## Preconditions and writes

The v2 inventory projection is shared with the read-only exporter, without changing its fingerprint format. Local execution additionally reads the **complete synthetic source documents**, including otherwise omitted fields, and pins a private HMAC over canonical-EJSON data and immutable-collection server `dbHash` values. Existing fields/array order/types and all unselected records are protected. Only the plan's originally absent allowed root ownership fields are normalized out to permit legitimate interruption/rerun. All other ownership states remain pinned.

Typed IDs are resolved using the same HMAC record-key algorithm. Counts, school set, references, roles, protected fingerprints, exact planned owner and all v2 compatibility reviews are revalidated. The full-record conditional update requires exact ID, absent ownership, explicit revision/version predicates, whole-document equality and recursive BSON-type equality. Numerically equal `int`, `long`, `double` or decimal revisions cannot bypass a stale-source check. There is no upsert or replacement save.

Only `$set: {tenantSchoolId: <reviewed exact ID>}` is possible on `users`, `employee`, `students`, `Punishments`, `OfficeReferrals`, `Guidance`, `TrackedBehaviorEvents`, `assignment_templates`, `assignment_template_bindings`. There are no receipt, grant, billing, embedded history, answer, notification, balance, key or relationship writes. Unsupported/ambiguous records remain quarantined, not guessed. Root additions do not resolve v2 embedded ownership holds or enable canonical runtime access.

## Atomicity, interruption and concurrency

- **Transaction:** one SNAPSHOT/MAJORITY transaction contains the executor's root updates. Tests demonstrate all 12 ownership additions committing together and rollback of two staged additions on failure. No automatic transaction-body retry or fallback occurs. This does not make journals, approval/inventory reads, external writers or the entire application cutover atomic.
- **PerRecord:** each full-document CAS is atomic; the batch is not. Tests demonstrate two committed additions, an explicit partial-failure report, then ten remaining additions on an approved rerun. No rollback/reconciliation erases those fields.
- **Unknown commit/write:** stop and report `UNCERTAIN` with `committed:null`; never automatically repeat the body. The tested lost-response fault is injected **after a real commit**, not a real network partition/failover. A separately initiated, still-approved rerun revalidates source and counts already-owned documents.
- **Hard process exit:** a real child exits after its second acknowledged CAS, before the second APPLIED journal event. Rerun discovers two actual owners in MongoDB and applies only the remaining ten. A checkpoint never overrides database/source evidence.
- **Concurrent change:** stale whole-source evidence or failed CAS stops the run. Changes to target revisions and numerically equal BSON types are tested. There is no cross-process lock or proof that unrelated writers stopped: the explicit maintenance attestation remains required. Transactional dependency reads cannot guarantee that external application writers are quiescent.

Operator rerun requires the same reviewed scope, unexpired approval, unchanged remaining source and a fresh report path. Never re-sign changed source automatically to bypass a conflict, change receipt/action IDs, clear ownership, modify states or invent provenance.

## Sanitized durable journal

A new report file outside the repository is created exclusively with private permissions. Each event is synchronously written/fsynced before the next operation: START, BEFORE_UPDATE, STAGED/APPLIED, COMMITTED and FINAL. A run/attempt UUID, HMAC record key and collection name identify progress without raw IDs, emails, content or keys.

Final report `reps-local-migration-report/v1` contains `mode`, `atomicity`, `status`, safe reason code, plan digest, `runId`/`attemptId`, planned-update count, baseline already-bound count, observed already-bound progress, attempted/committed counts and confirmed lower bound before uncertainty, quarantined/component-hold counts, before/after collection counts and `activationReady:false`.

Statuses include DRY_RUN_VALIDATED, COMPLETED, FAILED_PRECONDITION, FAILED_PARTIAL, ROLLED_BACK, UNCERTAIN and COMMITTED_VALIDATION_OR_REPORT_FAILURE. A known commit followed by a journal/validation failure is not reported as rollback. If the process/disk prevents a FINAL event, treat the journal as incomplete and reconstruct progress through a new validated run; do not assume missing APPLIED means no write occurred. An invalid approval may be rejected before any journal/connection is created.

## Exact disposable rehearsal (PowerShell from this backend checkout)

Requires Docker Desktop already available and an approved cached MongoDB image. This run used cached image `ad6a13630a36` (MongoDB 6, mongosh 1.10/Node 16). `--pull=never` prevents downloads. If unavailable, obtain separate image/install approval; do not replace the URI with a shared database. The process-local built-in V8 clone shim handles the old shell without weakening canonical-EJSON validation.

Verify the dedicated names/port are unused. Do not remove an existing container/network by name. Run:

```powershell
$repo = (Get-Location).ProviderPath
$networkId = docker network create --label reps.audit=a7-executor `
  -o com.docker.network.bridge.enable_ip_masquerade=false `
  -o com.docker.network.bridge.enable_icc=false reps-a7-executor-tests
if ($LASTEXITCODE -ne 0) { throw 'Network creation failed' }
$containerId = docker run -d --pull=never --name reps-a7-executor-mongo `
  --label reps.audit=a7-executor --network reps-a7-executor-tests --read-only `
  --tmpfs /data/db:rw,size=512m,uid=999,gid=999 `
  --tmpfs /data/configdb:rw,size=16m,uid=999,gid=999 --tmpfs /tmp:rw,size=64m `
  --user 999:999 --cap-drop ALL --security-opt no-new-privileges --memory 1g --cpus 2 `
  -p 127.0.0.1:27029:27029 --env HOME=/tmp `
  --mount "type=bind,source=$repo\scripts\tenancy,target=/audit/scripts/tenancy,readonly" `
  --mount "type=bind,source=$repo\docs\tenancy,target=/audit/docs/tenancy,readonly" `
  ad6a13630a36 mongod --replSet repsa7exec --bind_ip_all --port 27029
if ($LASTEXITCODE -ne 0) { throw 'Container creation failed; inspect only owned resources' }
# Wait for ping before initiating; do not substitute another database/host on failure.
$ready = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
  docker exec $containerId mongosh --port 27029 --quiet --eval 'db.runCommand({ping:1})' *> $null
  if ($LASTEXITCODE -eq 0) { $ready = $true; break }
  Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'Disposable MongoDB did not become ready' }
docker exec $containerId mongosh --port 27029 --quiet --eval `
  'rs.initiate({_id:"repsa7exec",members:[{_id:0,host:"127.0.0.1:27029"}]})'
if ($LASTEXITCODE -ne 0) { throw 'Replica-set initialization failed' }
# Wait for primary election, then run the synthetic suite (the driver has a bounded selection timeout).
Start-Sleep -Seconds 3
docker exec -e REPS_LOCAL_EXECUTOR_TOOL_DIR=/audit/scripts/tenancy $containerId `
  mongosh --nodb --norc --quiet --file /audit/scripts/tenancy/executor-mongo.test.mongosh.js
if ($LASTEXITCODE -ne 0) { throw 'Rehearsal failed; inspect sanitized PASS/FAIL output' }
docker exec $containerId cat /tmp/a7-executor-results.json
node --test scripts/tenancy/*.test.cjs
```

The harness seeds random disposable databases, generates **synthetic-only** reviewed v2 artifacts and ephemeral local approvals, runs actual executor/CLI calls, and drops each completed scenario's database. Two same-name schools, 33 application documents, guidance/roster assignments, immutable author snapshots, assignment work, notifications, receipts, grants, billing, duplicates/orphans/conflicting owners are exercised. Twelve School-A root additions are approved; 18 unresolved/other-school records stay quarantined. No provider or application service is started. Artifact/journal paths are private container tmpfs; no keys appear in Docker/child arguments or Git. Interrupted test setup may leave synthetic databases until container disposal.

For a manual **already approved local fixture** with externally held keys/artifacts, the entry point is:

```powershell
.\scripts\tenancy\Invoke-LocalTenantMigration.ps1 `
  -Database '<marked-disposable-database>' -SnapshotPath '<private>\snapshot.json' `
  -ManifestPath '<private>\manifest.json' -PlanPath '<private>\plan.json' `
  -ApprovalPath '<private>\approval.json' -ReportPath '<private>\new-report.ndjson'
# Add -Mode Execute only with a separately signed execute-root-ownership approval.
# Select -Atomicity PerRecord only if the approval explicitly authorizes partial commits.
```

The harness is not an approval issuer for arbitrary data. There is no deployed approval command. Keep the normal application dev database and configuration out of these tools.

Before cleanup, verify saved IDs, label and network membership. Only then stop/remove those exact task-owned resources; leave unrelated containers/worktrees/backups intact:

```powershell
$identity = docker inspect --format '{{.Id}}|{{index .Config.Labels "reps.audit"}}' $containerId
if ($LASTEXITCODE -ne 0 -or $identity -ne ($containerId + '|a7-executor')) { throw 'Ownership mismatch' }
$members = docker network inspect --format '{{range .Containers}}{{.Name}} {{end}}' $networkId
if ($LASTEXITCODE -ne 0 -or $members.Trim() -ne 'reps-a7-executor-mongo') { throw 'Unexpected network members' }
docker stop --time 5 $containerId
if ($LASTEXITCODE -ne 0) { throw 'Stop failed' }
docker rm -v $containerId
if ($LASTEXITCODE -ne 0) { throw 'Container removal failed' }
docker network rm $networkId
```

## Remaining deployed/cutover blockers and rollback

This release cannot connect to deployed data. Future work still needs separately approved protected-data inventory with a privacy-compatible shell/read-only principal; real reviewed pilot/quarantine mappings; private Java/BSON currency replay fingerprints and complete historical namespace evidence; safe embedded/grant ownership adoption or an approved immutable sidecar decision; a separately reviewed deployed executor/authorization/privilege/backup model; full canonical route/worker acceptance and unified activation. HMAC source evidence is not the runtime currency manifest's `recordSha256`.

Full-document CAS includes protected values in Mongo command bodies. **Do not promote this local tool to real data:** any future design needs explicit source-read approval and server query/audit-log redaction/privacy review, plus stronger operational fencing. No live uniqueness, TTL/index topology, backup or recovery evidence was inspected here.

Before any future migration, verify recovery point/restore, stop all old writers/workers/OAuth callbacks and drain currency transactions; pin exact builds/manifests. Maintain maintenance mode on any conflict or partial completion. Do not remove successful ownership fields, rewrite protected data or automatically restore a stale backup over new activity. Keep canonical runtime inactive until every data, supporting-service and owner activation gate passes. No deployed migration, merge, deployment, email or payment call is authorized or performed by this rehearsal.
