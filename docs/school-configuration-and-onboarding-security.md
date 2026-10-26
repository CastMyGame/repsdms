# MVP 1 school configuration and approved onboarding

## Scope, defects and routes

Reviewed backend dev `45a453c` and frontend dev `336bf41`. This is A6, not an A7 tenant-identity migration.

Before this correction, `SchoolService.editSchool` allowed any authenticated account to select a school by name and reflectively edit fields including schoolName. Public registration could persist schools before checkout. The user approved replacing that provisioning path with REPS-controlled onboarding.

| Route | Policy / callers |
| --- | --- |
| GET `/school/v1/all` | Existing public school-directory read preserved; no current direct frontend caller found |
| GET `/school/v1/search?city=&state=` | Existing public directory read preserved; former registration lookup, now unused by request-access screen |
| POST `/school/v1/newSchool` | Disabled for all application users, including anonymous visitors and school administrators; 403 with approval-required message. Service method also denies direct/internal calls. Former caller: Register.tsx |
| PUT `/school/v1/{schoolName}` | Enabled same-school administrator verified from persisted UserModel + Employee; request/HTTP role claims do not grant authority. No existing frontend settings caller found |
| Other POST/PUT/PATCH/DELETE paths under `/school/v1` | No supported provisioning, deletion, retirement, bulk or migration aliases; authenticated requests intentionally rejected by fallback handler (403); anonymous requests remain subject to chain authentication |

`PUT /employees/v1/{school}` is a separately secured same-school administrator currency-budget operation, not school creation/editing. Punishment `/updates`, `/descriptions`, `/emails`, `/schoolName`, `/infractionName` and `/infractionLevel` maintenance routes remain denied by existing policy. No other SchoolRepository write path was found.

## Editable fields and authorization

Only `currency` (display label, not any wallet balance) and `maxPunishLevel` can be edited. Currency must contain 1–80 printable characters; maxPunishLevel must be a whole number from 1 through 4, matching the application's supported four-level escalation. Existing stored values are not automatically normalized or rewritten. All keys and values are validated before any write; unsupported fields reject the entire request with 400.

School ID, school name, normalizedKey and location (city/state/ZIP) remain maintenance-only identity metadata. Arbitrary fields, forged school/role parameters, nested update operators and reflection are rejected. Location is not changed independently of its normalized identity key. Teachers, students, guidance without ADMIN authority, cross-school administrators, disabled/archived/missing staff and password-setup-pending accounts are denied (403; anonymous edit requests 401).

Updates use findAndModify with `$set` of allowed fields, constrained by the trusted stored `_id` and schoolName. No stale whole-document save, upsert, school transfer, currency balance mutation or case rewrite. Missing records are not created; concurrent disappearance/change returns 409. Ambiguous name lookup fails closed with 409 requiring REPS review.

## Frontend and billing compatibility

Both `/register` and `/sign-up` continue to resolve to Register. They now explain REPS approval and link to the existing ContactUsModal (General Inquiry) and sign-in. Opening the page makes no school lookup, provisioning, account, subscription, ZIP-provider or checkout request. Submitting an inquiry uses only the existing `/contact-us` email channel. It does not persist a school, UserModel, Employee, registration intent or Stripe session. Do not include student data or credentials in inquiries.

Stripe billing services, checkout API, existing subscriptions, signed webhooks and historical registration intents are unchanged. The request-access page does not invoke checkout. The existing separately callable public Stripe checkout API remains outside this change; it is not an approval mechanism and its pending-teacher provisioning must not be used to bootstrap administrators. Further billing authorization review can occur separately; no billing redesign or new public administrator API is introduced here.

## Controlled school and first-administrator onboarding (runbook, not an executed migration)

1. Record the prospective school's request using the existing contact channel. A REPS maintainer verifies the institution, its authorized representative, the mailbox they control and the commercial approval separately. Record the approver, target environment and change ticket. Inquiry/payment alone is not approval.
2. Before any data operation, inspect existing schools and accounts using least-privilege approved maintenance access. Stop if the display name already belongs to another school; do not onboard a second tenant with that name until A7 is resolved. Do not reassign an existing account to the new school.
3. Prepare a reviewed, one-use maintenance plan scoped to exact new IDs, with dry-run validation, rollback, record counts and an environment-specific backup/recovery point. Use a controlled database maintenance session or separately reviewed one-time migration, not disabled application APIs or a newly invented operator role. This PR provides no bootstrap endpoint or automatic script.
4. For an approved new school, create exactly one School using a new UUID, the approved name, currency label, supported maxPunishLevel and validated city/state/ZIP. Generate normalizedKey with the existing normalization (lowercase name/city, punctuation replaced by spaces, whitespace collapsed, uppercase two-letter state, five-digit ZIP). Check normalizedKey and display-name collisions; do not overwrite an existing document or auto-create indexes/migrate tenants during this process.
5. Establish the approved first administrator through that same separately reviewed maintenance operation: create matching UserModel and active Employee records for the verified representative, with identical school membership and synchronized established ADMIN role objects. Populate required IDs/names, set enabled=true and passwordSetupRequired=true, and use PasswordEncoder on a cryptographically random unusable password. Never choose/email/log an initial password, copy another account, or copy Stripe billing identifiers. Record only non-secret IDs and approval evidence in the change ticket.
6. Have the intended administrator use the existing Forgot password flow for their verified mailbox, or existing verified Google sign-in. Existing setup tokens are single-use, environment-bound and expiring; disabled accounts cannot recover access. No manually constructed reusable setup token. Confirm completed setup and persisted administrator membership before school use. Invitations/emails during actual onboarding require the separately approved operational action; none are sent by this PR's tests.
7. Verify school configuration, administrator sign-in and normal invitation of staff. Configure billing separately under the approved commercial arrangement using the existing billing process. Do not create subscriptions simply because an inquiry arrived.
8. Retain approval, maintainer identity, reviewed maintenance version, exact affected IDs, timestamps, sanitized before/after evidence and verification. If partially provisioned, disable only the newly created account pending review; do not delete historical records or touch existing subscriptions. A rollback must be scoped to those new IDs and reviewed for any new references before deleting anything.

## A7 and verification limits

Membership still uses school display names. An ambiguous lookup is rejected, but existing accounts cannot distinguish two tenant IDs sharing a name. This PR does not claim to solve A7; school identity migration and existing collision review remain separate pilot work.

Tests use the real Spring Security chain, SchoolController, SchoolService, WorkflowAccessService and UserContextService with synthetic identities and mocked MongoDB/providers. They verify every listed role, forged roles/school fields, disabled/archived actors, allowlist validation, targeted writes, stale updates, denied maintenance/provisioning and directory compatibility with email flags on/off. Broader security, case-creation, roster, assignment, email and Stripe regressions are also run. No deployed records, cloud services or real email are used. Real Mongo currency tests remain opt-in and are reported as skipped when no isolated URI is supplied.

Deploy the request-access frontend first (or together with backend) so the old create/pay wizard is not offered. Then deploy the school backend restriction. Catalog restriction is an independent backend companion PR. No schema, secret, environment-variable or feature-flag change; no database migration is run.

Verification for this branch: Java 17 offline regressions discovered 1,168 tests: 1,145 passed, zero failures/errors, 23 opt-in Mongo currency tests skipped. This includes 20 new security-chain tests (10 per email-gate configuration). Frontend companion: 212 tests / 30 suites passed; TypeScript passed. Git whitespace checks passed.
