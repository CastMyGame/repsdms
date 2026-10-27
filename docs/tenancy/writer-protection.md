# A7 Phase 2: safe profile writer preparation

Baseline: dev 69ba42c (PR #177 merged). No database was inspected, backfilled or changed. No authentication or tenant authorization changes are activated. The false-only TenantPreparationGuard remains unchanged; never set reps.tenancy.id-enforcement-enabled=true in this release.

## Implemented boundary

Employee and Student gain optional persisted `tenantSchoolId`, exactly as named in the Phase 1 manifest. This represents School._id / schoolIdNumber; it is not the existing school display name. Existing school/schoolName/schoolId fields are untouched. Jackson ignores this new property on input and output; ordinary HTTP requests cannot establish ownership. Mongo mapping can round-trip it. No factory automatically populates it, no name lookup is used, and newly created profiles stay unbound.

Their existing custom repository save/saveAll fragments route through BalanceSafeSave:

1. Convert the entity, then load only the persisted ownership projection by its mapped _id.
2. New profile: reject non-null proposed ownership, remove null metadata, retain the established zero initial currency behavior and insert. Duplicate _id races still fail through Mongo uniqueness.
3. Existing legacy profile: reject an attempted binding and require the owner field to remain absent in the atomic update predicate.
4. Existing owned profile: require a valid exact nonblank String with no surrounding whitespace and a School found by that exact _id (never name). Supplied different ownership is rejected; omitted stale input is allowed to preserve the stored owner. Explicit persisted null, blank, nonexistent or wrong BSON types fail closed for support review.
5. Exclude both tenantSchoolId and currency from every $set/$unset. Require the exact persisted owner (or field absence) in findAndModify. Ownership drift/deletion after the projection returns a generic OptimisticLockingFailureException, handled by the existing HTTP 409 mapper. No upsert/reinsert fallback.

This is preservation and ID existence checking, not tenant authorization: it does not grant access based on the metadata. The future persisted actor context must verify membership agreement and object relationships. Current name-based policy is still present and A7 is not closed. Generic profile saves retain their existing behavior for other mapped fields; this is not a new profile-wide concurrency solution. SaveAll remains sequential, not an all-or-nothing batch. The School existence read is not a cross-document lock; controlled maintenance must not delete/transfer School roots during writes or cutover.

All currently used profile writes were reviewed: repository fragments, explicit new-profile inserts, fixed role/archive updates, roster revision CAS, profile revision CAS, notes/time/spotters and currency $inc. Those targeted updates never touch ownership. Direct MongoTemplate.save/replace of Employee/Student is **unsupported**: it bypasses the fragments and could erase ownership. Do not introduce it. This PR does not promise protection against arbitrary driver/maintenance writes or older incompatible binaries.

## Deferred adoption

| Record | Reason / next dependency |
| --- | --- |
| UserModel/users | Replace-on-save provisioning/auth paths plus targeted setup/activation/billing writes. Need insert-only provisioning, immutable ownership repository semantics, persisted account/profile agreement and setup-session compatibility. |
| Punishments/OfficeReferrals/Guidance | Versioned replacement saves, legacy maintenance, reminders and nested notification state must preserve ownership and revisions atomically; adding a field alone would not protect stale snapshots. |
| assignment_templates/bindings | Replacement saves, legacy conversion and existing ambiguous schoolId need canonical field handling without modifying pinned snapshots or teacher/system scope. |
| TrackedBehaviorEvents | New bulk records need trusted canonical actor/student context; no IDs inferred from names. |
| currency_operations | Receipt identity/payload serialization and historical replay bridge require a coordinated separate change; current receipt model and algorithms are unchanged. |
| Gmail grants | Raw targeted writes preserve unknown metadata, but existing email-keyed upserts cannot establish trusted canonical owner/account binding. Keep ciphertext/key/AAD and grant IDs untouched. |
| registration_intents/Stripe | Existing schoolIdNumber already refers to School._id; its replacement saves and provisioning associations need review before new metadata is adopted. Existing subscriptions/events remain unchanged. |
| schools | Existing _id is canonical and is never replaced by a new ID. Application creation/deletion remains restricted and settings are fixed-field updates. No second tenant ID is necessary on the school root. |

## Currency version compatibility gate

Current receipts are **v1** (no explicit version field). Their _id is SHA-256(lowercase actor + ':' + lowercase historical school label + ':' + kind + ':' + actionId). Payload hashes use the existing request JSON, not Employee/Student serialization. Neither calculation, request DTO, CurrencyOperation, transaction manager nor debit/credit path changes here. Identical action retries still resolve the original receipt and skip work. Adding profile metadata must not create another receipt or notification.

No v2 generation is implemented or enabled. Before it is allowed, a separate PR must implement/test the following compatibility design:

- Treat absent receipt version as v1; future versions must be explicit and unknown versions rejected. Do not rewrite old IDs, hashes, amounts, case IDs, balances or notification obligations.
- Establish reviewed immutable receipt tenant/actor ownership and complete historical replay coverage before creating canonical action keys. A display label may reconstruct an old hash only as historical receipt evidence; it must never authorize a tenant.
- For each retry, resolve a tenant-scoped canonical action alias or a matching owned v1 receipt using the supplied original action ID, historical actor/label and exact original payload digest. Payload equality alone is insufficient evidence. Ambiguous ownership, unavailable historical identity/label or incomplete replay coverage fails closed, never falls through to a new debit.
- Reserve the unique canonical (tenantSchoolId, stable actor account ID, kind, actionId) alias inside the same Mongo transaction as v2 receipt/wallet/case writes. A verified v1 hit binds to its original receipt without running award work. Concurrent v1/v2 workers must be drained at cutover; a read-then-insert compatibility shim alone is unsafe.
- Rehearse action replay before/after profile/receipt metadata, rename, restart, alias races, payload conflicts, unknown versions, transaction rollback/uncertain commit, same-name schools and unmapped receipts. Preserve already-created case/notification identities.

The focused v1 mock test pins existing key/payload behavior and proves repeated hits cannot execute wallet/notification work. The new opt-in real currency test performs an award, adds synthetic profile ownership, retries the same action and checks byte-equivalent receipt BSON, unchanged balances and recognition count. These are v1 compatibility tests; they do not claim a v2 bridge is implemented or verified.

## Compatibility checks

- Profiles: no record ID, school name, account role or balance rewrite. Metadata is optional; explicit invalid persisted metadata causes a conflict instead of erasure.
- Assignments/notifications: neither models, snapshots, history, claim state nor provider dispatch logic change. Existing regressions remain required; no automatic resend.
- Gmail: no token access, account key, AES-GCM AAD or ciphertext change. Future owner binding still requires reviewed account identity; no reconnection/rotation for metadata alone.
- Stripe: no customer/subscription/event/intent identifier or provisioning policy changes. Payment is never school membership authority.
- No schema validation/index/migration is created. Actual deployed BSON/index compatibility and uniqueness remain unverified without separate inspection approval.

## Verification commands and limits

Use Java 17 and existing dependencies/Mockito agent as documented in README.md:

```powershell
mvn -o -B '-Dtest=TenantOwnershipSaveTest,CurrencyLegacyReceiptCompatibilityTest,TenantOwnershipWriterMongoIntegrationTest,CurrencyMongoIntegrationTest' test
node --test scripts/tenancy/tenant-audit.test.cjs
```

The new writer Mongo tests opt in only through REPS_TENANCY_TEST_MONGODB_URI, require credential-free 127.0.0.1 with no database in the URI and own/drop a random reps_a7_writer_test_* database. They exercise actual repository fragments, stale saves, ownership CAS races, forged same-name ownership, null metadata and concurrent profile/$inc writes. Existing currency tests require a separately running isolated replica set. No test launches Boot, providers or cloud configuration. Missing isolated URIs mean skipped, never fallback to application Mongo settings.

Final Phase 2 verification: Java 17 selected backend/security/workflow/email/authentication/roster/assignment/currency/Stripe regressions: 1,251 total, 1,214 passed, 37 skipped, zero failures/errors. This includes 13 new passing profile/legacy-receipt unit tests. Skips: 7 new writer Mongo tests, 6 existing tenant BSON tests, and 24 currency Mongo tests (including the new ownership/retry scenario). Docker's daemon is stopped and neither isolated URI is configured. Node built-in inventory/planner tests: 26 passed. Whitespace checks passed. Frontend tests: Not run (no frontend changes). Real Mongo writer/transaction verification remains pending; production A7 isolation is not established by these tests.

## Deployment and Phase 3 precautions

This preparation can run against existing unbound records without a backfill. Keep ownership authorization disabled and do not populate IDs merely to test this release against dev. Even mapped Employee/Student IDs must not become authorization authority until accounts, cases, workers, templates, currency replay and Stripe associations are converted together.

Do not permit mixed incompatible writers during backfill/cutover. Drain requests, old instances and workers first; complete protected insert/replacement paths across all deferred roots. Separately approve inventory and data operations. Preserve recovery points, counts and all original data. Rollback before any mapping may return to the prior compatible build; after ID cutover use a tenant-aware build/maintenance or reviewed forward correction, never old name authorization or a stale database restore over new activity. See cutover-and-compatibility.md.
