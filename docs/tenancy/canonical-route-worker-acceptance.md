# A7 Phase 5C canonical route and worker acceptance

## Scope and baseline

Backend baseline: `9c7ea874c652abd738830cd614ea5263e684fa33` (merged PR #189).
This report covers synthetic isolated acceptance only. It does not authorize deployed inventory, migration, canonical activation, emails, payment calls, merge, or deployment.

The original 172 method/path mappings are reconciled with Boot runtime mappings by `reconcile-activation-routes.py`. The companion `reconcile-acceptance-evidence.py` joins actual successful test evidence to those mappings. Binding probes remain discovery evidence and never promote a route to acceptance. The CSV retains original source locations, policy, positive and negative scenarios, test names, and limitations. Unsupported legacy assignment operations are explicit business denials, not successful assignment workflows.

## Confirmed corrections

1. Canonical detention reporting queried cases by display name and could join a foreign-tenant case to an otherwise authorized student's email. The canonical path now queries the exact tenant, verifies case access, and resolves the student through canonical access. Inactive legacy behavior is unchanged.
2. `/auth/capabilities` consulted the staff-only canonical Gmail grant path for students, causing valid students to receive 403. Gmail state lookup now occurs only for eligible staff.
3. Notification capture after an authorized archived-case restoration required an active case. RESTORED now uses the authorized archived-access lookup, retaining exact ownership and current relationship checks.
4. Notification status and operator resolution checked the parent case but did not independently validate embedded intent ownership. Both now verify immutable sender account/profile IDs and exact tenant ownership. Read/resolve does not grant send authority; retry and dispatch still require active recorded sender identity, credential version, and current relationship. Current authorized administrators can resolve owned historical delivery metadata after a sender is disabled without sending another message.

## Evidence boundaries

All acceptance cohorts load the actual Boot application, JWT/security filters, controllers, domain services and Mongo repositories. Every class uses a new random synthetic database on the allowlisted loopback replica set. SMTP, translation, Google token/identity boundaries and provider HTTP are mocked/intercepted. Scheduled processing is disabled and production workers are invoked explicitly. New worker instances test persistent recovery; this is not an operating-system crash or a whole App Engine deployment rehearsal.

Fixtures contain two schools with identical names and distinct canonical IDs. Negative mutation checks compare complete database digests and SMTP invocation counts. Positive read checks require owned fixture records rather than accepting empty lists. Global metadata and empty health endpoints have explicit exceptions. OAuth callback tests include real state/session handling and persisted account checks with mocked Google identities. Signed Stripe callbacks exercise existing subscription associations without allowing provisioning.

Historical currency replay fixtures represent explicitly reviewed synthetic ownership evidence. Historical receipt bytes remain unchanged during real route retries. No deployed legacy evidence has been inspected or approved.

## Worker and supporting-operation coverage

The only `@Scheduled` application worker in current source is `RequiredNotificationService.dispatchPending`. The scheduler itself remains disabled in test composition; acceptance invokes the real implementation deliberately.

| Operation | Actual evidence | Acceptance boundary |
| --- | --- | --- |
| Intent preparation and content capture | FullApplicationCanonicalMongoTest#ordinaryCreationAssignmentAndTenantDenialsAreRealDomainOperations | Owned case and intent committed together; unauthorized creation produces neither. |
| Pending dispatch and conditional claim | CanonicalCommunicationAcceptanceMongoTest#notificationStatusRetryResolutionAndRestartAreActualOwnedPaths | Fresh worker uses persisted actor/version, parent and student ownership; accepted work is not sent twice. |
| Changed ownership, revoked roster, disabled sender or credential version | CanonicalCommunicationAcceptanceMongoTest#restartedWorkerFailsClosedForChangedOwnershipAndRelationships | No transport invocation or consumed sending attempt; unresolved ownership stays unavailable. |
| Interrupted in-flight recovery | FullApplicationCanonicalMongoTest#recordedOwnerWorkerSendsOnceThroughMockAndRecoversInterruptedClaimWithoutResend | Expired DISPATCHING claim becomes UNCERTAIN, never silently pending or resent. |
| Gmail refresh, disconnect and account isolation | CanonicalCommunicationAcceptanceMongoTest#gmailRefreshPersistenceAndDisconnectRemainAccountBound; FullApplicationCanonicalMongoTest#passwordLoginAndActualGmailConnectPersistEncryptedOwnershipAndPreventGrantBorrowing | Real encrypted grant persistence with intercepted provider HTTP; borrowed tenant grant denied. |
| Historical currency replay | CanonicalAccountCurrencyAcceptanceMongoTest#historicalAwardReplayTraversesRealProductionRouteWithoutChangingReceiptOrBalances | Reviewed synthetic legacy evidence traverses real transaction path without changing receipt or balances. |
| Concurrent currency and later-write rollback | FullApplicationCanonicalMongoTest#concurrentProductionAwardRetriesCommitOnlyOnce; #productionBulkCurrencyRollsBackIfSecondRecognitionPersistenceFails | At most one award; later persistence failure rolls back all transactional writes. |
| Signed billing callbacks | CanonicalAccountCurrencyAcceptanceMongoTest#signedWebhookCanOnlyConfirmExistingBillingAndNeverProvision | Existing persisted associations only; missing/foreign/disabled identity cannot provision or transfer ownership. |

## Reproduction

Use Java 17, cached project dependencies, and a disposable MongoDB replica set named `a7activation`, published only at `127.0.0.1:27029`. Set:

```powershell
$env:REPS_TENANCY_TEST_MONGODB_URI='mongodb://127.0.0.1:27029/?replicaSet=a7activation&directConnection=true&serverSelectionTimeoutMS=5000'
$env:REPS_CURRENCY_TEST_MONGODB_URI=$env:REPS_TENANCY_TEST_MONGODB_URI
```

Run each `Canonical*AcceptanceMongoTest` class separately, then `FullApplicationCanonicalMongoTest`; retain each cohort's XML and `target/canonical-acceptance-evidence.jsonl` under `target/acceptance-runs/<Suite>`. Run existing backend regressions with the new acceptance and Full classes excluded from that separate regression command. The report generator requires all cohort XML reports to have zero failures, errors and skips. Never point these tests at deployed databases. The initializer refuses unknown URI targets, hosted runtime credentials and wrong replica-set identities.

```powershell
node --test scripts/tenancy/*.test.cjs
python scripts/tenancy/reconcile-activation-routes.py
python scripts/tenancy/reconcile-acceptance-evidence.py
git diff --check
```

## Cutover dependencies and rollback

Deployed canonical enforcement remains prohibited. Before any cutover, separately approve read-only inventory; resolve and quarantine ambiguous ownership; review account/profile/case/snapshot/notification/Gmail/billing associations and immutable historical currency replay evidence; verify backup recovery; drain old writers and workers; approve a conditional deployed executor separately from manifest approval; rehearse the exact version and manifest; then obtain explicit activation approval. The disposable-only executor is not a deployed migration authorization mechanism.

Keep every old writer stopped after ownership additions until compatibility is confirmed. A failed acceptance must leave maintenance mode in place. Do not roll back to name-based authorization while canonically owned records or queued obligations can be processed. Currency receipts, balances, grant ciphertext, answers, historical snapshots and delivery IDs must never be rewritten as rollback. Prefer a forward correction or restoration of an approved recovery point with workers stopped and notification/provider reconciliation before retry.

No frontend behavior, environment defaults, live data or provider configuration is changed by this acceptance work.

## Final results (2026-11-02)

- 172/172 source/runtime mappings reconciled; no missing original routes or unclassified additions.
- Previous 107 incomplete mappings: valid operation/authorization evidence or an explicit tested unsupported/global classification now recorded. The complete matrix contains 101 newly recorded actual route/domain/Mongo rows, 14 retained full-workflow rows, three metadata/root rows, six intentionally unsupported rows and 48 baseline global/public/retired exclusions. Two original selected rows also received new evidence, so these categories are not a simple 107-test count.
- Full application/security-chain/Mongo acceptance: **150 passed**, zero failures/errors/skips: students/admin 43, cases/assignments/guidance 52, communications/workers 12, accounts/currency/billing 11, Google callback 4, retained full workflows 28.
- Separate existing backend regression cohort: **1,718 passed**, zero failures/errors/skips. Includes 182 actual Mongo opt-in tests in 12 existing suites; these are distinguished from the 150 full-composition tests above. Total actual Mongo tests across both cohorts: 332. Combined backend executions counted once: **1,868**.
- Offline migration/inventory/planner/executor contracts: **127 passed**; whitespace checks passed.
- Focused detention branch: **66 passed**, including 28 full-composition Mongo tests and 38 existing student security tests. This overlaps the main results and is not added to the 1,868 total.
- Frontend/browser/live-provider verification: **Not run**. No frontend source changed; frontend dev is `bd5eac0db6770fc1aa6c182a702a898edbe547e2` (PR #299 merged).

Initial Docker interruptions, fixture mistakes and pre-fix failing reproducers are not counted as successful acceptance. Green final cohort reports are required by the offline evidence reconciler.

## Remaining activation gates

| Priority | Remaining blocker or limitation | Required next step |
| --- | --- | --- |
| P0 | Fixes in this work are unmerged | Review the focused detention PR first, then the communication/acceptance PR. No automatic merge. |
| P0 | Deployed ownership and private historical replay evidence remain uninspected/unapproved | Obtain separate read-only inventory approval, review manifest and immutable provenance, quarantine ambiguity. |
| P0 | Production activation remains intentionally rejected; executor remains disposable-only | Separately approve/review deployed execution and all-adapter activation controls. Do not relax existing guards for convenience. |
| P0 | No approved deployed backup/recovery point or drained-writer/migration window | Verify restoration, stop incompatible writers/workers/callbacks, separately authorize conditional migration and later activation. |
| P1 | Representative grouped acceptance is not exhaustive state exploration | Keep matrix limitations; run approved post-cutover role/workflow acceptance against reviewed migrated fixtures before reopening traffic. |
| P1 | Browser and full-process operational recovery not exercised here | Rehearse planned deployment/process restart with synthetic data and mocked providers; distinguish that from tested fresh worker instances. |

Within the documented grouped synthetic scope, canonical route and worker acceptance is complete. REPS is **not approved or ready to activate canonical enforcement in a deployed environment** until the P0 operational/data gates are satisfied. No further business-policy decision was required by this implementation.

## Review order

1. Backend PR #190: narrow detention reporting correction (`df0d37f375ed83a02c1a04db52620794ada80600`).
2. Companion Phase 5C communication corrections and acceptance evidence. Both target `dev`; its identical detention query patch is shared intentionally. Keep the dedicated detention regression when combining branches.
3. No frontend companion is necessary. Do not start deployed migration or canonical activation as part of merging these PRs.
