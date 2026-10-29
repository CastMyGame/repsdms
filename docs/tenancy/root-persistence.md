> Phase 4C update: see [real workflow integration](phase4c-integration.md), [historical snapshot policy](historical-access.md), and [route/worker matrix](phase4c-coverage.md). Earlier phase descriptions below remain historical except where explicitly superseded. No production activation is authorized.

# A7 Phase 4B-1: root persistence and isolated domain integration

Baseline: `origin/dev` 0d05c50, including merged PR #180. The 144-route/alias inventory in [core-endpoints.md](core-endpoints.md) remains the route inventory; this document records the narrower implementation boundary. No deployed data was inspected. No backfill, provider call, frontend change, currency receipt bridge, Gmail grant migration or Stripe cutover is included.

## Activation boundary

Normal application behavior retains its existing authorization path. No names authorize anything in the new canonical path. `reps.tenancy.id-enforcement-enabled` still accepts only false/default and fails startup otherwise.

The real-service branch requires **both** `reps.tenancy.core-preparation-enabled=true` and `reps.tenancy.domain-preparation-enabled=true`, plus the explicit active profile `tenant-isolated-verification`. The domain bean is absent by default. Enabling the domain flag outside that profile fails startup; enabling it without the core adapter also fails dependency creation. These are isolated verification controls, not deployment instructions. Do not add them to App Engine, dev properties or normal launchers. Existing email flags and defaults are unchanged.

Root ownership preservation applies without either flag. This does not enforce tenant access on existing production routes or make legacy name authorization safe for same-name schools. A7 is not closed by this PR.

## Persistence writer inventory and protection

All paths below are relative to `src/main/java/com/reps/demogcloud`. `TenantRootRepositoryImpl` overrides save/saveAll/delete(entity) for six repositories and delegates to `TenantRootSave`.

| Root / writers | Protection now | Still required before cutover |
| --- | --- | --- |
| Punishments: PunishRepository; punishment creation/update/closure/query, PunishmentUtils, EmailReferralService | Stored-only tenantSchoolId; repository saves use owner and revision CAS, targeted mapped-field updates; existing insert semantics retained | Canonical creation/closure/maintenance predicates, recipient/account joins and currency dependencies |
| OfficeReferrals: OfficeReferralRepository; OfficeReferralService | Same root save protection; no office assignment is introduced | Canonical case operations and notification ownership; office referrals remain principal-managed without student assignment |
| Guidance: GuidanceRepository; GuidanceService | Same root protection; actual isolated create/read/notes/status/follow-up/archive paths use canonical context | Resource-email command deliberately returns 503 in isolated mode before mutation or provider access |
| TrackedBehaviorEvents: TrackedBehaviorEventRepository; TrackedBehaviorServiceImpl | New nullable persistenceRevision; owner/revision CAS; isolated actual writes validate the batch then use a Mongo transaction | Retry/unknown-commit policy and any future idempotency contract must be reviewed before activation |
| assignment_templates and assignment_template_bindings: repositories; AssignmentService | New nullable persistenceRevision; immutable ownership; owned templates cannot be hard-deleted through legacy delete(entity) | Canonical resolver/binding predicates, explicit global-default handling, lifecycle/reporting joins |
| RequiredNotificationService | Reminder direct Mongo save now uses protected helper; legacy case deletion requires absent ownership and matching revision | Dispatcher/claim/retry and recipient/account canonical authorization remain disconnected |
| AssignmentLifecycleService | Existing targeted revision updates retain top-level ownership; AssignmentWork copies now preserve hidden template ownership/revision in current and submission snapshots | Existing production resolver/work/review routes remain on old path; canonical query/write predicates and snapshot provenance cutover remain required |
| Employee/Student/UserModel | Prior protected save fragments retained; isolated roster CAS and student notes/time writes include tenant predicates | Remaining CRUD, spotter and profile mutations, login/setup canonical enforcement and reporting |
| CurrencyTransactions, OAuth token store, Stripe | Unchanged | Explicit Phase 4B-2 dependencies below |

Existing targeted updates were inspected: fixed status/notes/assignment/notification operators do not set or unset root ownership. Existing root creation via Mongo insert cannot replace an existing ID. No raw replaceOne/findAndReplace, bulkWrite/bulkOps, replacement pipeline or arbitrary ownership update was found. A source regression guard flags reintroduced direct Mongo save and replacement/bulk writers. It is not a global driver interceptor: inherited deleteById/deleteAll, a future raw writer, external maintenance and old binaries must not be treated as protected. No current authorized root caller uses those destructive alternatives.

`TenantRootSave` reads only owner/version metadata, preserves the stored owner even when a stale object omits it, rejects conflicting or malformed ownership and verifies that a bound School exists. It cannot ordinarily assign an owner to an existing unbound record. The update predicate includes exact stored ownership and revision; an ownership/revision race fails with a conflict. It preserves unknown top-level BSON extensions. Nested mapped objects retain ordinary whole-field semantics; this is not a deep merge facility. SaveAll is not independently atomic; transaction-requiring services must supply the transaction.

Legacy records without ownership remain unbound and usable in the inactive preparation state. The first normal save of a legacy template/binding/event without a persistenceRevision conditionally initializes revision 0. No startup scan, index build, owner assignment or database migration occurs. Cases retain their existing revision and IDs. No existing currency value, receipt key/hash, ciphertext/AAD, provider state or Stripe identifier is regenerated.

Hidden owner metadata is ignored by JSON request binding and response serialization. AssignmentWork.copy explicitly carries trusted metadata through server-side snapshot copies rather than losing it through JSON cloning. Bound cases reject snapshots from a different tenant; a verified global system default remains allowed. Existing unbound cases/snapshots are not rewritten. Reviewed future backfill must bind case and school-owned snapshot metadata consistently; otherwise bound saves fail closed. Inventory fingerprints now include persistenceRevision, so regenerate inventories/plans after a revision-changing save.

## Actual domain integration versus disconnected adapters

`CanonicalDomainAccess` uses persisted CanonicalAccountContext, exact tenant queries, and CanonicalCoreWorkflows for independent roster/caseload/role checks. Client school names/IDs never grant authority. Guidance/staff/student identity is obtained from persisted authentication. Missing membership, nonexistent school, foreign target, revoked relationship, disabled actor and archived student fail closed.

| Actual service path exercised in isolated mode | Scope |
| --- | --- |
| GuidanceService creation/list/notes/closure | Canonical student/case/counselor checks; creation owner derived server-side; linked punishment must match; assigned guidance/admin mutation rules retained |
| TrackedBehaviorServiceImpl write/history/totals | All requested students validated before writes, tenant-scoped reads; replica-set/session capability check; SNAPSHOT/MAJORITY transaction; injected second-write failure rolls back |
| EmployeeClassService.change | Canonical teacher/student relationship and tenant predicate with existing roster revision CAS |
| StudentAdminService notes/time | Canonical student/actor and tenant-scoped conditional update; note author trusted; time-bank CAS retained |

Student bulk profile mutation and guidance resource email intentionally return 503 in isolated mode. Ordinary punishment/office creation and closure, special case creation/currency, spotter mutations, assignment resolution/work/review/reporting, broad DTO/report queries, background notification processing and remaining account/billing flows are **not canonically integrated**. The detached Phase 4A methods still do not prove those routes are isolated. Do not activate individual production routes while their related services remain incompatible.

## Verification actually executed

Java 17, existing offline Maven cache, MongoDB 6 disposable loopback replica set `a7rs`. Container used a separate network, tmpfs data, no shared credentials/volumes and only 127.0.0.1:27028. Tests generate and drop their own random databases. No live data, Google, SMTP or Secret Manager calls.

- Selected backend/security/workflow/email/authentication/currency/Stripe/A7 regressions: **1,425 passed, 0 failed, 0 skipped**.
- Actual Mongo executions within that total: root/domain security 19, currency 24, account membership 9, profile writer 7, metadata 6. These are not additional tests on top of the total.
- Offline Node tooling: **26 passed**, no skips.
- New root/domain suite: replacement-save owner retention across six repositories, stale/forged saves, ownership race, legacy revision compatibility, assignment/submission snapshot metadata, owned-template deletion refusal, actual guidance paths, tracked rollback, roster conflict/revocation, student note balance preservation, disabled/archived/unmapped denials and no provider interactions.
- Security tests use the actual SecurityConfig/JWT filter chain with test-only probe controllers calling actual domain services and real Mongo repositories. They do not exercise production controllers for every one of the 144 routes. Existing production security regressions ran separately. Detached adapter tests are additional preparation evidence only.
- No frontend changes; frontend/browser tests not run. No deployed migration, mixed-version rollout or live provider verification performed.

Repeat focused tests only against a disposable, loopback replica set, never an existing school database:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:REPS_TENANCY_TEST_MONGODB_URI = 'mongodb://127.0.0.1:27028/?replicaSet=a7rs&directConnection=true&serverSelectionTimeoutMS=5000'
$env:REPS_CURRENCY_TEST_MONGODB_URI = $env:REPS_TENANCY_TEST_MONGODB_URI
try {
  mvn -o -B '-Dtest=CanonicalDomain*Test,TenantRootBoundaryTest,TenantOwnershipWriterMongoIntegrationTest,CanonicalAccountMongoIntegrationTest,TenantMetadataMongoIntegrationTest,CurrencyMongoIntegrationTest' test
  if ($LASTEXITCODE -ne 0) { throw 'Mongo/security regressions failed' }
  node --test scripts/tenancy/tenant-audit.test.cjs
  if ($LASTEXITCODE -ne 0) { throw 'Offline tooling regressions failed' }
} finally {
  Remove-Item Env:REPS_TENANCY_TEST_MONGODB_URI -ErrorAction SilentlyContinue
  Remove-Item Env:REPS_CURRENCY_TEST_MONGODB_URI -ErrorAction SilentlyContinue
}
```

Use the existing Mockito agent option if required by the local runtime. Tests skip optional Mongo classes when no isolated URI is supplied; a skipped run is not persistence verification. The actual recorded run supplied both URIs and had zero skips.

## Deployment, cutover and rollback

1. Review/merge this preparation with both preparation flags absent and the ID-enforcement guard false. Do not backfill while old binaries or external replacement writers remain active.
2. Retain the existing approved inventory/manifest process. No new live inspection or backfill is authorized. Review pilot ownership, snapshot provenance, account/profile agreement and all related roots before any migration.
3. Complete Phase 4B-2 and remaining route/worker integrations; repeat actual production-controller security and Mongo tests before replacing the activation guard.
4. Future cutover requires quiescence/draining all old instances/workers, verified backups, reviewed conditional ownership updates, fresh fingerprints/counts and coordinated activation of the complete boundary. Do not roll out per-route mixed enforcement.
5. Before backfill, rollback to the previous preparation release is possible for legacy unbound data. Once owners/revisions/snapshots have been adopted, do not roll back to a binary that can replace or drop them. Disable isolated flags, stop writers and use a reviewed forward fix or restore plan; never remove ownership to make old code work.

## Proposed Phase 4B-2 scope

Keep distinct reviewed changes: versioned currency receipt compatibility/replay without changing historical action IDs or hashes; tenant-qualified currency reads/transaction predicates; account-owned Gmail grant lookup/refresh/revocation with unchanged ciphertext/AAD; canonical notification capture, participants, claims and recovery; Stripe registration/account/subscription joins. Then finish case/office/assignment/spotter/reporting route and worker integration using these dependencies. Verify transaction retry/unknown-commit behavior and relation revocation timing, not just individual helper checks. Authentication/onboarding canonical activation and the approved mapping executor/cutover remain separate gates. No automatic reconciliation or ownership inference is proposed.
