# Development Email MVP activation

This change prepares configuration; it does not establish successful deployed login or email delivery.

## Confirmed deployment targets

- Frontend: `https://repsdev.vercel.app`
- Backend: `https://repsdev31.ue.r.appspot.com` (App Engine metadata and Maven deployment project)
- Runtime service account: `repsdev31@appspot.gserviceaccount.com` (active version metadata)
- Runtime profile: `dev`; never activate `email-local` in App Engine.

## Prerequisites before deployment and dispatch

1. Populate Secret Manager secret `reps-dev-gmail-encryption-key` in project `repsdev31` with the persistent Base64-encoded 32-byte key. Metadata inspection found this secret absent. Do not generate or rotate a replacement if the development database already contains grants encrypted by the local DPAPI-protected key. Retrieve the existing key privately using the established local DPAPI tooling and upload it securely; do not print it, commit it, put it in Vercel, or pass it as a command-line argument. Keep the same key across instances and redeployments.
2. Grant the runtime service account Secret Manager Secret Accessor on this secret. Its existence and access are prerequisites; they are not provisioned by this PR.
3. Existing `dev` profile uses `${sm@reps-dev-mongodb-uri}`. A local launch using that same profile/reference targets the same configured database unless overridden. The local launch's effective URI and DPAPI key were not accessed during this review, so actual database/key equivalence remains an owner verification. OAuth authorizations live in `oauth2_authorized_clients`. Compare database identity privately, never publish credentials or encrypted records.
4. Reuse `google_oauth_client_id`, `google_oauth_client_secret`, and `gmail_password`. Confirm Gmail API is enabled in the project owning that OAuth client. No client or SMTP credential replacement is needed.
5. On that OAuth web client verify Authorized redirect URIs:
   - `https://repsdev31.ue.r.appspot.com/login/oauth2/code/google` (REPS login)
   - `https://repsdev.vercel.app/account/gmail/callback` (separate Gmail consent)
   - Preserve `http://localhost:8080/login/oauth2/code/google` and `http://localhost:3000/account/gmail/callback` for local development.
   Verify JavaScript origins `https://repsdev.vercel.app` and `http://localhost:3000` where the existing client uses browser-based Google integrations. The two server authorization-code flows here do not themselves require adding the backend to JavaScript origins. Keep other existing registrations.
6. Verify Vercel `REACT_APP_API_BASE_URL=https://repsdev31.ue.r.appspot.com` in the environment serving the development domain. CRA embeds it at build time. No new frontend email variables or secrets are required.

## Prepared backend settings

`app.yaml` enables routing, Gmail connection and intent capture, sets the fixed Vercel Gmail callback and supplies the key as literal `${sm@reps-dev-gmail-encryption-key}`. Spring resolves that reference through the existing `spring.config.import=sm@`; App Engine does not perform secret interpolation. A focused test exercises the installed Secret Manager property source and converters with a mocked service client. It proves resolution mechanics, not deployed IAM or secret availability. The inspected active version had only the `dev` profile among selected redirect/profile overrides; launch arguments and future deployment overrides still need operator review.

`REPS_EMAIL_NOTIFICATIONS_DISPATCH_ENABLED` deliberately remains `false`. Missing encryption or callback configuration can fail Gmail operations; secret resolution may fail on access. Configure the secret and OAuth registrations before deploying/enabling these settings.

The `dev` Google login callback is pinned to backend HTTPS, independently of the final frontend redirect. Local callback configuration remains in the local profile. Production properties and default feature gates are unchanged. Existing application logout calls `/v1/logout` and navigates to relative `/login`; it stays on the current frontend on either API success or failure.

## Queue review and manual activation

No database was accessed and no SMTP/OAuth live test was performed during this change. Before activating dispatch, use an authorized read-only MongoDB session against the confirmed development database. For each of `Punishments`, `OfficeReferrals`, and `Guidance`, inspect counts without exposing student data:

```javascript
for (const collection of ["Punishments", "OfficeReferrals", "Guidance"]) {
  print(collection);
  printjson(db.getCollection(collection).aggregate([
    {$unwind: "$requiredNotifications"},
    {$group: {_id: {state: "$requiredNotifications.state", retrySafe: "$requiredNotifications.retrySafe"}, count: {$sum: 1}}}
  ]).toArray());
}
```

Review intended recipients and synthetic-data scope privately for every pending obligation before enabling dispatch. Existing `PENDING` intents can send immediately when dispatch is activated. Automatic dispatch does not retry `FAILED`, `RECONNECT_REQUIRED`, `UNCERTAIN` or `ACCEPTED` states. Interrupted `DISPATCHING` claims become `UNCERTAIN` after five minutes, never blindly resent. Gmail send failures do not fall back to SMTP. No automatic historical notification backfill is introduced.

After key access, OAuth registrations, pending-queue review and owner-controlled SMTP/Gmail verification succeed, change only the development dispatch flag to `true` through a reviewed configuration update. Merge/deploy and all live tests remain owner actions. Verify real browser login, failure handling, logout and Gmail Connect/Reconnect on Vercel; automated tests do not establish that the deployed flows work.

## Remaining link limitation

Account setup and communication links now use `REPS_ACCOUNTS_PUBLIC_BASE_URL`. Set it to `http://localhost:3000` for normal local testing; the dev profile defaults to `https://repsdev.vercel.app`. See `docs/email-communication.md` for validation and deployment requirements. OAuth callbacks are configured separately and are unchanged.
