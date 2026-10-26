# Disciplinary case creation authorization

## Reviewed bases and confirmed defects

Backend dev `0acfff4` includes PR #173; frontend dev `336bf41` includes PR #296.

`PunishmentCreationService.prepare` previously enforced an independent relationship only when a guidance description was present. Ordinary, academic, behavioral, incomplete-work and referral requests otherwise needed only verified same-school staff. `OfficeReferralService.createNewOfficeReferral` also checked only school membership. Both non-currency bulk paths persisted one item before checking later items. Missing office-referral students produced a 500 with an identifying message.

## Endpoint inventory and callers

All creation routes require authentication through the production SecurityConfig chain and persisted staff verification at their controllers. Service checks now apply even to internal/direct callers.

| Method and path | Cases / callers | Authorization and side effects |
| --- | --- | --- |
| POST `/punish/v1/startPunish/form` | Deprecated single-item route; supported alternate API, no current application creation caller found | Same creation policy as bulk, before assignment resolution, phone notes, case persistence, linked guidance and notifications |
| POST `/punish/v1/startPunish/formList` | Shared desktop `createPunishmentPanel.tsx`; legacy `createGuidanceReportPanel.js`; `MobileAdminCreateRecord.tsx`, `MobileAdminCreateRecordV2.tsx`, legacy `MobileAdminCreateStudentTeacher.tsx`; `MobileTeacherShoutOut.tsx` | Ordinary infractions, Behavioral Concern, Academic Concern, Failure to Complete Work, special guidance labels, admin-referral flag and automatic level-four referral; full non-currency preparation before any write |
| POST `/officeReferral/v1/startPunish/adminReferral` | Desktop/shared `createOfficeReferral.tsx` | Full batch preparation and authorization before referral or notification persistence; actor and school from persisted records |
| POST `/guidance/v1/guidance/new` | Guidance forms and student self-request (PR #296) | Already secured in PR #173; independent student access, same-school scope, trusted staff actor; student self-request remains a distinct supported workflow |
| POST `/tracked-behaviors/v1/save` | Desktop/mobile behavior tracking | Already secured in PR #173; independent relationship and full batch validation |

Positive Behavior Shout Out uses the two punishment routes. It already rechecks independent access inside the existing currency transaction; it now also checks every student before transaction entry and before receipt replay. Unique receipts, debit/credit atomicity and notification-intent capture are unchanged. No new currency path or transaction mechanism is introduced.

The punishment facade only delegates creation to the creation service. Automatic level-four escalation calls the same authorized office-referral service internally. No additional disciplinary creation aliases were found. Closure, assignment submission, review and historical reads are separate workflows and are unchanged.

## Authorization matrix

Every permitted staff actor requires an enabled, password-setup-complete persisted UserModel and matching active Employee identity and school. Student records must be active and in that school.

| Persisted actor | Create disciplinary case |
| --- | --- |
| Anonymous / student / unsupported role | Denied (anonymous 401; authenticated denial 403) |
| Teacher | Current persisted Employee class roster contains the student |
| Guidance | Current Student.guidanceEmail assignment matches the actor (or another independently valid role relationship under existing multi-role policy) |
| Administrator | Same-school student; no roster required |
| Disabled, archived or pending-setup staff | Denied |
| Archived student / missing student / cross-school student | Denied |

Spotters are subscriptions only. Revoked rosters/caseloads cannot be replaced by old spotter entries, recorded authorship or forged HTTP roles. Client teacherEmail is overwritten with the authenticated verified staff email, including before currency payload hashing. Client school fields are not authority; saved school comes from the student and school records. A selected student ID/email is a lookup key, never proof of access.

## Persistence and compatibility

Non-currency batches prepare every item before any case, phone note, guidance or notification write. Unauthorized later items therefore cannot leave an earlier authorized item persisted. Managed action-ID lookups still occur after relationship authorization. Office creation retains managed duplicate handling in its persistence step. Email gates do not control authorization.

No schema changes, migration, data rewriting, environment variables, secrets or feature-default changes. Existing historical records, balances, claims and assignment bindings are untouched. Google/SMTP and case notification routing remain unchanged. Existing students and staff with legitimate current relationships remain compatible; historical/spotter-only access never grants new creation rights.

Frontend request and response contracts are unchanged. Existing student selection uses secured student/overview data. Existing handlers only report success after a successful response and preserve inputs on rejected requests. A selection that became stale can now receive 403. Some older forms show a generic error rather than explaining the lost relationship; improving that wording is a separate optional usability task. No frontend PR is required.

## Verification and limits

`DisciplinaryCreationSecurityTest` and its email-enabled subclass use the real SecurityConfig filter chain, controllers, facade, creation services, utilities and persisted-identity authorization. Persistence and external services are mocked. They cover authorized/unrelated teachers, assigned/unassigned/revoked guidance, same/cross-school administrators, forged actor/school/HTTP roles, missing/disabled/archived/pending actors, archived/missing students, spotter-only/revoked relationships, full-batch denial before side effects, special/referral flags, level-four office escalation and direct-service bypass attempts.

Java 17 offline regression run: 1,113 tests discovered, 1,090 passed, zero failures/errors, 23 opt-in real-Mongo currency tests skipped because no isolated replica-set URI was supplied. This includes 44 new production-security-chain tests (22 under each email-gate configuration). Frontend compatibility: 29 Jest suites / 209 tests passed; TypeScript passed. Git whitespace checks passed.

Additional regression suites exercise existing currency, identity, student, roster, assignment, guidance, authentication and email behavior. Legacy identity/currency test fixtures now include a real independent roster rather than relying on spotters.

This change does not make non-currency batches transactional for unrelated infrastructure failures. It does not provide an atomic transaction between concurrent roster revocation and a non-currency case write; it validates current persisted relationships during request preparation, as the established policy does elsewhere. Currency retains its in-transaction validation. No deployed database or providers are contacted. Real MongoDB currency integration tests require an explicitly configured isolated replica-set URI and must be reported separately if skipped. Browser/manual deployment checks remain pending owner review.

## Deployment

Review and merge the backend PR into dev after #173 (already merged), then deploy the backend. Existing frontend #296 (already merged) is compatible; no companion frontend release, migration or flag activation is required. After deployment, use synthetic authorized/unrelated accounts for a controlled smoke check; this implementation run performs no live creation or email sends.
