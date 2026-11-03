# Canonical School ID preparation and offline contracts

Current migration contract: **v2**. Read [migration-contracts.md](migration-contracts.md), [manifest.schema.json](manifest.schema.json), [currency replay](currency-replay.md), [email ownership](email-ownership.md) and [cutover/rollback](cutover-and-compatibility.md). V1 generic plans are rejected. Runtime A7 remains inactive; these tools do not authorize deployed inspection, writes or activation.

## Offline verification

Node uses built-ins only; no dependency installation, MongoDB or provider connection is needed:

```powershell
node --test scripts/tenancy/*.test.cjs
```

Synthetic fixtures contain identical-name schools and deliberate dummy credentials/content solely to verify redaction. Never reuse the public fixture HMAC key for real evidence. No test source contains a real credential.

## Phase 5D-1 aggregate inventory

The checked-in target policy permits only `reps_inventory_fixture` at `127.0.0.1:27031`, with direct routing to the single member of replica set `repsinventory`. The URI must match the full policy, including database and options. The MongoDB hello response must identify the expected primary and membership. There is no caller-supplied host approval. The policy has no deployed targets.

The launcher authenticates the dedicated fixture reader and verifies MongoDB's effective privileges from `connectionStatus`. Each approved collection must have exactly one matching resource with only `find`. Missing metadata, wildcard resources, unexpected identities, extra collections, cross-database grants and administrative or write actions cause refusal. Inherited grants are checked through the server's effective privileges. No application or provider starts.

Use the cached MongoDB 6.0.6 image and the official Linux x64 mongosh 2.14.1 archive. The cached image shell lacks required privacy settings. Download the archive from the [mongosh 2.14.1 release](https://github.com/mongodb-js/mongosh/releases/tag/v2.14.1). The rehearsal verifies SHA-256 `8d24940ebe3675959b518fd41ea16b4e2182d6a75c09777db09cfa64e18834a1` before extraction. Run the fixture with Docker networking disabled from the repository root in PowerShell.

```powershell
$inventoryTools = (Resolve-Path -LiteralPath '.\scripts\tenancy').Path
$inventoryShell = (Resolve-Path -LiteralPath '<path to mongosh-2.14.1-linux-x64.tgz>').Path
docker run --pull never --rm --network none --tmpfs /data/db --tmpfs /data/configdb --tmpfs /tmp --mount "type=bind,source=$inventoryTools,target=/tools,readonly" --mount "type=bind,source=$inventoryShell,target=/opt/inventory-mongosh.tgz,readonly" --entrypoint /bin/bash mongo:6.0.6 /tools/inventory-rehearsal.sh
```

The rehearsal creates synthetic data and roles only inside its disposable container. It exercises the actual mongosh entry point, read and index denial, malformed and excessive privileges, atomic publication and interrupted staging. The output and synthetic credentials are discarded with the container. The Node suite additionally spies on connection construction to confirm refusal before connection for disallowed URI forms and host overrides.

The aggregate schema is defined by `inventory-aggregate.cjs`. It has fixed version, consistency and readiness labels. All other leaves are nonnegative integer counts under fixed collection or category keys. It reports collection totals, ownership and relationship gaps, collisions, notification state and incomplete evidence. A quarantine count includes records that still require human review. The separate replay manifest and Stripe event log scans count records without interpreting private evidence. A nontransactional count does not establish cutover readiness.

Publication requires a caller-owned private POSIX directory outside Git. The tool validates the full aggregate shape, stages a mode-0600 file, synchronizes it, then publishes with an exclusive atomic hard link. Recoverable failures remove the staging file. A process kill can leave a private staging file. Remove such remnants and reports after the approved retention interval, including backup copies under the applicable retention policy. Store aggregate output outside synced folders. The launcher declines Windows publication because POSIX mode checks cannot attest its ACL.

A future deployed run needs an independently reviewed environment and cluster identity, exact database and collection scope, dedicated read-only credentials, verified privacy settings, approved retention and a separate owner authorization. Adding a deployed target requires code review and verification of the actual routing mechanism. Private mapping and fingerprint collection require separate approval and a separate restricted artifact. This launcher produces no migration input.

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
