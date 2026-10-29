# Historical case snapshot policy

This implements the owner's Phase 4C decision and supersedes earlier blanket statements denying former authors all historical access. Live access and every mutation continue requiring current independently persisted relationships.

Only an enabled, nonarchived, version-valid staff account with matching canonical School ID and exact immutable account/profile provenance can read its specific snapshot. No email/name/spotter/client field establishes historical authorization. Legacy cases without trustworthy provenance remain unavailable.

Fresh canonical punishment, office and guidance factories capture `historicalAccess` formatVersion 1 on the case. Each entry contains exact tenant/account/profile/case IDs, AUTHOR or initial ASSIGNEE basis, capture time, creation date, student display name, initial infraction label and initial description. Office initial principal and guidance initial counselor are validated persisted assignees. It does not grant schoolwide/student-history access. Ordinary saves cannot change or erase this immutable array. HTTP input/output ignores it.

`GET /history/v1/cases` and `GET /history/v1/cases/{kind}/{id}` return only the allowlisted View projection, with Cache-Control no-store. No current case status, assignment work, student contact details, currency, later notes, mutable descriptions, review actions or provider state is returned. List access has a fail-closed 500-snapshot result cap; pagination is future work. The companion frontend `/account/history` is a responsive read-only view with no live-case links or write actions. No new navigation entry is enabled in normal deployed workflows.

A changed roster/caseload cannot retain live case/student/review/currency/notification access through history. New notification recipients are recomputed from current relationships. Historical ownership never grants future email-copy eligibility. Existing case IDs and historical arrays/answers remain intact.

No legacy grant is backfilled here. Future reviewed historical access needs documentary evidence of immutable author/assignee account and profile IDs AND the facts legitimately visible at that time. Do not make a snapshot of today's mutable case and call it historical; if as-of facts cannot be established, deny access. A historical snapshot never authorizes a new staff assignment or reissues an email.

Rollback before adoption can remove these inactive components. After canonical activity, retain all provenance/snapshots and use a compatible build or maintenance mode. Never strip the array or recover access by matching emails. No deployed inspection, migration executor or rewrite is part of this change.
