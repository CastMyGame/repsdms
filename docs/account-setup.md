# Account setup and school-assisted student recovery

## Deployment and compatibility

Set `REPS_ACCOUNTS_PUBLIC_BASE_URL` to this deployment's frontend origin. Dev defaults to `https://repsdev.vercel.app`, production to `https://www.repsdiscipline.com`, and the isolated local profile to `http://localhost:3000`. Override it explicitly for normal local development. HTTPS is required except loopback outside the prod profile. Paths, credentials, query strings and fragments are rejected. No client-supplied redirect is accepted. Google login and Gmail callbacks remain unchanged.

No migration or account rewrite is performed. Missing setup fields preserve existing accounts and credential version zero. Existing passwords, roles, school membership and Google grants are retained. Only newly provisioned accounts require first setup. Outstanding *old-format* password-reset links must be requested again; the old snapshot-bearing token collection is not read, migrated or deleted. A separately approved legacy weak-password remediation campaign remains necessary.

New fields on users: passwordSetupRequired, credentialVersion, invitationStatus, invitationExpiresAt, setupCompletedAt, privateEmailSetupAllowed, and a non-serialized accessTicket containing only a credential digest, purpose, issue ID, environment, expiry and remaining attempts. Ticket expiry is enforced in the atomic query; no TTL index or database startup migration is required. A sparse digest index is an optional scaling improvement.

## Staff lifecycle

An existing authorized administrator creates the teacher using the existing endpoint. Both account and employee are saved before attempting a system SMTP invitation. The account remains stored if sending fails. The saved invitation status is PENDING_SEND, SENT (SMTP accepted, not proof of inbox delivery), FAILED, DELIVERY_UNCONFIRMED or CONFIGURATION_REQUIRED. Manage staff displays that status and can request another invitation. Email requests are limited to one new ticket per account per minute; wait a minute before retrying. Concurrent requests cannot each claim a send ticket.

The email link is valid for 24 hours. The new password must be 12 or more characters and at most 72 UTF-8 bytes. Setup atomically consumes the digest and changes only credential/setup fields; it cannot restore an old role/school snapshot or enable a disabled account. Completion is SETUP_COMPLETE. First verified Google login can establish access instead (GOOGLE_ESTABLISHED); it does not grant Gmail sending consent. Paid signups stay disabled until a school administrator approves them. Approving a newly provisioned staff account triggers its invitation. Re-enabling revokes old tickets before access is granted. There is no public first-administrator bootstrap.

## Students: no personal mailbox required

Keep each student's existing school-issued REPS username (the current studentEmail identifier); a deliverable personal mailbox is not required. Do not change identifiers on existing students to enable this workflow.

An authorized teacher or administrator opens Student access. Teachers must already have a supported relationship to the active student (class roster, assigned spotter or assigned guidance relationship), and both the student's account and profile must belong to that school. Guidance-only actors, unrelated teachers and cross-school actors cannot issue or inspect access.

1. Verify the student face-to-face using the school's identity-check procedure; do not rely on the requester merely knowing a username.
2. Confirm identity in the UI and issue a school credential. This explicitly replaces older setup credentials, revokes existing sessions, and makes the previous password unusable.
3. Privately hand the displayed 12-character credential to that student. It expires after 15 minutes and permits five guesses. Never put it in disciplinary emails, copied recipients, class chats or shared documents. The credential is shown only in the issue response, not retrievable later; hide it after handoff.
4. On a student device, open `/set-password`. Enter the school username and credential, then choose a password privately. On a shared device, sign out the staff session first. Staff must never ask for, see, or record the student's chosen password.
5. After saving the password, sign in normally. No temporary JWT or assignment access is issued by the setup endpoint. Reissue through the same identity-verification procedure if it expires or the student later forgets the password.

For private email setup, staff must explicitly verify that the student independently controls the registered mailbox. The Email setup link option records that permission and sends the same private, single-use email flow. Students without that verification use school-assisted recovery instead; they are not automatically emailed at an assumed login address. Existing Google sign-in remains available for matching approved accounts; a verified first Google login establishes access and private mailbox eligibility.

## Secret handling and operations

Email links carry the token in the URL fragment, not a query string or request path. The frontend removes the fragment and submits it only in the password-setup POST body. No raw credential is persisted in MongoDB, browser storage, application logs, or API error messages. Responses containing handoff credentials use Cache-Control: no-store. Do not enable HTTP request/response-body tracing, browser session replay on credential screens, or mail-body logging; SMTP infrastructure can necessarily see its own invitation message.

Deactivation revokes outstanding tickets and sessions. Reissue replaces the previous ticket, so a delayed earlier email may no longer work. For unconfirmed delivery, check the mailbox before requesting another email. SMTP transport errors never trigger a Gmail fallback. Invitation attempts are separate from disciplinary notification intents and do not change case delivery/idempotency behavior.

## Verification boundaries

Tests use mocked repositories/MongoTemplate/mail and real Spring Security chains. They do not start services, contact Google/SMTP, or alter a database. Real disposable-Mongo persistence/concurrency and manual viewport/device/OAuth/delivery verification remain pending before deployment. Mocked tests assert the atomic completion predicate and update, attempt decrement, expiry, environment binding, disabled guards, revocation, role/school preservation, and provider failures. No live-email verification was executed.
