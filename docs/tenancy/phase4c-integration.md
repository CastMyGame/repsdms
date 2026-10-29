# A7 Phase 4C real workflow integration (inactive)

Backend base ba08374 (merged #182/#183/#184); frontend base 7bd7fe2 (#298). This document supersedes earlier disconnected-domain/worker statements only for the paths listed here. It does not authorize cutover, backfill, live inspection, or provider calls.

## Boundary

All preparation properties remain absent/false by default. CanonicalWorkflowAccess, CanonicalDomainAccess, HistoricalCaseAccess, CanonicalEmailOwnership and CanonicalCurrencyTransactions require `tenant-isolated-verification`. `reps.tenancy.id-enforcement-enabled` remains a false-only startup guard. Existing deployed routes continue using their current behavior until a separately reviewed coordinated release; this PR is not a claim of deployed tenant isolation.

Use all applicable preparation beans together in isolated tests: `reps.tenancy.core-preparation-enabled`, `reps.tenancy.domain-preparation-enabled`, `reps.tenancy.email-preparation-enabled`, and, for money, `reps.tenancy.currency-preparation-enabled`. A domain request that requires money or email refuses a missing supporting canonical adapter instead of calling the legacy engine. Do not put these settings in App Engine or ordinary development startup.

## Integrated production service paths

| Path | Canonical treatment and side effects |
| --- | --- |
| Student reads/reports/directory | Exact tenant queries; current independent roster/caseload/self/admin checks. Enrollment directory and compatible duplicate-create responses contain only minimal identity fields, not notes/history/balances. |
| Student creation/archive/profile/notes/time | Server-owned account/profile inserts; scoped target checks, existing profile revisions and fixed updates. Archived accounts remain unavailable. Destructive deletion and old points stay retired. |
| Classes/rosters/spotters | Existing roster revision CAS and independent caseload/roster checks remain; only own spotter subscription unless ADMIN; subscriptions never establish access. Tenant-scoped targets. |
| Punishment creation/special concerns/escalation | Whole batch prepares in MongoDB transaction; student, actor, School and child-referral ownership checked before persistence. Server-owned case and immutable initial historical grants; pinned assignment snapshots. Managed response avoids legacy direct email. |
| Ordinary/office reads, lists, joins, DTOs | Exact case ownership and current relationships; student aggregation joins restrict the tenant too. School DTO resolves School._id. Historical grants never authorize a mutable-case response. |
| Closure/archive/restore | Current participant and active student checks plus tenant/revision-protected root saves. Office close requires ADMIN; no office assignment flow. Former authors cannot mutate by using a snapshot grant. |
| Guidance/tracked behavior | Existing actual canonical services plus resource-intent integration, canonical student lookup, persisted staff identity and guidance-note restrictions. Resources require managed communication; no direct legacy send in canonical mode. |
| Assignment template/binding/work | Tenant/global-system predicates; teacher/school precedence; block unresolved legacy teacher bindings, preserve original pinned IDs/snapshots, answer validation, submission/review history and revision CAS. Global legacy assignment mutations remain blocked. |
| CurrencyService award/redeem/reset | Actual service selects CanonicalCurrencyTransactions only with matching domain preparation. Tenant wallet CAS and result lookup; complete preparation precedes debits. Versioned replay manifest, historical namespaces and payload hashing unchanged. Ambiguous historical replay is still blocked. |
| Gmail grant use | Same mailbox key, encryption/AAD/ciphertext; owner-qualified grant operations in request and explicit internal recorded-owner execution. No legacy grant adoption or SMTP fallback after Gmail attempt. Domain mode refuses a missing email adapter. |
| Notification capture/status/retry | Exact parent and actor ownership, current permissions, server-derived actor/account/profile/version metadata; embedded intents remain atomic with cases. Legacy unmapped intent ownership is never guessed. |
| Worker claim/dispatch/recovery | No SecurityContext impersonation. Short-lived thread-local recorded-owner scope is created only after immutable intent IDs/version, parent/student ownership and current roster/caseload/participant checks. Every scope use revalidates the account. Finally clears scope. Claims and state CAS qualify parent and intent ownership. Removed participants receive no new copies. Interrupted dispatch becomes UNCERTAIN, never automatic resend. Known pending actor failures become FAILED without provider attempts; unmapped legacy obligations remain untouched for reviewed resolution. |
| Manual ISS/detention reminders | Current same-tenant administrator selects canonically owned cases; creates system-SMTP obligations. Old author receives Reply-To/copy only with a current independent relationship. No global anonymous scheduler identity is fabricated. |
| Password/Google login, setup/reset/invitations | Optional canonical provisioned-membership validator permits a legitimate pending setup target but does not authenticate it; actual login still requires completed setup. Invalid/missing/conflicting account/profile/School membership denies access before Google profile auto-repair or token completion. Fixed setup writes qualify ownership; account IDs and JWT infrastructure unchanged. |
| Stripe callback | Checkout remains disabled. Callback requires exact canonical existing account/profile/School agreement and existing customer/subscription/price associations, with tenant-qualified billing confirmation. Payment never creates membership, school, activation or roles. Unsupported invoice/subscription events remain ignored. |
| Legacy maintenance/external state reporting | Unsafe global maintenance and unverified external reporting fail closed in canonical mode; no name lookup or provider call is substituted. Controlled maintenance needs a separately reviewed implementation. |

## Test evidence and limits

`CanonicalFullWorkflowMongoSecurityTest` uses actual Punish/AssignmentLifecycle/OfficeReferral/Student/Guidance controllers, the real SecurityConfig/JWT chain, domain services, repositories, canonical currency engine and a disposable replica set. Transport is mocked, not live SMTP/Gmail. Individual assertions cover ordinary creation/replay, unauthorized batches, assignment persistence/conflicts, actual CurrencyService replay and second-recognition rollback, recorded-owner worker acceptance/recovery, revoked relationships and both email-gate states. Importing a controller is not proof of testing every route.

`CanonicalStudentHistoryMongoSecurityTest` exercises production Student/Guidance/HistoricalCase controllers and protected root persistence. Existing CanonicalDomain/Email/Currency tests retain their test-only probe coverage and must not be counted as production-controller end-to-end coverage. Full suite and offline-tooling results are recorded in the PR. No deployed data, MongoDB indexes, browser rendering, real providers, webhook provider delivery or migration completeness was verified.

## Remaining activation dependencies

- Reviewed pilot-school account/profile/case/template/binding/notification/grant mappings, exact counts/fingerprints, collision/orphan resolution and complete currency history manifest. No automatic adoption.
- Coordinated stop of old writers/workers and pending OAuth flows. Protect all versions during deployment; preparation flags cannot be used as a production rollout switch.
- Full HTTP acceptance for every route in the matrix, mobile/desktop browser checks, account/billing callbacks and all reviewed historical scenarios against a rehearsal dataset. This PR's focused tests are not exhaustive route acceptance.
- Transactionally integrated case, assignment, guidance, roster and currency mutations fence persisted actor membership and selected student relationships using additive `authorizationFence` counters. This serializes account/profile or caseload revocation against the guarded transaction rather than relying only on snapshot reads. Balances, IDs and receipt hashes are unchanged. Rehearse every supported route and revocation ordering before replacing the false-only activation guard; independent profile/onboarding updates retain their existing conditional-write protections.
- No new cross-tenant scheduled actor, external-reporting connector, invoice handler or student-history expansion is supplied. Unsupported maintenance stays closed.
- Preserve IDs, balances, receipt hashes, Gmail ciphertext, assignment/notification history. Never roll back to a name-authorizing binary after canonical activity.

## Notification manifest compatibility

New intents additionally capture stored-only `actorCredentialVersion` from the persisted acting account. Workers revalidate this version throughout the recorded-owner scope. Existing intent IDs, outcomes and provider metadata are unchanged. Legacy intents without this value are not adopted from the current account version; they remain blocked for separately reviewed authorization/outcome resolution. Do not backfill a guessed version or reset accepted/uncertain outcomes to pending. In-flight recovery only marks a structurally owned, recorded attempt UNCERTAIN; it grants no sending authority and remains safe if the actor has since been disabled. Pending scans require complete recorded-owner metadata so unmapped legacy obligations cannot occupy every batch slot.

## Rehearsal and cutover checklist

1. Keep every preparation flag absent in ordinary App Engine/local configuration and leave the ID-enforcement guard false. Deploy preparatory writers only before adoption.
2. Rehearse using an isolated MongoDB replica set and synthetic same-name schools. Set `REPS_TENANCY_TEST_MONGODB_URI` and `REPS_CURRENCY_TEST_MONGODB_URI` to the same disposable loopback URI, with no database name or credentials. Tests assert the URI and create/drop randomly named test databases. They never inspect an existing development database.
3. With Java 17 and cached dependencies, run `mvn -o -B test` using the project's Mockito Java agent configuration. Run `node --test scripts/tenancy/tenant-audit.test.cjs` separately. Missing URI means integration tests are skipped, not verified. Do not point these variables at Atlas or another shared database.
4. Independently approve the read-only inventory, mapping manifest and reviewed pilot-school scope. Verify exact account/profile ownership agreement, all root and nested ownership, duplicate/orphan counts, billing association evidence, old grants and unresolved notification outcomes. Snapshot provenance requires as-of evidence, never today's email or mutable case text.
5. Validate the historical currency manifest against untouched receipt fingerprints and every old namespace. Ambiguity blocks the action rather than issuing another award. Do not alter receipt IDs, hashes, balances or old recognition references.
6. Drain old instances, workers and pending OAuth flows. Preserve encrypted keys/ciphertext, all IDs, versions, snapshots and notification message/provider state. No old writer may run after ownership adoption.
7. Rehearse conditional/idempotent backfill, exact before/after counts and conflict refusal under separately approved tooling. This PR contains no backfill executor or authorization to run one.
8. Require complete route/worker HTTP acceptance and revocation/concurrency fencing review before a separate activation release replaces the false-only guard. No combination of the current preparation properties authorizes production cutover.
9. Before adoption, rollback may use defaults and remove preparation code. After adoption or canonical activity, stop affected traffic/workers and use a compatible tenant-aware release or reviewed forward repair. Preserve newly written ownership and historical facts; do not return to name-only authorization or replay old actions.

Normal deployment requires no new environment variables, secrets, indexes or schema migration from this inactive PR. Future cutover prerequisites remain explicit above. This PR does not inspect deployed index shape, support mixed-version canonical writers, or claim launch readiness.

Canonical currency contention retains bounded retries. A concurrent caller may receive 409 while another request commits; retrying the identical action ID/payload must resolve to the existing receipt and recognition, without a second debit/credit. The real CurrencyService concurrent integration test verifies both possible initial outcomes and the final replay/balance/receipt invariants. No provider call is made on an action's request thread.
