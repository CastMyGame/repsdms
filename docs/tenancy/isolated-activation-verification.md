# A7 isolated canonical activation verification

Baseline: backend `cb2d99844cd0797cb557da41b48e3941c4a86ca6` (merged #188), frontend `bd5eac0db6770fc1aa6c182a702a898edbe547e2` (merged #299). Both remote dev refs fetched on 2026-10-30. No deployed records were inspected.

## Readiness decision

**Isolated full-application verification is supported. Deployed cutover is NOT approved or ready.** This work supplies a test-only composition and stronger deployment rejection, not a production activation switch. The false-only `reps.tenancy.id-enforcement-enabled` guard remains intact. All four preparation properties remain disabled/absent by default.

`IsolatedCanonicalBootstrap` exists only in test sources. It accepts one exact loopback replica-set URI, checks `hello` replica identity/hosts, creates a randomly named synthetic database with a control marker, disables cloud imports and credential discovery, and supplies synthetic JWT/Gmail keys. It installs core/domain/currency/email canonical adapters together. It cannot be included in the normal application JAR through an environment variable. Hosted runtime markers and ordinary dev/prod profiles reject preparation at application startup.

The full application uses its actual component scan, all controllers, JWT/filter chain, repositories, domain services, transaction engine and notification renderer/router. Only external SMTP and translation are mocked. Gmail's actual HTTP clients are intercepted with MockRestServiceServer (unexpected requests fail); Stripe session network methods are intercepted during probes. Explicit scheduler removal is asserted; workers are invoked manually. No HTTP server, browser, live Google login, email provider or billing provider was started/contacted.

## Route reconciliation and evidence levels

[Full matrix](activation-route-coverage.csv) covers **172 expanded method/path mappings across 19 controllers**. All original 144 entries remain; the other 28 were already in the prior 172 inventory. Runtime RequestMappingHandlerMapping agrees exactly with current source. Framework `/error`, implicit HEAD/OPTIONS and Spring-generated login endpoints are not additional application-controller mappings.

Every mapping receives five security/filter/binding probes: anonymous, teacher, student, guidance and administrator. These 860 requests are **not** 860 successful authorization acceptance tests. Most mutations use an intentionally incomplete body; 400/404/500 responses or a missing parameter do not establish object-level authorization. Probes also assert that target school B's student email is not disclosed in non-directory responses. Wildcard maintenance mappings are represented as mappings, not every possible suffix. Dedicated valid-payload tests are the stronger evidence.

Matrix categories are 17 selected-workflow mappings, 48 intentional global/public/retired mappings, and 107 integrated mappings with incomplete acceptance. A selected mapping still needs its full role/state/parameter matrix: assignment save/submit/review are covered, but staff binding/recovery is not. Raw response statuses are retained for review; no malformed-fixture 500 is automatically described as a production defect.

Dedicated full-context tests cover:

- Password login; persisted canonical account/profile agreement; same-name cross-school and unrelated actors denied.
- Teacher, assigned guidance and administrator case creation; pinned assignment selection, saved answers, refresh retrieval, version conflict, submission, rejection, resubmission, acceptance and replay.
- Office creation/replay, administrator-only closure and no student assignment; historical snapshots exclude later private closure text.
- Real CurrencyService awards through the versioned canonical engine; concurrent retries, persisted balances/recognitions/intents, and rollback after a second recognition write fails. Historical namespace/mapping adversarial tests remain separate real-Mongo/probe evidence, not full production award-route replay acceptance.
- Real notification capture, renderer, router and SMTP mock acceptance; no duplicate dispatch, interrupted claim becomes uncertain; revoked roster prevents sending while historical snapshot remains readable. Both email-capture flag states preserve case authorization.
- Gmail connect/complete with intercepted OAuth responses, encrypted Mongo persistence, actual Gmail send HTTP interception, no SMTP fallback, and mismatched grant ownership refusal (503 unavailable, not a leaked grant).
- Roster revision conflict, cross-school admin denial; tracked-behavior write/read authorization; school-assisted student setup and replay rejection; Google setup service validation without real OAuth login.
- Always-on announcement sender/recipient checks: own roster succeeds through real services and mocked SMTP; forged sender, student actor, mixed same-name cross-school recipient and missing student ownership fail before sending. Background use without authenticated authority fails closed.
- Checkout is unavailable to every tested role, and unsigned callbacks cannot create accounts. Signed existing-billing Mongo tests run separately; these are not full Spring Boot signed HTTP callback acceptance.
- Missing/conflicting membership, disabled actor, archived student and stale spotter cannot create a case or notification.

No desktop/mobile browser rendering or Vercel build is claimed. Frontend code and contracts are unchanged.

## Route / worker ownership and name-authority review

| Path/worker family | Canonical boundary when all preparation adapters are installed | Remaining verification / disposition |
| --- | --- | --- |
| Student, roster, spotter, DTO/reporting routes | CanonicalWorkflowAccess/CanonicalDomainAccess/CoreWorkflows, exact account/profile/target IDs and independent roster/caseload | All routes reconciled; valid-payload admin/profile/spotter/report matrix incomplete. Stale spotters grant no access. |
| Punishment, office and special-concern routes | Server-owned case creation, atomic batch preparation, current relationships, tenant queries and revision checks | Core creation/assignment/office paths exercised; escalation, all alternate mutations and academic/behavioral concern variants need further full-context acceptance. |
| Guidance/tracking routes | Current participant/caseload plus exact owned student/case; transaction fences | Creation/tracking and negative cases exercised; notes/resources/follow-up/status/revocation combinations remain incomplete. |
| Assignment templates/bindings/work | Exact owned case/template or explicit global-system default; pinned snapshot; version CAS | Full student review cycle exercised; bind/recovery, all template precedence and legacy variants need full-context route acceptance. |
| Currency mutations | CanonicalCurrencyTransactions and reviewed replay manifest; exact tenant wallets, atomic receipts/debit/credits/cases/intents | Actual award/retry/rollback tested; historical receipt replay through every real award/redeem/admin-reset route is still required. Probe engine tests are supplementary. |
| Gmail grant operations/use | CanonicalEmailOwnership owner account/profile/tenant query; original encryption/AAD retained | Real connect/send intercepted; full disconnect/refresh/revocation/restart HTTP composition remains incomplete (existing focused tests supplement it). |
| RequiredNotificationService.dispatchPending (only @Scheduled worker) | Recorded immutable actor/version + parent/student/intent ownership + current independent access; claim/state CAS | Real router and provider interception exercised. Legacy intent quarantine, all retry/resolve routes, changed-recipient outcomes and multi-worker composition need further acceptance. Scheduler itself is deliberately suppressed. |
| Manual detention/ISS reminders | Current canonical administrator; scoped cases; managed system-SMTP obligation | Legacy unscoped branch is bypassed by canonical early return; full controller-to-worker acceptance outstanding. No fabricated system principal. |
| Historical case routes | Immutable persisted account-ID snapshot provenance and exact tenant; no mutable-case grant | Office/punishment snapshots and revoked relationships exercised; all legacy ambiguity/assignment provenance combinations require reviewed evidence. |
| Password/Google login/setup/invitation | Canonical persisted membership validation before profile creation; existing disabled/session protections | Password and assisted setup exercised. External Google login callback and staff invitation HTTP composition remain incomplete. |
| Stripe callback | Signature verification, existing reviewed billing/account/profile binding; no provisioning or activation | Checkout disabled; unsupported events ignored. Valid signed full HTTP callback acceptance remains required. |
| Contact/invitation asynchronous mail | Public contact is intentional non-tenant bootstrap; invitations require secured account service | Not a source of tenant authority; no real mail performed. No separate authenticated worker invented. |
| Global catalog, school directory, retired operations | Global reads or explicit deny/unavailable; no new school/role/checkout authority | Intentional exclusions documented in matrix; global metadata is not student data. External state-reporting path denies in canonical mode. |

School-name comparison hits remain in the inactive legacy branches of UserContextService, WorkflowAccessService, AssignmentResolver, student/employee/case services and currency. Their canonical branches return first or select ID predicates. `CanonicalWorkflowAccess.label` validates a display label only after deriving persisted actor ownership; it does not select the tenant. OAuth's legacy display-name profile repair occurs after canonical `provisioned` validation, which already requires an existing matching profile. School settings use `_id` as authority; name is an additional presentation/CAS condition.

`EmailReferralService` still contains direct name-based school/history queries in legacy direct-email helpers. Canonical punishment creation returns a managed response before invoking those helpers, even with email capture disabled; canonical reminders similarly return before legacy sending. Those helpers are not reusable canonical authorization boundaries. Before any new caller or production activation release, preserve this disconnect or explicitly deny legacy helper use in canonical mode. The 172-route probes do not prove every possible direct service invocation safe.

**No additional name-based bypass was confirmed in the exercised enabled routes. This is not an assertion of exhaustive absence.** Ordinary deployed behavior is still legacy until a separately reviewed cutover release. Optional injection and per-adapter settings must not become an ad hoc production rollout mechanism.

## Remaining activation blockers / next focused work

| Priority | Blocker | Focused next action |
| --- | --- | --- |
| P0 | No authorized deployed ownership inventory, reviewed mappings or complete private currency replay evidence | Separate owner approval for read-only inventory; review every root/nested/account/profile/grant/notification mapping and ambiguous quarantine. Never infer from names/current membership. |
| P0 | No approved deployed migration executor or production activation mechanism | #188 remains disposable-only. Separately review deployed target/approval/precondition protections and an atomic all-adapter startup boundary. Keep false-only production guard until release acceptance. |
| P0 | 107 mappings lack complete canonical valid-payload acceptance; 17 selected mappings are not exhaustive | Focused acceptance PRs: (1) remaining student/DTO/roster/admin and case/escalation/assignment routes; (2) real historical award/redemption/admin replay; (3) notification retry/recovery/Gmail refresh and signed existing-billing callbacks. Use real chain/Mongo, no provider calls. |
| P0 | No reviewed backup/recovery point, migration window or drained-writer evidence | Owner-approved operational rehearsal; all old writers/workers and pending OAuth callbacks drained before adoption. |
| P1 | Browser compatibility and environment-specific callback/notice behavior not verified here | Synthetic local browser acceptance and separately approved controlled provider checks only after tenant readiness; no flag activation implied. |
| P1 | Legacy direct email helpers retain name-only queries | Keep disconnected; add an explicit canonical-mode denial or remove reachability in a separately reviewed small hardening PR before expanding their callers. |

## Activation and rollback architecture

1. Continue deploying only compatible preparatory writers with all preparation flags absent and ID enforcement false. Do not add this test profile to App Engine.
2. Obtain separate owner approval for a narrowly scoped read-only deployed inventory. A valid manifest is not permission to execute it.
3. Review exact account/profile/root/nested ownership, immutable historical provenance, receipt namespaces and byte fingerprints. Quarantine ambiguity without changing IDs, balances, snapshots, delivery states or ciphertext.
4. Verify a recoverable database backup and independent restoration rehearsal. Record expected counts and versions; obtain separate migration approval.
5. Enter maintenance, stop traffic and drain every old instance, worker and OAuth flow. Do not run mixed name/ID writers.
6. Use a separately approved deployed executor: dry-run first, pinned v2 plan/manifest/approval, conditional ownership-only updates, accurate partial-progress journal and idempotent reruns. The current local executor is intentionally unsuitable for a deployed target.
7. Deploy an independently reviewed compatible all-canonical release while still in maintenance. Require every adapter and dependency; no missing-adapter name fallback. Re-run approved synthetic acceptance and validate reviewed counts/quarantine before separately approving activation.
8. After activation, perform owner-approved workflow and cross-tenant denial acceptance, currency replay and notification outcome verification. Accepted/uncertain notifications must never be reset to pending to make a test pass.
9. On failure before canonical activity, remain in maintenance and use the verified recovery plan. After canonical writes, **never revert to a name-authorizing binary**. Prefer a compatible fix with workers stopped; restoring a coordinated recovery point requires explicit approval and reconciliation of external message/payment outcomes. Do not erase ownership fields or rewrite historical receipts as rollback.

## Reproduce locally

Use a separate disposable MongoDB replica set named `a7activation`, whose sole advertised member is `127.0.0.1:27029`, exposed only on loopback. Do not reuse a normal development MongoDB. The migration executor intentionally requires the different `repsa7exec` target; do not weaken either guard to combine them.

With Java 17, cached Maven dependencies and the existing Mockito agent:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17'
$env:REPS_TENANCY_TEST_MONGODB_URI = 'mongodb://127.0.0.1:27029/?replicaSet=a7activation&directConnection=true&serverSelectionTimeoutMS=5000'
$env:REPS_CURRENCY_TEST_MONGODB_URI = $env:REPS_TENANCY_TEST_MONGODB_URI
# Run in a dedicated shell without Google application credentials or hosted runtime markers.
mvn -o -B '-DargLine=-javaagent:C:\Users\cahar\.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar' test
python scripts/tenancy/reconcile-activation-routes.py
node --test scripts/tenancy/tenant-audit.test.cjs scripts/tenancy/migration-contract.test.cjs scripts/tenancy/executor.test.cjs
```

The test creates/drops only its random synthetic database. Source/runtime mismatch fails inventory generation. Ordinary integration tests with no test URI are skipped, not verified. No migration executor was changed or executed in this task.

## Results recorded on 2026-10-30

Final Java 17 offline Maven regression run: **1,745 tests; 0 failures, 0 errors, 0 skipped**. This includes **27 new full-application canonical Mongo/security tests** and **3 activation-guard tests**. Actual Mongo opt-in suites and their executed counts are recorded in [activation-verification-results.json](activation-verification-results.json), separating full production composition from existing probe/helper evidence. Offline inventory/planner/executor contracts: **127 passed, 0 failed/skipped**. Source/runtime reconciliation: **172/172 mappings, no missing original entries**. Git whitespace checks pass. Frontend tests/build/browser: **Not run**, no frontend changes. The deployed migration executor, real providers and deployed activation were not exercised.

The initial mail test error was a spy-stubbing mistake; subsequent rejected sends exposed blank names in the synthetic seed and correctly persisted CONTENT_OR_LINK_CONFIGURATION_INVALID. Adding valid synthetic names allowed the real renderer/router SMTP and intercepted Gmail tests to pass. No application authorization or email-content checks were weakened to make these tests pass.
