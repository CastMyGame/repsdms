> Phase 4C update: see [real workflow integration](phase4c-integration.md), [historical snapshot policy](historical-access.md), and [route/worker matrix](phase4c-coverage.md). Earlier phase descriptions below remain historical except where explicitly superseded. No production activation is authorized.

# A7 Phase 4B-2 email ownership preparation

Base: dev 894b6cc, merged #181. This is isolated request-path preparation, not a dispatcher or production tenant cutover. Currency bridge work is reviewed separately in backend PR #182; approved checkout retirement is backend #183 / frontend #298. The supporting-system entry-point inventory is in the currency PR's docs/tenancy/supporting-systems.md.

## Explicit inactive boundary

CanonicalEmailOwnership is absent unless reps.tenancy.email-preparation-enabled=true. It requires reps.tenancy.core-preparation-enabled=true and the explicit tenant-isolated-verification active profile. Missing core dependencies or use outside that profile fails closed; direct guard calls also check the profile. No App Engine/dev property or normal launcher enables this. The global ID-enforcement guard remains false-only. Existing password/Google login, onboarding, email flags, provider classification and normal dispatcher behavior remain unchanged with the preparation flag absent.

Do not use this flag to activate a production route. In isolated email preparation, notification workers and reminders remain deliberately disconnected. With normal email/dispatch gates disabled they remain off; with gates enabled they return 503 before claims, state recovery, provider lookup or sending. A complete canonical worker-to-router-to-provider boundary is still required. No fake SecurityContext or name-based authorization fallback is introduced.

## Real request paths integrated

| Entry point | Isolated canonical treatment |
| --- | --- |
| GoogleOAuthTokenStore connection/status/load, begin/complete, refresh/persist, rejection/disconnect | All shared user queries validate current persisted account/profile/School, authenticated staff identity, and exact grant metadata; every read/update is qualified by mailbox key plus ownerAccountId, ownerProfileId and tenantSchoolId. Existing token version predicates and pending browser-proof/state rules remain. |
| RequiredNotificationService prepare/save paths | Require exact case/student/actor tenant and independent roster/caseload/self permissions. New intents record server-derived tenantSchoolId, actorAccountId and actorProfileId. Student submission/completion remains system SMTP policy; no sender selected from the frontend. No root ownership is inferred or assigned. |
| creation replay / previousSubmission / load / statuses / listStatuses | Tenant-qualified queries and canonical object relationships. Existing case/intent/message IDs remain unchanged. Status history does not require reclassifying an old outcome. |
| retry / resolve | Fresh current account/case permission and tenant-qualified state/attempt CAS. Retry additionally validates the recorded intent account/profile IDs and current enabled/nonarchived membership. Unmapped historical intent ownership blocks retry. UNCERTAIN/delivered outcomes are not resent; invalid canonical retry returns 409. Resolution requires canonical ADMIN. |
| dispatchPending / dispatchOne / reminders | Disconnected in isolated canonical mode before claims or sends. Legacy production path remains unchanged with preparation absent. No canonical email delivery is claimed. |

CanonicalAccountContext.requireRecorded validates exact persisted account/profile/tenant agreement for an internal recorded identity without pretending it is an HTTP login. It is not authentication, a reusable authorization capability, or proof of student access. Future workers must independently check student/case relationships and participants immediately before a provider operation.

## Gmail compatibility and review contract

The existing normalized-email _id, AES-GCM ciphertext/AAD, encryption key, provider subject, scope, version and Google-login/send-authorization separation are unchanged. New first-time isolated grants derive the three owner equality fields from trusted context during their initial upsert. An existing email-keyed row with missing/conflicting metadata is refused before refresh, disconnect, overwrite or provider requests; it is not adopted from current membership and is not marked reconnect-required merely because tenant metadata is absent.

Legacy grant adoption requires a separately reviewed conditional mapping of the existing grant _id to exact ownerAccountId, ownerProfileId and tenantSchoolId, with historical account/mailbox evidence and before fingerprint/version. Preserve the grant key, subject, cipher, encryption key and AAD. Do not decrypt/re-encrypt tokens or make users reconnect just to attach ownership. Missing/ambiguous ownership remains blocked for controlled review. These private mappings must not contain token values in reports or Git; this PR contains no importer/backfill executor and inspects no deployed grants.

Drain/expire pending OAuth flows before future cutover so an old callback cannot overwrite a reviewed grant. Drain old token writers and dispatch workers before adoption; an old email-only caller cannot be made canonical by adding metadata alone. Fresh staff-account/profile validity is checked before token use, while version/owner predicates prevent stale writes. Provider/SMTP fallback behavior is unchanged.

## Notification compatibility and remaining worker work

The three new embedded ownership fields are stored-only and ignored by HTTP JSON binding/serialization. Legacy absent values remain absent; no historical state, provider ID, claim, message/thread ID, recipient outcome or content snapshot is rewritten. Case revisions continue protecting array updates. A new canonical capture derives its IDs server-side; a retry cannot attach today's email holder to an old intent. Reviewed historical actor ownership is required before old obligations can be retried canonically.

Before production activation, finish recorded sender identity through RequiredNotificationService -> EmailRoutingService -> GoogleMailService/GoogleOAuthTokenStore, including system actors and verified SMTP Reply-To. Queries/claim/recovery must qualify parent ownership and recheck case/student/actor ownership. Rebuild recipient projections from current persisted student/guardian and independently authorized staff relationships. Former roster/guidance assignments and stale spotters/author email must not preserve future copy access. School names, provider success and grant presence are not recipient authority. Preserve no-fallback-after-Gmail-attempt, retrySafe rules, attempt IDs and UNCERTAIN handling; never reset claims/states or generate new message IDs because of migration.

Case creation/closure, assignment transitions and other domain services still need complete coordinated conversion. This request-side preparation does not prove their production controllers are canonical. Normal dispatcher recovery and SMTP/Gmail transport behavior continue to be verified by existing mocked regression tests; they are not evidence of an activated canonical worker.

## Verification and execution

Use Java 17, existing Mockito agent configuration and a disposable loopback MongoDB replica set. Set REPS_TENANCY_TEST_MONGODB_URI only to an isolated URI with no database name/credentials; CanonicalEmailMongoSecurityTest creates/drops a random reps_a7_email_test database. Its actual security chain calls real token-store and notification request methods. Google token/userinfo/refresh/revoke HTTP is intercepted by MockRestServiceServer; no live provider requests occur. Existing email suites cover default-off compatibility, provider errors, durable retry and uncertain recovery separately.

Focused command: mvn -o -B '-Dtest=CanonicalEmail*Test,CanonicalAccount*Test,GoogleOAuthTokenStoreTest,RequiredNotificationServiceTest,Email*Test,GoogleMailServiceTest,GmailConnectionControllerTest' test. Missing Mongo URI skips opt-in tests; skips are not persistence verification. Record actual results in the PR.

## Cutover / rollback

Deploy only with preparation flags absent. Complete the worker/provider and domain dependencies, separately approved historical inventory/mappings, pending-flow drain, owner-preserving writers and same-name-school controller/worker tests before replacing the global activation guard. All old writers/workers must be stopped for backfill and coordinated activation. Preserve backups/fingerprints and unresolved records; never guess ownership or reset notification states to unblock migration.

Before metadata adoption, preparation can roll back with flags absent. After ownership adoption or canonical activity, do not roll back to email/name-only authorization or an owner-unaware writer. Keep IDs/cipher/history and use maintenance mode, a compatible tenant-aware build or reviewed forward repair. No merge, deployment, backfill, live provider call or real message is performed here.
