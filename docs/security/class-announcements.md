# Class announcement recipient authorization

## Always-on, feature-specific boundary

`POST /email/v1/classAnnouncement` accepts the existing array of class name/period, teacher email, subject and message objects. The sender hint must match the authenticated persisted employee. Classes and recipients are loaded from that employee's current persisted rosters, never from request recipient or school fields. All requested classes/recipients are authorized before translation or the first send. Duplicate class names require an unambiguous period.

`ClassAnnouncementAccess` reuses the canonical account membership validator **only for announcements**. It has no A7 flag or verification-profile dependency. Runtime canonical authorization for other routes remains inactive and unchanged. It requires a current enabled REPS AccountPrincipal, valid credential version, matching account/employee ownership and roles, and a real School ID. Each recipient must resolve uniquely to an active student and enabled account with matching canonical profile/account/actor School IDs. Pending student password setup does not prevent receipt. Spotters, school labels, historical authorship and supplied identifiers cannot grant authority.

The sender boundary rechecks the complete recipient list against current authenticated membership and rosters before routing; a direct/background call without current authentication cannot reuse a previously authorized list. Existing managed Gmail/SMTP routing and address privacy remain unchanged under both email-flag states.

## Legacy compatibility and deployment

This fix intentionally fails closed for announcements when an account, employee or student has missing/conflicting/nonexistent ownership, missing account/profile, ambiguous email resolution, archived profile or disabled account. Unmigrated development users may therefore receive HTTP 403 for announcements. Other login, roster, case, assignment and notification workflows are not switched to canonical authorization by this fix.

Do not infer ownership from school names, populate IDs at startup or disable this check to restore announcements. Owners must separately review account/profile/School identity evidence and authorize any compatibility remediation under the A7 migration process. No database inspection, backfill, key/grant migration or automatic record repair is included here. Preserve all existing records and delivery history. No environment variables, secrets, indexes or schema migration are introduced.

Deploy the backend fix independently of frontend/A7 activation. The existing frontend payload remains compatible and preserves form input on errors. Its generic delivery-failure message does not yet distinguish authorization rejection from transport uncertainty; operators should use HTTP 403 as a denial, not retry blindly. Never restore the name-authorizing code to re-enable legacy announcements.

## Dispatch, retries and remaining limitations

Announcements are synchronous: controller -> EmailService batch -> EmailNotificationService -> EmailSenderService -> existing routing. No announcement receipt, intent, outbox or background queue exists. The durable RequiredNotificationService dispatcher scans disciplinary case obligations, not announcements. Rejected requests persist no notification or case and cannot be recovered by that worker. Every HTTP retry reruns authorization; a detached sender/worker is denied.

Whole-batch authorization is not atomic email delivery. Provider failures may leave **authorized** recipients partially contacted, and successful request replay is not idempotent. Do not automatically retry uncertain delivery. This PR does not build a new announcement queue or alter disciplinary notification retry behavior. External provider acceptance cannot be made atomic with roster revocation: changes after the final persisted check cannot recall an accepted message. Rechecks narrow that race; no claim of linearizable database/provider delivery is made.

## Verification

`EmailCommunicationSecurityTest` uses a real local MongoDB replica set, JWT/filter chain, repositories, canonical membership validator, production controller/orchestration/sender and mock providers, with A7 enforcement inactive. It requires `REPS_TENANCY_TEST_MONGODB_URI` containing a loopback-only, credential-free URI without an existing database name, and creates/drops a random `reps_announcement_test_*` database. Use the disposable replica-set setup in `docs/currency-transactions.md` and URI instructions in `docs/tenancy/account-membership.md`; never point this test at a shared database.

Run Java 17 offline Maven with the test URI set:

```powershell
mvn -o -B '-Dtest=EmailCommunicationSecurityTest,EmailControllerTest,EmailNotificationServiceTest,EmailSenderServiceTest,EmailServiceTest' test
mvn -o -B test
git diff --check
```

No real mail, Google authorization, payment calls or deployed data are required. The filter-disabled controller slice tests verify serialization/delegation only; authorization evidence comes from the real security-chain/Mongo test.
