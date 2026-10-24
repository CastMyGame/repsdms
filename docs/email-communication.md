# Email content and communication reliability

## Confirmed defects and corrections

- `EmailTemplateBuilderService.createCFRMessage` omitted the `description` token present in both CFR templates. It now requires the persisted description. The legacy CFR orchestration also only constructed a response; it now invokes the existing notification service once.
- `RequiredNotificationService.render` sent every ordinary CREATED event through disciplinary copy, including positive recognition and academic/behavioral concerns. Office creation used escalation instructions instead of a principal-handled notification, and office closure claimed assignment completion. Event/type-specific copy now reflects the saved action.
- Managed submission/reminder text was English-only; Spanish locale variants were not normalized consistently. English/Spanish framing now follows the persisted student preference, including `es-MX`/`es_US`. Guidance resources also have localized framing.
- Managed values were escaped before translation while legacy template values were not escaped. Rendering now translates raw narrative when appropriate and escapes inserted HTML values once. Substitution processes template tokens only; user-entered braces cannot become another template variable. Required missing values/unknown tokens fail rather than emit incomplete messages. Translation failures retain the original narrative in the selected-language frame and log no provider payload.
- Template links could become the non-URL phrase “your school REPS login page.” Account and communication links now share the existing origin validation and fail explicitly if missing/unsafe. Obsolete password/login-information wording and unsupported hardcoded deadline promises were removed.
- Class announcements matched name without period and exposed other recipients through legacy CC. They now require an unambiguous name/period, validate staff and roster school relationships independently of email gates, escape plain-text messages, and send individual messages.
- Frontend resource, follow-up, announcement and shout-out copy implied sending on save/request success; failed status fetches also looked like disabled notifications. These states are now distinguished.

## Outgoing-path inventory

Managed required notifications are embedded in the same saved case/action, then dispatched separately. Templates receive the retained event content, not later edits to the behavior description. Sender and recipients are revalidated against current persisted relationships at dispatch.

| Event | Trigger / actor | Recipients / identity | Content, language and links | State behavior |
| --- | --- | --- | --- | --- |
| Disciplinary creation / repeated CFR | Authorized issuing staff | Guardian To; student and eligible original/responsible/acting staff, admin/guidance CC; eligible spotters BCC | Actual behavior, level and pinned assignment availability; CFR explicitly promises no new assignment. Student language; configured login link | Managed intent retained with case. Legacy direct send uses existing SMTP/Gmail policy; corrected CFR invokes its transport |
| Office referral creation / principal closure | Verified staff / same-school administrator | Same persisted case participants; closing administrator sends and original teacher remains eligible CC | Principal handles consequences individually; no student assignment or assignment-completion claim. Closure comment retained. English/Spanish | Managed lifecycle retained; legacy office creation/closure saves records without sending email, so UI must not claim delivery |
| Positive Shout Out | Authorized staff; SO / positive event | Existing persisted guardian/student/staff participation policy | Positive subject and behavior recognition in English/Spanish; no disciplinary assignment copy | Uses existing managed or legacy path, not a new provider |
| Academic / behavioral concern | Authorized staff | Persisted guardian/student/staff participants | School-update copy and actual concern; no invented assignment | Existing managed/legacy state behavior |
| Student assignment submission | Authorized student of the case; system notification | Persisted case participants | Saved, awaiting staff review; never “completed.” English/Spanish | System SMTP; intent and assignment state share an atomic case update |
| Rejection / acceptance / closure / archive / restoration | Authorized case staff | Current actor sends; eligible original teacher retained | Required feedback/correction text, accurate state; preserved original behavior and submission history. English/Spanish | Existing retry/idempotency/claim handling retained |
| Staff invitation / password setup / reset | Trusted provisioning, administrator reissue or public non-enumerating reset request | Intended enabled account only; direct system SMTP; no CC/Gmail borrowing | Existing English security copy and configured `/set-password#token`; 24-hour, single-use ticket; no password in mail | PENDING_SEND / SENT (provider accepted) / FAILED / DELIVERY_UNCONFIRMED / CONFIGURATION_REQUIRED; account remains saved |
| Student setup / recovery | Authorized staff verifies identity and relationship, or permitted private-email reset / verified Google account | School-assisted credential handed to student, not emailed; private setup mail to confirmed account only | Existing 15-minute, five-attempt assisted credential and single-use password setup. No predictable password; no personal mailbox requirement | Existing ticket, activation and Google protections unchanged; mail setup uses the existing invitation states |
| Guidance resources | Authorized persisted case staff | Student To; eligible staff CC and spotters BCC for managed intents (legacy Phase 1 resources use staff BCC) | English/Spanish framing, preserved resource labels, validated HTTP(S) links and escaped labels/names | Managed stored resource HTML/intent; legacy direct send. Notes say “requested,” not delivered |
| Incomplete-work reminders | Existing reminder service; system actor | Current case relationships, not stored prior mail recipients | English/Spanish reminder, no new invented deadline or consequence | Existing same-day action IDs, system SMTP; only dispatcher is periodically scheduled here |
| Class announcement | Verified acting staff, own exact class/period | Individually addressed active students from the persisted same-school roster | Student language; plain-text user message escaped after translation; no classmates’ CC disclosure | Synchronous per-recipient calls, not a durable outbox. A batch can partially succeed; inspect before retrying |
| Contact/support | Contact request | Existing requester/support-copy policy | Plain-text subject/body, no disciplinary records; unresolved-template guard | Direct system SMTP, not a managed disciplinary intent |
| Follow-up date | Guidance date update | No immediate email recipients | Saves a date/status, not proof of a reminder being sent | Frontend says date saved |

Generic legacy sender wrappers and the gated Gmail proof-of-concept route remain separate from managed case intents. They are not new automatic email triggers. No new scheduler, email provider, fallback policy or public test-send endpoint is introduced.

## Recipient and transport policy

Managed dispatch requires the case/student school to match and the student to remain active. Staff participants must still be eligible in that school. Request-provided sender overrides are not trusted. The required guardian must be a single syntactically valid stored address: missing/invalid guardian data records `FAILED / REQUIRED_RECIPIENT_INVALID` before any provider call. It is not silently replaced by the student, administrator or another mailbox. A syntactically valid fictitious address can still bounce; syntax validation is not ownership or inbox verification.

Connected Gmail sends through the verified actor's authorized mailbox. No connection uses configured SMTP where policy permits. Reconnect-required/unknown Gmail remains blocked; a Gmail attempt never silently falls back to SMTP. System notifications stay on SMTP with an eligible staff Reply-To. Existing provider acceptance, partial rejection, uncertain outcomes and explicit safe-retry/manual-resolution policy remain intact.

Managed subjects contain the case reference and localized message category, not student names/behavior. The subject is recorded as additive notification metadata. Gmail thread IDs are reused only with matching subject, sender and recipient set. Older accepted intents lacking a recorded subject keep their history and RFC conversation references but do not force an incompatible Gmail thread. Provider acceptance is not inbox delivery or a read receipt.

## Configuration and compatibility

Reuse **`REPS_ACCOUNTS_PUBLIC_BASE_URL`** → **`reps.accounts.public-base-url`**. No new environment variable or secret is added.

| Environment | Expected value | Location |
| --- | --- | --- |
| Normal local development | `http://localhost:3000` (set explicitly when using the dev profile) | Backend process/IDE environment: `$env:REPS_ACCOUNTS_PUBLIC_BASE_URL='http://localhost:3000'` |
| App Engine deployed dev | `https://repsdev.vercel.app` (existing dev-profile default) | Backend App Engine environment/configuration |
| Production | Explicit production HTTPS frontend origin (existing prod default is `https://www.repsdiscipline.com`) | Separate production backend configuration |

Origins cannot contain user information, paths, query strings or fragments. HTTP is allowed only on loopback outside `prod`. Links never accept a client-provided redirect. Google login and Gmail OAuth callbacks are unchanged. No new Vercel setting, feature gate, secret rotation, migration or backfill is needed. Existing notification bodies/history are not rewritten. Old retained content with missing required values now fails visibly before sending, rather than emitting placeholders.

Before enabling a deployment, confirm its effective origin is correct. No cloud configuration was read or changed during this work. Reverting copy/UI is possible, but retain the additive subject metadata/history and avoid restoring unsafe recipient selection or raw template rendering.

## Verification and limits

Tests use synthetic records, mocked persistence/providers, real Spring Security chains, actual renderers and Spring Mongo conversion. No deployed database access, real OAuth, SMTP or Gmail sending occurs. Browser layout, real recipient acceptance/delivery and final end-to-end school acceptance remain manual checks for the later acceptance phase.

Account setup copy remains English because the account workflow has no persisted invitation-language preference; it is not claimed to have Spanish security-mail coverage. Existing Spanish student/guardian case and resource communications remain supported. Translation-provider failure keeps the original narrative, rather than inventing a translation.

Class announcements and legacy direct sends are not made durable by this change; batch failure can follow earlier accepted recipients. Managed uncertain outcomes remain blocked from blind retry. Required-notification dispatch cadence remains the existing configured poll interval. Mailbox delivery delays, bounces and provider policy cannot be proven by source/unit tests.
