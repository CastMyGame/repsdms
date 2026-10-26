# Legacy student API security closure

Base reviewed: backend `a3a6ea6eb7c257b15e46b6f246167d64ae1c977c`, frontend `0aabd31869bc9497132b3e9e0abfeb24819f4418` (dev). No deployed student data was inspected or changed.

## Confirmed defects and policy

Previously, the security chain required authentication for most student routes but did not establish persisted roles, school membership or object access in the underlying query, spotter, notes, time and archival services. Points transfer was explicitly anonymous. Spotter add/remove accepted arbitrary staff/student lists and saved whole student documents; spotter membership alone then granted reads and case participation. Bulk profile edits could change points, notes and relationships. Destructive deletion removed history references; archival did not revoke account access.

The services now enforce persisted enabled/setup-complete identities and school membership. Students read themselves; teachers require a persisted class roster; GUIDANCE requires the student's persisted guidance assignment; administrators have same-school access. Forged request school/email/ID/authorities are not proof of access. Missing/archived employee records fail staff verification. Student self projections exclude staff notes and spotter lists.

Spotters are subscriptions, not independent grants of student access. Teachers/guidance may change only their own subscription while an independent relationship exists. Same-school administrators may add eligible staff and remove stale subscriptions for other staff. A grant does not bypass roster/guidance authorization. Reads and dispatch recheck current relationships, so stale historical spotter entries confer no access or email copies. Existing case author/responsible-staff participation is preserved independently; roster membership alone does not newly grant another author's case-review rights.

## Complete StudentController endpoint inventory

All paths below are under `/student/v1`. Before this change all required authentication except `POST /points/transfer`. Existing controller checks on creation, email-list reads and bulk updates are retained. Service checks now protect direct service callers too.

| Method/path | Current frontend caller or purpose | Data/mutation | Previous additional checks | Resulting authorization/behavior |
|---|---|---|---|---|
| GET `/` | No active data caller | Empty response | None | Authenticated, no data |
| GET `/studentid/{studentId}` | Legacy lookup; no active direct caller found | Full student | None | Self or independently related staff; same school, active student |
| GET `/allStudents` and `/allStudents/` | Teacher/admin/guidance dashboards, referral/shout-out forms, reports, wallet selection, profile editor, Manage Spotters | Student profiles/contact/history/balances | Staff only | Current staff; active students filtered to independent relationship, admin same-school |
| GET `/archived` | Legacy archive lookup | Historical full students | None | Same-school administrator only |
| GET `/lastname/{lastName}` | Legacy lookup; no active direct caller found | Student profiles | School query only | Staff; own school plus independent relationship |
| GET `/email/{email}` | Student details modal, wallet/student self view | Student profile/balance | None | Self or independently related staff; school/archived checks; no staff notes/subscriptions in self response |
| GET `/parentEmail/{parentEmail}` | Legacy lookup; no active direct caller found | Student/contact/history | None; global lookup | Staff; results filtered to same-school independently authorized students; no parent-account role exists |
| GET `/detentionList/{school}` | Staff detention widgets | Student names plus disciplinary cases | Caller-supplied school | Staff; persisted school must match; per-student relationship filtering |
| GET `/issList/{school}` | Staff ISS widgets | Student names plus disciplinary cases | Caller-supplied school | Same as detention |
| GET `/findBySpotter/{spotterEmail}` | Manage Spotters | Student profiles | None | Own verified staff identity or same-school admin; results require current actor and target relationships; stale subscriptions excluded |
| POST `/newStudent` | Desktop/mobile creation and enrollment | Account/profile creation | prepareStudentCreation | Preserved existing trusted school/staff reference validation and idempotent creation |
| POST `/addStudents` | Existing batch provisioning | Account/profile creation | Prevalidated prepareStudentCreation | Preserved; no new public permissions |
| POST `/getByEmailList` | Teacher roster, mobile class/shout-out/records, behavior panel | Student profiles | Staff/student-access check | Entire list must be authorized before results are returned; service also checks |
| POST `/{studentEmail}/add-time` | DetentionTimer | Detention time bank | None | Independently authorized staff; positive bounded time; compare-and-set protects simultaneous edits (409) |
| PUT `/updateStudents` | EditStudentPanel | Student profile | Staff/object checks, currency/school/email blocked | Entire batch prevalidated; profile field allowlist; revision CAS; no points, spotter, history, archive or time overwrite; guidance/admin relationship changes require admin and verified same-school role |
| PUT `/notes/{id}` | NotesComponent | Student staff notes | None | Independently authorized staff; server author/date, bounded content, atomic append |
| PUT `/addAsSpotter` | ManageSpottersPopup, teacher student panel, admin teacher profile | Subscription relationship | None | Self only with independent roster/guidance relationship; admin may manage eligible same-school staff; targeted add-to-set |
| PUT `/removeAsSpotter` | ManageSpottersPopup | Subscription relationship | None | Same self policy; admin may remove stale same-school grants; targeted pull |
| PUT `/remove-spotter/{email}` | Admin TeacherProfileSpotter widget | Subscription removal | None | Same centralized policy; student ID resolved from persistence, request profile ignored |
| PUT `/archived/{studentId}` | Legacy retirement; no active direct caller found | Archive metadata/account deactivation | None | Same-school admin; revoke account/tickets first using existing deactivation, then targeted archive; retain profile, case/roster references and balances |
| DELETE `/delete` | No active caller found | Destructive student deletion | None | Retired: authenticated requests return 410; use admin archival |
| PUT `/assignSchool` | No active caller found; actually global phone formatting | Global student writes | None | Retired: authenticated requests return 410; any future maintenance requires separately reviewed scoped procedure |
| POST `/points/add` | No active caller found | Legacy points balance | None | Retired: 410 before persistence |
| POST `/points/delete` | No active caller found | Legacy points balance | None | Retired: 410 before persistence |
| POST `/points/transfer` | No active frontend/backend workflow caller found | Two-document legacy points transfer | Public, non-atomic | Public exception removed; authenticated valid requests return 410; invalid payloads can return 400; no writes |
| GET `/enrollment-directory` (new) | Desktop ClassUpdate and mobile teacher enrollment | ID, account identifier, first/last name only | N/A | Verified staff, active same-school directory; no contacts, notes, balances, case history or spotters |

The guidance dashboard uses the trailing-slash `/allStudents/` spelling; it explicitly shares the same secured handler. No other additional legacy controller aliases were found. Controller-method aliases (`getStudentByEmail` overloaded; `archivedDeleted`; `updateGuidance`) do not create additional routes. Service-only `findAllStudentArchived`, `findBySchool`, `findByArchivedAndSchool` and `findByLastNameAndSchool` now apply the same school/role restrictions. Related DTO routes retain prior WorkflowAccess checks; PunishmentDtoService filters before looking up students, so an unrelated case cannot break or leak through the authorized overview.

## Frontend compatibility

- Existing-student enrollment intentionally needs a directory of not-yet-enrolled same-school students. Desktop ClassUpdate and MobileTeacherAddStudent use the minimized directory instead of requesting unrelated full records. Existing guarded roster-update APIs and revisions remain unchanged.
- Manage Spotters uses only scoped authorized students; self IDs remain in the compatibility payload but are verified against the authenticated account. Admin addition options require independently eligible target staff/student relationships. Admin removal can clean up historical stale entries.
- Profile editing sends only allowed profile fields and the saved profile revision. Staff assignment changes are administrator-only. A 403/409 keeps input and explains that records must be refreshed; no automatic stale retry. Successful responses must confirm the revised profile/subscriptions. Duplicate submission guards apply to updated controls.
- Other full-profile callers receive only authorized students. If a school's historical teaching relationships exist only as spotters, staff must establish actual roster/guidance assignments using existing authorized workflows. Spotters alone no longer authorize full student access.
- Historical legacy points remain displayed where the existing UI reads them. No currency mapping, reconciliation or balance changes occur.

## Persistence, compatibility and failure semantics

No bulk data migration or new index is required. `profileRevision` is an additive primitive long: absent legacy values read as zero, and the first targeted update matches missing/zero explicitly then increments. Stale revisions or concurrent changes return 409. Updated clients must retain the returned revision; an old client lacking revision safely conflicts after the first edit rather than overwriting a newer edit.

Profile updates set only allowlisted fields; notes use `$push`; spotters use `$addToSet`/`$pullAll`; time edits compare the prior bank. The punishment phone-log path now atomically appends notes instead of saving a stale Student. No remaining production StudentRepository.save call sites were found. Existing currency-safe repository protections remain intact.

Multi-student profile/spotter batches prevalidate all authorization before writes, but are not multi-document transactions. A later database failure may leave earlier independently valid changes saved. The UI reports unconfirmed outcomes, retains input, and requires refresh. Spotter set operations are idempotent; profile revisions reject blind batch retries. Notes/time are not new financial APIs or exactly-once operations; after an ambiguous network failure inspect saved history/time before manually retrying. No automatic replay is introduced.

Archival deliberately disables the authoritative account and revokes setup tickets before changing student metadata. A later archive failure can leave a disabled, not-yet-archived profile: retry archival; never automatically reactivate it. No history, wallet values, OAuth records or class references are removed. Existing activation rejects archived student profiles. An already-issued email accepted by a provider cannot be recalled; dispatch excludes stale spotter recipients before any new send.

Rollback: do not restore anonymous points, spotter-only authority or destructive deletion. Prefer a forward compatibility fix; additive profile revisions and retained historical records need no data rollback. Any emergency deployment rollback needs explicit security review.

## Verification and remaining scope

StudentApiSecurityTest and its EmailEnabled subclass use the real production SecurityConfig/JwtFilterRequest, MockMvc with springSecurity(), real authorization/student services and mocked persistence/providers. They cover anonymous reads, self/unrelated student, related/unrelated teacher, same/cross-school admin, assigned/revoked guidance, disabled/archived/pending actors, forged authorities and IDs, spotter self/other management, profile protection/conflicts, archival deactivation ordering, retired writes, and communication revocation. Unit regressions exercise atomic update shapes, retry-safe subscriptions, time CAS, history preservation, legacy revision compatibility and concurrent rejected points requests. Authorization results are identical with email gates enabled/disabled.

No application startup, real student data, real points/currency transactions, emails, Google authorization or deployed database verification was performed. Database-backed integration tests are opt-in and remain skipped without an explicitly isolated URI. Tests of update shape and mocked races do not establish real MongoDB concurrency behavior.

Separate remaining security work from the readiness audit: guidance/tracked-behavior and other non-StudentController legacy APIs still need their own endpoint review; tenant identity still relies on established school-name semantics. This PR is not a claim that all application authorization issues are resolved. Browser-level desktop/mobile smoke testing against synthetic local accounts remains a reviewer check.

### Local verification results

- Offline Java 17 relevant regression suite: 987 discovered, 964 passed, 23 opt-in MongoDB currency integration tests skipped; zero failures/errors.
- Final compatibility run after adding the guidance trailing-slash alias: 98 passed (includes the 38 student security-chain cases, controller mapping, legacy password staff identity and CORS regressions). Counts overlap the larger suite.
- Frontend: 27 Jest suites, 204 tests passed; TypeScript `--noEmit --incremental false` passed.
- Git whitespace checks passed in both repositories. No new environment variables, secrets, services or database migrations.
- The tests run without network-backed providers or database connections; live browser verification was not run.
