> Phase 5B migration contract: [v2 preservation and review rules](migration-contracts.md) supersede generic v1 planning/adoption instructions. No receipt, embedded history or cipher writes are proposed; no executor or activation is authorized.

> Phase 4C update: see [real workflow integration](phase4c-integration.md), [historical snapshot policy](historical-access.md), and [route/worker matrix](phase4c-coverage.md). Earlier phase descriptions below remain historical except where explicitly superseded. No production activation is authorized.

# A7 Phase 4B-2: versioned currency replay preparation

Base: dev 894b6cc (merged #181). This is the approved Option 1 bridge, not a currency migration or route activation. Historical currency_operations documents, payload hashes, IDs and wallet balances are never rewritten by the bridge. CurrencyTransactions and CurrencyService remain unchanged and disconnected from this new engine.

## Inactive boundary

CanonicalCurrencyTransactions is absent unless reps.tenancy.currency-preparation-enabled=true. Its startup and execute method both require the explicit tenant-isolated-verification active profile. Production ID enforcement still accepts false/default only. There is no deployed activation switch, fallback to name authority, automatic manifest loader or backfill executor in this PR. Do not configure the preparation flag in App Engine or normal development launchers.

The engine verifies real persisted CanonicalAccountContext membership. Students are denied; staff-budget reset additionally requires ADMIN. Future domain callers must independently validate the entire student/roster/caseload batch and apply tenant-qualified wallet CAS inside the supplied transaction callback. The bridge is not a substitute for those domain checks. Existing production CurrencyService continues using its legacy engine until its complete coordinated conversion.

## Receipt compatibility

- V1 IDs remain SHA-256(lowercase actor email + ':' + lowercase historical school name + ':' + operation kind + ':' + normalized action UUID). No historical action UUID can be recovered from that hash. Current membership, current school name or an empty lookup under just today's name cannot prove an action is new.
- Reviewed namespaces may locate candidate receipts; they never prove ownership. Every found historical receipt additionally requires reviewed exact receipt ID, canonical account/tenant and full-record fingerprint evidence. Missing evidence, changed fingerprints, overlapping namespaces, multiple candidate receipts, or both a v1 and v2 receipt block the action.
- V2 ID is 'v2-' plus SHA-256 of JSON array ["reps-currency/v2", exact tenant ID, exact account ID, kind, normalized action UUID]. Stable IDs, not display names, define this namespace. Actor profile ID is also persisted/verified. Supported kinds are AWARD, REDEEM and RESET_STAFF_BUDGETS.
- The v1 ObjectMapper payload serialization/hash algorithm is retained for comparisons. Do not change currency request serialization without separately versioning/reviewing historical replay compatibility.
- Candidate lookup, review validation, unique v2 receipt insert, callback wallet/recognition/notification-intent writes and final COMMITTED receipt update share one SNAPSHOT/MAJORITY Mongo transaction. A callback failure rolls everything back. Duplicate/transient retries retain the action ID; unknown commit outcomes perform lookup only and never automatically repeat the body.
- Historical replay returns the original receipt without creating an alias, updating its metadata, or running the callback. V2 replay checks tenant/account/profile/kind/state/hash before returning. The original engine remains available only on the existing production path, not as a fallback from failed canonical verification.

## Private historical review manifest

Future reviewed manifests live in a separate currency_replay_manifests collection. This PR provides a reader only. Its exact _id and SHA-256 fingerprint must be pinned through reps.tenancy.currency-replay-manifest-id and reps.tenancy.currency-replay-manifest-sha256 in the isolated verification environment. A request cannot select the manifest or assert ownership. There is no application manifest-management API.

Shape (synthetic values only; placeholders are not valid production digests):

```json
{
  "_id": "reviewed-cutover-generation",
  "schema": "reps-currency-replay/v1",
  "historyReviewed": true,
  "legacyWritersDrained": true,
  "reviewReference": "private-review-reference",
  "actors": [{
    "accountId": "existing-account-id",
    "profileId": "existing-profile-id",
    "tenantSchoolId": "existing-school-id",
    "historyComplete": true,
    "evidenceReference": "independent-historical-evidence",
    "legacyNamespaces": [{"actor": "staff@example.test", "school": "historical display name"}]
  }],
  "receipts": [{
    "receiptId": "64-lowercase-hex-v1-receipt-id",
    "accountId": "existing-account-id",
    "tenantSchoolId": "existing-school-id",
    "recordSha256": "64-lowercase-hex-full-record-fingerprint"
  }]
}
```

Namespaces must be the exact already-normalized historical components, with no guessing from current names. Review all known historical account/email/school namespaces. Empty namespaces are permitted only when independent review establishes that this account has no legacy currency history; historyComplete is an explicit reviewed attestation, not something the application discovers. Reviewers must not mark incomplete records complete merely to unblock a payment/award. Shared/ambiguous namespaces are rejected even if the schools have identical names. A manifest cannot assign one account or receipt to multiple owners.

Fingerprints use CurrencyReplayManifest.fingerprint: recursively sort document keys, preserve array order and BSON values, serialize with Document.toJson, then SHA-256 UTF-8 bytes. Full receipt fingerprints include IDs, amounts, case references, hashes and timestamps, not a mutable subset. Never mutate a historical receipt to make a fingerprint match. This is a deterministic integrity check of a separately trusted review artifact, not a digital signature or a tool that can prove historical provenance.

The private review contains historical staff emails and must be access-controlled, excluded from Git/logs/PRs, and handled under the existing approved inventory process. Only sanitized counts/review references belong in shared reports. No actual manifest, inventory, account record or deployed database was read/imported here. Metadata pin changes require a new reviewed generation and drained writers; do not edit an in-use manifest in place.

## Ambiguous replay resolution

Return 503 with a generic reviewed-resolution message and do not execute the callback. Preserve the incoming action ID and payload privately; do not create a fresh action ID to bypass the block. Separately authorized maintainers must inspect the original ledger/case evidence, resolve ownership and establish complete historical coverage. If execution cannot be established reliably, keep that action blocked. No automatic award, refund, reconciliation, receipt relabeling or balance correction is authorized. A payload mismatch on a valid receipt returns 409.

## Cutover and rollback prerequisites

1. Finish canonical domain wallet/case/notification integration and production-controller coverage. Review every callback for tenant predicates, role/relationship checks and full-batch validation.
2. Separately approve read-only historical inventory, reviewed pilot mappings, manifest generation and any future write/import executor. Preserve original receipt/BSON evidence, backups and exact payload serialization compatibility.
3. Drain all old and new currency writers/transactions before mapping or manifest changes. The manifest's legacyWritersDrained attestation is an operational prerequisite, not detection of an old process. Concurrent old binaries creating v1 receipts while the bridge creates v2 receipts are unsupported and unsafe; do not run a mixed-writer rollout.
4. Recheck ownership/fingerprints/counts and pin the immutable reviewed manifest. No new receipt may be generated for actors lacking complete reviewed coverage. Rehearse before/after retries and uncertain outcomes on a disposable replica set.
5. Only a later coordinated release may connect the complete service path and replace the global activation guard. No receipt scheme is activated by deploying this preparation.
6. Before adoption, roll back the preparation normally with flags absent. After canonical activity, use a known-good canonical build or maintenance/forward repair; never roll back to the legacy-only engine, remove ownership, regenerate receipts or restore stale balances over new work.

## Actual verification boundary

CanonicalCurrencyMongoSecurityTest uses real SecurityConfig/JWT/account context, real MongoDB transactions and the bridge. A test-only callback performs tenant-qualified wallet debit/credit and synthetic recognition/notification-intent writes. It is **not** end-to-end proof that production CurrencyService, punishment creation or dispatch has migrated. Existing actual CurrencyMongoIntegrationTest verifies the unchanged legacy domain transaction path separately. No provider client runs in either bridge test path.

The suite covers original-engine receipt replay, unchanged historical documents, reviewed versus absent/altered/foreign evidence, identical-name schools, canonical restart/retry, concurrent historical/canonical replay, rollback after debit, insufficient funds, forged receipt ownership, disabled/missing membership and simulated lost/unresolved commit responses. Lost-response faults are injected after a real transaction completes; they are application fault simulations, not network partition tests.

Run with Java 17 and existing Mockito agent configuration, supplying REPS_CURRENCY_TEST_MONGODB_URI for an isolated disposable loopback replica set, with no database name or credentials. Tests generate/drop only their own random database. Command: mvn -o -B '-Dtest=CanonicalCurrencyMongoSecurityTest,CurrencyReplayBoundaryTest,Currency*Test' test. Missing URI skips Mongo tests; skips do not establish persistence verification. No shared database may be used.
