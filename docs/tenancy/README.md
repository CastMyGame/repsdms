# Canonical School ID preparation and offline contracts

Current migration contract: **v2**. Read [migration-contracts.md](migration-contracts.md), [manifest.schema.json](manifest.schema.json), [currency replay](currency-replay.md), [email ownership](email-ownership.md) and [cutover/rollback](cutover-and-compatibility.md). V1 generic plans are rejected. Runtime A7 remains inactive; these tools do not authorize deployed inspection, writes or activation.

## Offline verification

Node uses built-ins only; no dependency installation, MongoDB or provider connection is needed:

```powershell
node --test scripts/tenancy/*.test.cjs
```

Synthetic fixtures contain identical-name schools and deliberate dummy credentials/content solely to verify redaction. Never reuse the public fixture HMAC key for real evidence. No test source contains a real credential.

## Future inventory — requires separate owner approval

The v2 read scope includes protected assignment/notification/history/grant subtrees and full receipt bytes **in memory for HMAC only**. No plaintext passwords/setup tickets or encryption keys are selected; no token is decrypted. Approve that scope explicitly in addition to the exact host/database/read-only principal, private output/key custody and retention. Do not use application/cloud credential discovery.

Use an approved privacy-compatible `mongosh`; the prior cached shell failed privacy preflight, which remains unresolved. Before supplying URI/key, configure and verify all required settings in a separate `--nodb --norc` session: `disableLogging=true`, `enableTelemetry=false`, `disableSchemaSampling=true`, `historyLength=0`, `updateURL=""`, `snippetAutoload=false`, `browser=false`. The tool verifies these without changing them, refuses `MONGODB_LOG_*`, then checks authenticated read-only privileges before collection reads. Unknown/write-capable privileges abort.

Provide the approved URI and a private random 32-byte canonical-Base64 **audit HMAC key** through process environment variables `REPS_TENANT_AUDIT_URI` / `REPS_TENANT_AUDIT_KEY`; do not place values in arguments, history, logs or Git. This is separate from Gmail/JWT keys. Output must be in a private existing directory outside repositories. Never print key/URI values.

Only after both read approvals, using owner-selected nonpersonal placeholders:

```powershell
.\scripts\tenancy\Invoke-TenantInventory.ps1 `
  -Database '<approved-database>' -SourceId '<opaque-source-label>' `
  -ExpectedHost '<approved-host[:port]>' -OutputPath '<private-existing-directory>\inventory-v2.json' `
  -ReadOnlyInventoryApproved -ProtectedFingerprintReadApproved
```

Single-host DNS/IPv4 `mongodb://` and `mongodb+srv://` forms are supported; unsupported hosts/typed IDs fail closed. No database writes, automatic school lookup/adoption, cloud imports, token decryption, migrations or provider calls exist. Projection and full-receipt coverage are specified in `inventory.mongosh.js`; output contains sanitized metadata only, but still stays private. Output is exclusive and never overwrites prior evidence.

## Reviewed manifest and non-executable plan

Review independent historical evidence; never assign owners from names or a student's current school. Complete all required root, embedded and currency-history mappings using the v2 schema. Unknown/ambiguous records stay quarantined. Component review is not runtime adoption or permission to mutate snapshots. Keep all real evidence outside Git.

```powershell
node scripts/tenancy/plan.cjs summary '<private-directory>\inventory-v2.json'
node scripts/tenancy/plan.cjs plan '<private-directory>\inventory-v2.json' '<private-directory>\reviewed-v2.json' '<private-directory>\plan-v2.json'
```

No execute/apply/backfill command exists. Root update descriptors permit only `tenantSchoolId` on nine named collections. Receipts, Gmail grants and registration intents are compatibility-only; protected embedded structures are review-only. Plans report counts, component holds and transitive quarantine and always declare `executable:false`, `activationReady:false`.

Offline `rehearse` uses copies of sanitized inventory, not actual MongoDB records or a migration executor. It cannot generate an award, claim a notification, change a cipher or prove deployed data readiness. Separate approvals and remaining gates are listed in [migration-contracts.md](migration-contracts.md).

## Phase 5C local-only conditional executor

See [local-executor.md](local-executor.md) for fixed disposable-target guards, separate signed authorization, default dry-run, real MongoDB rehearsal, journal/partial-progress semantics and remaining deployed gates. The offline v2 plan itself still grants no execution permission. No deployed executor or canonical activation is enabled.

## Isolated activation verification

[Full application verification and cutover blockers](isolated-activation-verification.md) distinguishes runtime mapping probes from valid-payload security/Mongo acceptance. [The reconciled 172-mapping matrix](activation-route-coverage.csv) includes explicit gaps and intentional global/retired routes. Production enforcement remains inactive; test-only composition is not a deployed activation switch.
