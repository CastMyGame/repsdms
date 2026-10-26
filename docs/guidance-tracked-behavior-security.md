# Guidance and tracked-behavior authorization

Reviewed from merged dev: backend `3869f597783613d5b89029486bdfb7857d4fa956`, frontend `8bd29217d1b6688b08d0882450353b79912fe092`. Both include the prior student-security PRs. No deployed records were read or changed.

## Confirmed vulnerabilities

- SecurityConfig authenticated these routes, but GuidanceService.findAll/findByStatus returned globally queried guidance notes without school/participant checks. Notes, status and follow-up mutations loaded arbitrary IDs; note authors and creation authors came from request fields. Resource handling depended on a broader case/notification policy rather than current counselor authorization. The old delete delegated to destructive removal if no notification obligation existed.
- TrackedBehaviorServiceImpl accepted teacherEmail and school from requests, queried timelines/totals by arbitrary student identifiers, and accepted an arbitrary school/student list for batch totals. It did not check persisted roles, school membership, current roster/guidance relationships or student archival. Invalid null items were silently skipped.
- Active frontend guidance URLs used nonexistent `/punish/v1/guidance/...` routes. The guidance dashboard's default overview queried a commented-out alias. Its Complete action addressed punishment closure using a guidance ID. The teacher disciplinary form attempted a separate guidance call even though the backend already links guidance during disciplinary creation.

## Authorization matrix

Every role and school below is loaded from persisted authenticated identities. A UI role/teacher/school field is never authority. Enabled/setup-complete accounts and active matching Employee records are required for staff. Existing spotters confer no independent permission.

| Actor | Guidance list/read and notes | Create guidance | Guidance status/follow-up/resources/archive | Tracked history/totals | Record tracked behavior |
|---|---|---|---|---|---|
| Anonymous / missing, disabled, pending-setup or archived staff | Denied | Denied | Denied | Denied | Denied |
| Active student | Staff notes/lists denied | Own request only; no staff impersonation | Denied | Own active student record only | Denied |
| Teacher | Own authored case AND current independent roster relationship; may append notes | Current independently authorized student only | Denied unless another persisted qualifying role authorizes management | Current roster students | Current roster students |
| GUIDANCE | Current assigned counselor; old stored counselor identity is insufficient | Current independently authorized student only | Current assigned counselor | Currently assigned students | Currently assigned students |
| ADMIN | Same-school records, including historical student cases | Same-school active student | Same-school active student and unarchived guidance record | Same-school history, including archived students | Same-school active students |
| Cross-school / unrelated / spotter-only staff | Denied | Denied | Denied | Denied | Denied |

All guidance participants may read retained archive history when otherwise authorized; archive does not authorize new access. Archived guidance is read-only. A role change or roster/counselor revocation removes access on subsequent reads/mutations/sends. Assigned guidance is checked against the current Student record, not solely the referral's historical guidanceEmail/responsibleStaffEmail. Teachers require case authorship in addition to current independent student access; being another teacher of the same student is insufficient for counseling notes. Multi-role staff receive only their persisted role permissions.

Student guidance creation returns a fresh request receipt containing the student's own supplied description and trusted routing metadata, not existing staff notes. It does not add a student-facing counseling-notes portal or generate an email. Requests without an assigned counselor require same-school administrator triage.

## Complete endpoint inventory

Before this PR every route below required only generic authentication in SecurityConfig (resource/deletion paths also used existing notification checks). There were no anonymous matchers for these namespaces. Real filter-chain coverage retains 401 for anonymous calls and 403 for persisted authorization failures.

| Method/path | Existing callers | Data/action | Corrected policy |
|---|---|---|---|
| GET `/guidance/v1/referrals` and `/referrals/` | GuidanceDashboard overview/all and parent-contact lists | Guidance descriptions, staff notes, routing and status | Query persisted own school, then current authorized participants only |
| GET `/guidance/v1/guidanceStatus/{status}` | GuidanceDashboard status tabs | Same guidance data | Same participant filter; OPEN/DORMANT/CLOSED accepted; ALL uses referrals route |
| POST `/guidance/v1/guidance/new` | ContactUsModal guidance request; GuidanceRequestModal from student details | New referral | Student self-request or independently related staff; server-derived actor/school/counselor; new ID/time/status; supplied history/IDs ignored; linked punishment authorized and matched to same student/school |
| PUT `/guidance/v1/guidance/notes/{id}` | Shared NotesComponent on guidance overview | Append staff note | Current participant; server author/date; bounded valid content; no arbitrary ID or creator trust |
| PUT `/guidance/v1/guidance/followup/{id}` | SchedulerComponent | Follow-up date/status and event | Assigned current counselor/admin; strict date parsing and valid status; record must remain active |
| PUT `/guidance/v1/guidance/status/{id}` | GuidanceDashboard Complete/Restore | Status/closure date and event | Assigned current counselor/admin; OPEN/DORMANT/CLOSED; no mutation of archive |
| PUT `/guidance/v1/guidance/resources/{id}` | SendResourcesComponent | Resource history and notification intent/legacy delivery | Assigned current counselor/admin; current participant recipients; save history/intent before legacy transport; existing managed idempotency retained |
| DELETE `/guidance/v1/guidance/delete/{id}` | GuidanceDashboard archive action (previous frontend URL was invalid) | Historically destructive deletion | Assigned current counselor/admin; now soft archive, close and retain history/obligations; no Mongo remove |
| GET `/tracked-behaviors/v1/types` | TrackedBehaviorPanel | Static behavior/consequence catalog | Valid persisted student self account or verified staff; no student history returned |
| POST `/tracked-behaviors/v1/save` | TrackedBehaviorPanel used by teacher/admin screens | Append one/multiple events | Verified staff, current independent relationship and school, active student; whole batch prevalidation; server actor/school/names; reject malformed and duplicate same-event items |
| GET `/tracked-behaviors/v1/student/{studentEmail}/timeline` | TeacherStudentPanel details/history shared across staff contexts | Behavior events | Self/current independently related staff/admin; filter historical event school and requested student |
| GET `/tracked-behaviors/v1/student/{studentEmail}/totals` | TeacherStudentPanel counters | Aggregated behavior history | Same as timeline; mismatched or schoolless historical events excluded |
| POST `/tracked-behaviors/v1/students/totals` | No current frontend caller found; supported API | Multi-student totals | Authorize every requested student before query; derive school from persisted actor; reject forged school; filter returned events to requested authorized students |

Spring-generated HEAD/OPTIONS do not create new data permissions. Local preflight uses the existing explicit CORS policy; the GuidanceController localhost origin's trailing slash is corrected. No permissive wildcard or security matcher expansion is introduced.

Service-only guidance entry points (`LinkAssignmentToGuidance`, `createNewGuidanceForm`, `getLoggedInUserGuidanceReferrals`, `getAllGuidanceReferrals`) use the same policy. The linked-guidance precondition is checked in PunishmentCreationService before any disciplinary persistence, then the saved punishment is validated when binding guidance. A caller-provided logged-in email must match authentication; the assigned-only helper checks the current persisted Student guidance assignment.

Related notification status/retry/capture/dispatch authorization now applies current guidance-participant policy. Dispatch also filters CC/BCC so revoked counselors, unrelated roster teachers and old spotters do not retain disclosure through notification recipients. Existing Google/Gmail/SMTP routing, action IDs and no-fallback protections are preserved.

Obsolete `/punish/v1/guidance/{status}/{userFilter}` is commented out; `/punish/v1/guidance/new`, `/formList`, `/notes`, `/followup` and `/resources` have no active backend mappings. No new permissive aliases are added. Active frontend callers now use actual guidance routes. The redundant disciplinary-form guidance call is removed because the backend already creates the linked referral. No dedicated mobile guidance/tracked API callers were found; shared responsive components retain their layouts.

## Compatibility and persistence

No bulk migration, database reset, new index, dependency, secret or environment variable. Guidance gains additive `requestedByEmail`, `archived`, `archivedBy`, and `archivedOn`; absent legacy archive state is false. Student requester identity is never written as a staff teacher identity. Existing descriptions, notes, case links, behavior events, balances and notification intents remain intact. Archive remains visible in closed/all history to authorized staff; it is immutable rather than deleted. Existing case version checks still detect conflicting saves.

Frontend uses canonical routes, checks returned records before success, preserves input on errors, reports 401/403/400/409/unconfirmed results, and guards repeat clicks. Tracked requests omit teacher and school authority fields; names are canonicalized server-side. Student guidance requests use their signed-in identifier and do not require a personal mailbox. Public contact email behavior is unchanged.

Legacy records with missing/mismatched school or student metadata are hidden rather than assigned a guessed tenant or rewritten. Historical forged authorship cannot be certified retroactively; no history is reconciled automatically. Current independent relationships plus case participation are required. Owners should review excluded records using a separately approved read-only diagnostic.

Batch tracked inserts are prevalidated but are not a database transaction or durable action receipt. A database/network failure may leave some events saved. The UI reports unconfirmed results and keeps input; inspect history before manually retrying. Duplicate items inside a batch are rejected, but an identical later HTTP request is not durably deduplicated. Rate limits, pagination, retention and exact-once behavior remain follow-ups; no unrelated infrastructure is introduced.

## Verification

Real SecurityConfig/JwtFilterRequest and real policy/services run through MockMvc with springSecurity(), with mocked repositories/providers. Both email-gate configurations cover anonymous, self/unrelated student, related/unrelated teacher, assigned/revoked counselor, same/cross-school admin, forged actor/school/IDs, stale spotters, archived/disabled/pending accounts, preserved archive history, current recipients and local CORS. Controller tests that disable filters are mapping tests only, not authorization evidence.

- Broad offline Java 17 run: 1,062 discovered, 1,039 passed, 23 opt-in real MongoDB currency integration tests skipped; no failures/errors.
- Final overlapping focused run: 144 passed, including 40 new security-chain cases and resource/creation/notification regressions after final corrections.
- Final overlapping identity/security run after rejecting blank staff membership: 74 passed (current/legacy identity and the 40 security-chain cases).
- Frontend: 29 Jest suites / 209 tests passed. TypeScript and whitespace checks run separately.
- Not run: application startup, real MongoDB persistence/concurrency, browser/touch smoke tests, deployed data inspection or real providers. No emails or real Google requests.

## Remaining security review and deployment

Deploy reviewed backend first, then frontend so canonical routes and archive semantics are available before the UI changes. Do not merge/deploy automatically. Additive metadata does not need data rollback; do not roll back to broad guidance reads or client-controlled behavior authorship to address a compatibility problem.

Separate P0 follow-up: the non-guidance disciplinary creation path still uses same-school staff checks without this PR's independent linked-guidance precondition. Its complete object-authorization policy needs a dedicated review/correction. This PR intentionally does not redesign unrelated punishment creation. School-name tenancy and other school/infraction/legacy administrative APIs also remain outside this closure; this is not an application-wide security certification.
