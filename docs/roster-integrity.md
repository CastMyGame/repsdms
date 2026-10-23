# Class roster and student enrollment integrity

## Storage and identity

Classes remain embedded in `employee.classes`; rosters remain lists of student identifiers. Class identity is the trimmed, case-normalized **(className, classPeriod)** pair, not the name alone. Existing names, periods and IDs are retained. Duplicate legacy pairs return 409 for explicit review; no record is silently merged. Historical punishment counts only carry a period, so same-period classes still share that period's calculated count.

`employee.rosterRevision` is additive. Missing means zero. The first successful write sets revision 1. No data migration or new index is required. Requests to update/delete a class must include `expectedRosterRevision` from the latest employee/overview response. Creation also requires `createOnly: true`; editing requires false. Editing an absent class returns 404. Creating an existing pair returns 409. Empty rosters are supported.

The conditional MongoDB update matches employee ID, school, active state and revision; it sets only classes and increments revision. Concurrent edits to any classes on one teacher conflict rather than overwriting one another. Reload and deliberately reapply edits. Dashboard counts are response-only and never saved. Currency updates no longer save an employee snapshot over rosters.

## Provisioning and retries

New students and their users use deterministic `student-<UUID derived from normalized identifier>` IDs in their respective collections. These are identity keys, not passwords or access credentials. Existing IDs are never rewritten. Provisioning uses insert, never replacement save; concurrent identical requests resolve the winner. Google missing-profile repair and administrative account provisioning use the same ID scheme.

Student creation requires persisted, verified staff and derives the school from that identity. Conflicting supplied schools are forbidden. Caller IDs, balances, archive state, notes and spotter relationships cannot be injected through creation. Guidance/admin references are accepted only after validating persisted enabled staff, role and school membership. Matching existing student profiles are returned without modification. Conflicting/archived profiles return 409. An account-only retry may resume only the matching deterministic student account, never a legacy/staff/foreign/disabled account. Passwords, roles, school, activation and credential version are not overwritten by retries.

Account insert, profile insert and class enrollment remain separate writes. There is no cross-document transaction. A failure can leave an account awaiting its profile, or a student awaiting enrollment. Retry the same details to finish profile creation; after roster conflict reload classes and retry enrollment/select the existing student. A network timeout can leave enrollment already saved: check the roster before retrying. Never delete a student to recover a roster operation. Removal affects only the selected class; moving a student between classes is two explicit roster operations, not an atomic transfer. Enroll and verify the destination first, then remove the source; if the second request fails, retain the source until a deliberate retry.

Legacy account/profile inconsistencies require administrator review rather than automatic adoption. Existing duplicate accounts/profiles are not repaired by this change. Global cross-role username uniqueness requires a separate legacy-duplicate audit and strategy; deterministic IDs guard the supported student provisioning paths, not simultaneous unrelated staff provisioning. Other student profile editing endpoints remain separate security/data-integrity work.

## Deployment and rollback

Deploy backend first and frontend immediately afterward in a coordinated roster-write maintenance window. Require browser refresh. Older clients omit revision/create intent and are deliberately rejected instead of silently overwriting rosters. The new frontend must not be used with the old name-only backend. No new secrets, environment settings, feature flags, dependencies or production database commands are needed; authorization applies with Email MVP flags off or on.

Rollback both application versions together with roster writes paused. Retain additive revisions and new deterministic IDs; old reads support arbitrary string IDs, but old write behavior restores the original lost-update defects. Do not remove fields, rewrite IDs, reset databases or replay full employee snapshots.

## Verification boundaries

Focused service tests exercise stale GET/read projections, overlapping writes, duplicate identities, empty/removal rosters, legacy missing revisions, retry after profile insert failure and duplicate account inserts. Real Spring Security filter-chain tests cover anonymous/student denial, own staff access, same-school administrator access, forged authorities, foreign-school/archived student rejection and HTTP 409, with Email MVP flags enabled and disabled. MongoTemplate/repositories and transports are mocked; a disposable real MongoDB race/restart check and manual desktop/mobile browser checks remain pending. No deployed database or real email service is used by these tests.
