# A7 School ID preparation

Phase 4A adds a **disabled canonical core-workflow preparation adapter**, without changing production routes or adopting ownership fields on case/template models. See [core-workflows.md](core-workflows.md) and [core-endpoints.md](core-endpoints.md) for the prepared operations, endpoint/caller inventory, execution limits and Phase 4B dependencies.

Phase 3 prepares persisted account membership and an **unwired canonical account context**. See [account-membership.md](account-membership.md) for the current account writer contract, real MongoDB verification, authentication boundary and remaining cutover work. No existing route, login, worker, JWT claim or frontend presentation switches to ID-only authorization.

Phase 2 adds **stored-only employee/student ownership fields and profile save protection**. Read [writer-protection.md](writer-protection.md) and [writer-inventory.md](writer-inventory.md) for the current boundary; the Phase 1 description below records the original preparation design. All other mapped fields, ID-only authorization and migration remain deferred. `tenantSchoolId` remains the canonical field name to avoid overloading existing ambiguous `schoolId` fields.

## Original Phase 1 reference (PR #177)

Base: backend dev 75d5f09 (school/catalog restrictions merged), frontend dev c302615 (approved onboarding merged).

## Scope and deployment boundary

This PR does NOT close A7 or migrate production authorization. It introduces a detached canonical-ownership contract, schemas, read-only inventory, offline planning/rehearsal, synthetic fixtures/tests and a guard against premature activation. There is no database writer/backfill executor and no new application API.

The canonical field will be **tenantSchoolId**, exactly equal to the existing School._id represented by School.schoolIdNumber. It is a new, server-owned field; existing school/schoolName/schoolId fields are not reinterpreted. No UUID-only restriction or case/whitespace folding of IDs. One account still belongs to one school.

**Why mapped fields are deferred:** adding fields to live Mongo-mapped entities or request DTOs before every writer is protected can erase ownership on full/stale saves or change serialized currency hashes. Therefore ownership-fields.json prepares the additive field definitions and CanonicalOwnership validates detached stored-owner values; this PR deliberately does not attach fields to existing entities, DTOs, receipts or ciphertext. That wiring belongs to the next reviewed PRs. It is safe to deploy this preparatory code only before backfill, using existing defaults. Do not deploy old/preparatory binaries after cutover.

`reps.tenancy.id-enforcement-enabled` defaults to literal `false`. Any other value (including `true` or malformed input) aborts startup without echoing the value. It is a **premature-activation guard**, not an operational feature switch. There is no name-based fallback in the new validator. Existing name-based production behavior remains until the complete ID-only implementation/cutover; no partial ID/name authorization mode is added.

Do not set that property true in this release. A future release must replace the guard only after all writers/readers, reviewed ownership coverage and cutover acceptance are ready. No application, email or production feature defaults have changed.

## Files

- ownership-fields.json: detached field/collection contract and global/inherited ownership exceptions.
- manifest.schema.json: sanitized reviewed mapping format; runtime validation is also performed by audit-core.cjs.
- scripts/tenancy/Invoke-TenantInventory.ps1 + inventory.mongosh.js: separately approved, read-only inventory.
- scripts/tenancy/audit-core.cjs + plan.cjs: offline summary/planning/conditional rehearsal; no Mongo driver/write capability.
- scripts/tenancy/synthetic-fixture.cjs + tenant-audit.test.cjs: two identical-name schools, privacy, conflicts, provider simulation and offline tests.
- cutover-and-compatibility.md: writer/currency/Gmail/Stripe/assignment/notification prerequisites, rollout and rollback.

## Safe verification now (no DB or network)

Use Java 17 and the existing project dependencies. Node tests use only Node built-ins and need no npm install:

```powershell
node --test scripts/tenancy/tenant-audit.test.cjs
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -o -B '-Dtest=CanonicalOwnershipTest,TenantPreparationSecurity*Test,TenantMetadataMongoIntegrationTest' test
```

Use the project's existing Mockito agent configuration where required. The TenantPreparationSecurity tests use the real production SecurityConfig chain and an explicitly test-only controller/store exercising the unwired ownership validator. They do not prove that production endpoints have migrated. Existing workflow/security suites are regression evidence only. No test-only endpoint is packaged in main sources.

Real Mongo BSON tests are opt-in. They require a separately running disposable loopback MongoDB with no deployed credentials; the test creates and drops only its generated `reps_a7_test_<random>` database. No existing database may be named in the URI:

```powershell
$env:REPS_TENANCY_TEST_MONGODB_URI = 'mongodb://127.0.0.1:27017/'
mvn -o -B '-Dtest=TenantMetadataMongoIntegrationTest' test
Remove-Item Env:REPS_TENANCY_TEST_MONGODB_URI
```

No server/container is started by these commands or this PR. Currency integration tests separately require their existing isolated replica-set configuration. Missing opt-in URIs mean skipped tests, never use of the application's database.

## Read-only inventory: FUTURE execution only after separate authorization

No dev/production inspection or backfill is authorized by this PR. Do not run the commands in this section until the owner separately approves the target, read-only principal and evidence location.

Prerequisites:

1. Install/approve MongoDB Shell (`mongosh`) separately if absent; see https://www.mongodb.com/docs/mongodb-shell/install/. No dependencies are installed by the tool.
2. Use an authenticated read-only MongoDB principal scoped to the approved database. The exporter inspects only the current connection's privilege metadata and refuses unauthenticated, write-capable or unrecognized privileges before collection reads. It never grants IAM/DB roles or discovers application/cloud credentials.
3. Approve one exact URI host (DNS/IPv4, optional port), database and source label. Single-host mongodb:// and mongodb+srv:// are supported; multi-host/IPv6 URI forms fail safely. String and BSON ObjectId identifiers are supported; unsupported or colliding typed ID representations abort for review rather than being coerced. The output binds a HMAC target tag to protocol/host/database plus a separate audit key tag and inventory digest.
4. Use a private existing directory outside every repository, e.g. `$env:LOCALAPPDATA\REPS\tenant-audit`, protected for the operator's account. Redacted/HMAC evidence is still private metadata, not a public report or authorization token. The tool refuses output inside its repository, existing output files and parent symlinks resolving into the repository.
5. Before supplying any URI or audit key, explicitly configure mongosh privacy in a no-database session. These commands change LOCAL shell preferences only; the inventory tool itself never changes them. Review this prerequisite separately:

```powershell
mongosh --norc --quiet --nodb --eval 'config.set("disableLogging", true); config.set("enableTelemetry", false); config.set("disableSchemaSampling", true); config.set("historyLength", 0); config.set("updateURL", ""); config.set("snippetAutoload", false); config.set("browser", false);'
```

The exporter requires those exact settings before connecting. MongoDB documents logging and startup settings at https://www.mongodb.com/docs/mongodb-shell/logs/disable/ and https://www.mongodb.com/docs/mongodb-shell/reference/configure-shell-settings/. Use a clean approved shell profile and keep transcripts/debug logging off; MONGODB_LOG_* driver logging environment variables are refused. The first privacy-setup shell may perform its existing update check; the subsequent audit run requires updateURL empty.

Prepare a **separate audit HMAC key**, never the Gmail/JWT key. The following future setup preserves/reuses an existing DPAPI copy and never prints plaintext or saves it in Git:

```powershell
Add-Type -AssemblyName System.Security
$auditDirectory = Join-Path $env:LOCALAPPDATA 'REPS\tenant-audit'
New-Item -ItemType Directory -Force -Path $auditDirectory | Out-Null
$auditKeyPath = Join-Path $auditDirectory 'inventory-hmac.dpapi'
if (-not (Test-Path -LiteralPath $auditKeyPath)) {
    $auditBytes = New-Object byte[] 32
    $auditRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $auditRandom.GetBytes($auditBytes) } finally { $auditRandom.Dispose() }
    $auditEncrypted = [Security.Cryptography.ProtectedData]::Protect($auditBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $auditStream = [IO.File]::Open($auditKeyPath, [IO.FileMode]::CreateNew)
    try { $auditStream.Write($auditEncrypted, 0, $auditEncrypted.Length) } finally { $auditStream.Dispose(); [Array]::Clear($auditBytes, 0, $auditBytes.Length) }
}
$auditBytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($auditKeyPath), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
if ($auditBytes.Length -ne 32) { throw 'Invalid audit HMAC key' }
$env:REPS_TENANT_AUDIT_KEY = [Convert]::ToBase64String($auditBytes)
[Array]::Clear($auditBytes, 0, $auditBytes.Length)
$auditUri = Read-Host 'Approved read-only MongoDB URI' -AsSecureString
$auditPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($auditUri)
try { $env:REPS_TENANT_AUDIT_URI = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($auditPointer) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($auditPointer) }
try {
    .\scripts\tenancy\Invoke-TenantInventory.ps1 `
      -Database 'REPLACE_APPROVED_DATABASE' `
      -SourceId 'REPLACE_APPROVED_ENVIRONMENT_LABEL' `
      -ExpectedHost 'REPLACE_APPROVED_URI_HOST' `
      -OutputPath (Join-Path $auditDirectory 'inventory-001.json') `
      -ReadOnlyInventoryApproved
} finally {
    Remove-Item Env:REPS_TENANT_AUDIT_URI -ErrorAction SilentlyContinue
    Remove-Item Env:REPS_TENANT_AUDIT_KEY -ErrorAction SilentlyContinue
    $auditUri = $null
}
```

Replace placeholders only after approval. For Atlas use the approved cluster's URI hostname as ExpectedHost; for an approved local target use the exact host:port. Do not put a URI/password in arguments, transcripts, tickets or shell history. Losing/rotating the audit HMAC key invalidates record-key matching; preserve its DPAPI file for the approved inventory/review cycle. This key cannot decrypt Gmail or sign JWTs.

The exporter only uses connectionStatus and projected find operations. It never reads passwords, reset tickets, OAuth ciphertext/tokens, student names, guardian emails, message bodies, answers or full documents. Emails and record IDs needed for graph checks are HMAC-redacted in output; school IDs remain canonical. Outputs include duplicate-name/identity groups, invalid owners, unresolved school names, orphaned/ambiguous references, structural template conflicts and collection counts. Names produce candidates for review ONLY; even one candidate never authorizes a mapping.

Limits: scans are non-transactional and bounded at 10,000 schools / 100,000 documents per scoped collection / 200,000 total scoped documents with 30-second cursor limits. Oversize, insufficient privileges, malformed data or provider errors abort without a partial output. No completeness is claimed if a scan fails. Review on a quiescent source and recheck fresh fingerprints/counts before any future write; an inventory is not an atomic backup. External Stripe ownership, existing indexes and historical provenance still require separate approved inspection; this tool does not query Stripe/Google/Secret Manager.

## Manifest and offline plan

Copy sourceId, targetTag, keyTag and inventoryDigest from the private inventory. Set an explicit `schoolIds` pilot allowlist and reviewer alias. Each entry contains only a HMAC recordKey, its beforeDigest, canonical tenantSchoolId and a non-personal evidence kind/reference. Use manifest.schema.json. No emails, passwords, names, URI values, arbitrary filters or update operators are permitted in entries. Evidence must be reviewed-onboarding, reviewed-historical-record or verified-existing-id; the latter must actually match a stored explicit ID. Name/spotter evidence is rejected.

A reviewer must establish historical case ownership from reliable original evidence. Current student membership alone is not sufficient. Conflicting current references are blocked for separately reviewed remediation, not silently transferred. Ambiguous/orphaned relationships in a proposed mapping must be resolved; unsubscribed/retired legacy records remain preserved and unmapped. Global system templates cannot be assigned a school by this planner.

```powershell
node scripts/tenancy/plan.cjs summary "$auditDirectory\inventory-001.json"
# After completing a separately reviewed private manifest:
node scripts/tenancy/plan.cjs plan "$auditDirectory\inventory-001.json" "$auditDirectory\manifest-reviewed.json" "$auditDirectory\plan-001.json"
```

The plan has `executable:false`. It reports expected before/after counts (unchanged document totals), field-update counts, already-bound entries and metadata-only update descriptors. It is NOT a runnable Mongo update. No execute/backfill command exists. The offline API's rehearse function demonstrates conditional/idempotent behavior with in-memory copies; it cannot connect to MongoDB.

A future separately approved executor must privately resolve each HMAC recordKey back to the exact typed _id using the same audit key, recheck target/reviewer approval/known school IDs/fingerprints/references/counts, and use atomic conditional `$set: {tenantSchoolId: ...}` only. Recheck actual approved source metadata/revisions and absent ownership in the write predicate, not just a client-side digest. Skip an already-correct owner only when all remaining fingerprints/references still match. Reject a different owner, drift or partial uncertainty; never overwrite it. No inserts/deletes, balance reconciliation, ID regeneration or history/cipher rewrites. No such executor is included here.

## Verification recorded for this preparation

Final results are recorded in the PR description. Offline exporter tests use a fake read-only Mongo interface, not a live server. PowerShell syntax is parsed but actual mongosh execution is pending because the CLI is not installed. The Node planner tests use synthetic metadata only. No frontend files are changed; frontend tests are not rerun for this backend-only preparation.
