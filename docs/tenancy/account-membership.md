# A7 Phase 3: persisted membership preparation

Baseline: dev 91da9a2 after merged PRs #177/#178. The Phase 2 prerequisite was independently verified here: all 37 existing real MongoDB tenant/writer/currency tests passed with zero skips on a disposable local replica set before implementation. No deployed data inspection or backfill occurred.

## Inactive boundary

`CanonicalAccountContext.requireCurrent()` is an explicit opt-in service. No production controller, login handler, authorization service, worker, invitation flow or currency path calls it in this PR. It has no application endpoint, filter registration, automatic migration, startup DB lookup or activation flag. Only test-source controllers exercise it through the production Spring Security chain. The existing false-only `reps.tenancy.id-enforcement-enabled` preparation guard is unchanged and still refuses premature ID-only activation.

Stored-only `UserModel.tenantSchoolId` is additive, HTTP-ignored and optional. Employee/Student metadata from Phase 2 remains unchanged. Legacy school/schoolName values remain available for existing display/compatibility, and current routes still have their existing policies until coordinated replacement. The new canonical context never compares names, calls a name resolver, infers IDs or repairs records. Missing metadata denies canonical resolution while legacy login is preserved. This is not a prolonged runtime ID/name authorization fallback: runtime conversion is still pending, and the new service has no fallback path.

## Canonical actor contract

1. Use only an authenticated, enabled/unlocked versioned REPS AccountPrincipal from Spring Security. Password and Google login already issue REPS JWTs that the existing filter validates into this principal. Caller school/actor fields and principal authorities cannot establish membership or roles.
2. Reload the persisted UserModel using the principal username. Require matching username, a nonblank account ID, enabled/completed setup state and unchanged credentialVersion.
3. Reload employee/student profiles independently. A student requires the sole STUDENT role, an active matching Student and no ambiguous Employee profile. Staff require only supported TEACHER/GUIDANCE/ADMIN roles, an active matching Employee, role agreement with UserModel and no ambiguous Student profile. Email matching follows existing case-insensitive profile semantics; tenant IDs are never normalized.
4. Require nonblank exact account/profile tenantSchoolId agreement, no surrounding whitespace, and a School found by that exact _id whose returned ID also agrees. Missing, conflicting, malformed, ambiguous or nonexistent ownership is denied with a generic message. Missing/opposite profiles or incompatible role records need reviewed remediation, not silent profile creation or role repair.
5. Return immutable account ID, profile ID, canonical School ID and a copied set of persisted roles. Do not return mutable account/profile objects as authority. Student/case/roster/caseload permissions are additional requirements for future callers; tenant membership alone grants none.

Raw Google OIDC cookie principals intentionally cannot resolve this context: their provider subject is not the versioned REPS principal. The current frontend's Google-login REPS JWT is supported and tested. Any future cookie-only canonical access needs an explicit persisted/versioned principal adapter and session-revocation review before activation. No OAuth scopes, callbacks, claims, Gmail permissions or JWT cryptography change here. The context is not a substitute for the JWT filter: revoked/expired/credential-changed tokens must be rejected by that filter before invocation.

## Membership/writer inventory

| Path | Persistence and identity behavior | Preparation / remaining activation dependency |
| --- | --- | --- |
| AuthControllers password login; CustomUserDetailsService; JwtFilterRequest/JwtUtils | Existing account lookup, disabled/setup checks, persisted role reload, signed subject/credentialVersion and Mongo-backed revocation. Login itself does not persist membership. | Unchanged. Canonical context accepts only the resulting REPS principal; no tenant claim is added or trusted. |
| CustomOAuth2UserService; OAuth2LoginSuccessHandler | Existing-user approval/verified Google email, missing-profile creation, googleEstablished fixed-field setup completion, then the same REPS JWT path. | No ownership propagation/repair added. At cutover, missing or unmapped profiles must fail canonical resolution even if legacy profile repair/login succeeds. Review canonical profile creation before wiring login. |
| AuthService.createEmployeeUser; EmployeeCreationService | New account through UserRepository.save; new employee through protected profile save; invitation afterward. | Account save is now insert-only; no existing account can be replaced. No new or legacy owner assigned. Existing role/school preparation stays in place. |
| AuthService.provisionStudent; StudentMutationService | Deterministic account/profile IDs, insert-only fresh explicit-field models and compatible retry checks. | Preserve IDs/roles/passwords; insert cannot replace an existing _id. No client canonical field is copied. Canonical provisioning must later use verified persisted membership, not names. |
| UserAccountService.createUsersForSchool | School-controlled fresh student-account inserts; deterministic identities; no existing-account saves. | Ownership remains absent until separately reviewed mapping. Existing legacy school authorization is not claimed as canonical. |
| UserRepository.save/saveAll via UserInsertOnlyRepositoryImpl | All current production save callers create new accounts (AuthService and Stripe). Previously generic save could replace all account fields. | Always mongo.insert, never upsert/save/replace. Non-null proposed tenant metadata is rejected; an existing _id/unique-key collision becomes generic HTTP 409. This preserves owner, credentials, tickets, roles, enabled state, billing and unknown fields even for stale ownerless objects. saveAll is sequential, not transactional. |
| UserRepository fixed updates; UserAdministrationService | Explicit role/enabled/password/billing fields; linked employee role/archive updates. | Tenant field is never changed. Existing account/profile role synchronization remains; any transient mismatch fails canonical context rather than authorizing through either record alone. ID-based administration predicates remain future coordinated work. |
| AccountAccessService invitation/reset/handoff/complete/revoke/googleEstablished | Account-local single-use/environment-bound expiring tickets, bounded attempts, fixed-field password/setup/credentialVersion updates and mocked SMTP during tests. | Metadata is preserved; no whole account save. Canonical actor/target checks must replace legacy-name authority at cutover; this PR does not activate them. Disabled or pending actors cannot resolve the new context. |
| Profile writes: BalanceSafeSave, employee roster CAS, student profile CAS, notes/time/spotters/archive, currency $inc | Phase 2 ownership/currency preservation plus fixed-field updates. OAuth and student provisioning insert fresh profiles only. | Unchanged and rerun against real Mongo. Metadata remains immutable through ordinary reviewed paths; no new profile replacement path. |
| StripeProvisioningService; UserRepository.updateBillingIfEnabled | New teacher account save and existing-account fixed billing updates; RegistrationIntent uses existing School._id association. | New saves insert only. No subscription/customer/event IDs, old intent fields or provisioning policy changes. Signed payment never grants canonical membership. |
| GoogleOAuthTokenStore | Gmail grants stored separately by normalized email, raw targeted writes, ciphertext bound to email through AAD. | No grant, key, cipher, owner or mailbox rewrite. Future verified ownerAccountId/tenant binding remains deferred. |
| Legacy password reset snapshots / dormant maintenance | Some legacy records embed old UserModel snapshots; retired password maintenance remains denied. | No scan/migration/revival of old credentials. No new token authority derived from embedded membership. |

Account and profile models still support repository insert for trusted explicit creation. No HTTP endpoint accepts UserModel as an account-creation authority, and current factories never populate tenantSchoolId. Direct MongoTemplate.save/replace/raw driver writes bypass repository fragments and are unsupported for accounts/profiles; no current production account/profile caller uses them. Controlled future migration is a separate reviewed writer, not an ordinary setter/save. Existing destructive repository helpers and unrelated model replacement writers are not globally intercepted by this PR.

## Compatibility and deployment precautions

- No IDs, balances, receipt keys/payload hashes, transactions, pinned assignment snapshots, answers, case history, notification intents/states, Gmail encryption material or Stripe associations change. UserModel's new property is ignored by HTTP serialization. Frontend names remain unchanged.
- Existing unbound accounts and newly provisioned accounts remain unbound. No name-to-ID inference, account/profile repair, automatic backfill, index or schema validator is introduced. Canonical context requires reviewed mapping before use; do not enable it by partially editing route call sites against existing data.
- Generic account replacement saves are intentionally no longer supported, even for legacy unbound accounts. Known production callers create new accounts; existing updates must retain fixed-field authorized actions. Separately controlled scripts, alternate binaries and direct Mongo writes require review before deployment/backfill. Do not deploy incompatible writers alongside the final cutover release.
- No new environment variables/secrets or key rotations. No JWT claim/expiration, password-login, Google callback, invitation token, school provisioning or currency API contract changes.
- Keep both account and profile owner metadata immutable during ordinary traffic. Canonical resolution reads separate persisted documents; it is not a migration transaction or locking mechanism. Any future owner reassignment/backfill must drain writers/sessions/workers and verify coherent mapping under the cutover runbook. Account transfers remain unsupported.
- Before backfill, preparation rollback can use the previous compatible binary while records remain unbound. After ID cutover, use tenant-aware rollback/maintenance or reviewed forward correction; never restore name authorization or overwrite new activity with an old database.

## Test procedure

The isolated tests use only explicitly provided loopback URIs without credentials or an existing DB name. They create/drop their own randomly named databases. No Boot cloud profile or real email/OAuth/Stripe calls are used. The security-chain probe exists only in test sources; it does not add a public test endpoint.

```powershell
# Only for a separately started disposable local replica set:
$env:REPS_TENANCY_TEST_MONGODB_URI = 'mongodb://127.0.0.1:27028/?replicaSet=a7rs&directConnection=true&serverSelectionTimeoutMS=5000'
$env:REPS_CURRENCY_TEST_MONGODB_URI = $env:REPS_TENANCY_TEST_MONGODB_URI
# Use Java 17 and the project's existing Mockito agent configuration where needed.
try {
  mvn -o -B '-Dtest=CanonicalAccount*Test,UserInsertOnlyRepositoryTest,TenantOwnership*Test,TenantMetadataMongoIntegrationTest,CurrencyMongoIntegrationTest' test
} finally {
  Remove-Item Env:REPS_TENANCY_TEST_MONGODB_URI,Env:REPS_CURRENCY_TEST_MONGODB_URI
}
```

Actual verification in this task used cached MongoDB 6 (wire version 17), one primary replica set a7rs, temporary container storage, no credentials/mounts, loopback port 27028 and a dedicated bridge with IP masquerading disabled. Docker Desktop's internal network did not publish a host-reachable port; that test-only network was replaced before successful execution. Sandbox socket restrictions required approved execution outside the sandbox. Failed connectivity attempts are not counted as Mongo verification.

Final Java 17 selected backend regressions: **1,299 passed, zero failures/errors/skips**. Includes authentication/JWT/revocation, password/Google login, invitations/activation, school/student/staff security, roster/cases/guidance/behavior/assignments, currency and email/Stripe regressions. Phase 3 adds 13 context unit tests, 4 insert-only/HTTP-binding unit tests, 22 real-security-chain fixture tests with email gates off/on, and 9 real account Mongo tests. The latter exercise actual fragment discovery, stale-save/disable concurrency, legacy unbound data, exact same-name-school membership, single-use setup-token completion and unchanged synthetic grant/receipt/case BSON. They are not live-provider verification or proof that all production routes have converted.

Actual Mongo tests in the final run: TenantOwnershipWriterMongoIntegrationTest (7), TenantMetadataMongoIntegrationTest (6), CurrencyMongoIntegrationTest (24), CanonicalAccountMongoIntegrationTest (9): **46 passed, zero skipped**. Offline Node inventory/planner suite: **26 passed**. Whitespace checks passed. Frontend tests: Not run (no frontend changes). No live OAuth, SMTP/Gmail, Stripe or deployed database calls were used.

## Remaining before ID-only cutover

1. Protect and adopt ownership for cases, guidance, behaviors, templates/bindings, receipts, Gmail owner/account associations and Stripe roots; preserve all history and global defaults.
2. Implement/test the versioned legacy currency receipt replay bridge before generating canonical receipt keys. Do not change hashes or award again on an ambiguous legacy retry.
3. Convert every authorized route, target lookup, relationship join, ticket/provisioning path and background dispatcher predicate to persisted canonical IDs together; retain role/roster/caseload/self restrictions. Decide and implement any raw OIDC-session adapter before enabling that path.
4. Obtain separate live-inventory approval, verify account uniqueness/profile ambiguity and historical ownership evidence, review conditional pilot mapping and rehearse the complete release. No deployed data uniqueness or ownership coverage was inspected here.
5. Separately approve backfill, drain incompatible writers, verify canonical account/profile/root agreement, then perform the coordinated fail-closed ID-only release under cutover-and-compatibility.md. A7 remains open.
