# JWT signing and session reliability

## Confirmed causes

`JwtUtils` previously generated a new random HS256 key per bean/process, so restarts and App Engine instances could not verify one another's tokens. `app.yaml` permits five instances. Password and Google login both call this service. No existing JWT signing configuration was found in the checked-in dev/prod configuration; deployed Secret Manager contents were not accessed.

The access token lifetime remains 20 minutes. `/v1/renew-token` accepts only an unexpired, unrevoked token and replaces it; there is no separate long-lived refresh token. The old process-local blacklist could not revoke tokens across instances/restarts. Tokens renewed within the same second could be identical to their predecessors. The frontend treated 403 as expiry and tried renewal only after expiration. The JWT filter also caught downstream exceptions as authentication failures.

## Configuration: mandatory before backend deployment

| Setting | Value/source |
| --- | --- |
| `reps.auth.jwt.signing-key` | Canonical standard Base64 encoding of exactly 32 cryptographically random bytes. No whitespace, URL-safe substitutions or generated runtime fallback. |
| `REPS_JWT_SIGNING_KEY` | Explicit backend/local override. Never put its value in app.yaml, Git, browser variables, command arguments or logs. |
| `REPS_JWT_SIGNING_KEY_VERSION` | Positive numeric Secret Manager version; defaults to `1`. Never `latest`. Maps through `reps.auth.jwt.signing-key-version`. |
| dev fallback | `${sm@reps-dev-jwt-signing-key/${reps.auth.jwt.signing-key-version:1}}` |
| prod fallback | `${sm@reps-prod-jwt-signing-key/${reps.auth.jwt.signing-key-version:1}}` |
| frontend | No new environment variables. The signing key belongs only on the backend. |

The installed Spring Cloud GCP 7.3.0 resolver was inspected locally: `sm@secret/version` is supported. The existing `spring.config.import=sm@` is reused. Local override resolution is tested without consulting the Secret Manager fallback. Each App Engine version must use the same fixed key version across all its instances; publishing a newer secret version must not change the key of autoscaled instances.

Owner procedure (not executed by this PR):

1. In project `repsdev31`, inspect metadata for `reps-dev-jwt-signing-key`. If it already exists, coordinate its use and identify a valid enabled numeric version; do not overwrite or rotate it blindly.
2. If absent, use the approved secure secret-creation workflow to generate **32 fresh random bytes**, encode as one standard Base64 line, and create the dedicated secret. Its initial version should be 1. Do not reuse Gmail encryption, SMTP, OAuth, production or local keys. Do not paste the key into chat or put it in CLI arguments. A CLI uploader must write the value to redirected stdin without a newline; an ordinary PowerShell text pipeline adds a newline and is unsuitable.
3. Grant the actual App Engine runtime identity Secret Accessor on this secret only. The existing dev identity is `repsdev31@appspot.gserviceaccount.com`. Manual command, if needed:

   ```powershell
   gcloud secrets add-iam-policy-binding reps-dev-jwt-signing-key --project=repsdev31 --member="serviceAccount:repsdev31@appspot.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
   ```

4. Confirm enabled version metadata and IAM without reading/printing the value:

   ```powershell
   gcloud secrets versions list reps-dev-jwt-signing-key --project=repsdev31 --format="table(name,state)"
   gcloud secrets get-iam-policy reps-dev-jwt-signing-key --project=repsdev31 --format=json
   ```

5. If the selected version is not 1, set `REPS_JWT_SIGNING_KEY_VERSION` to that number in the reviewed deployment configuration. Ensure no stale `REPS_JWT_SIGNING_KEY` override shadows it. Missing/malformed values prevent backend startup with a sanitized message.
6. Future production must provision its own distinct secret and IAM in its intended project. No production values or identities are assumed here.

## Local development (PowerShell 7 on Windows)

Use a distinct persistent local key, independently of the Gmail encryption key. The following commands are owner-run instructions, not part of application startup or an automatically executed script.

Create once; refuses to replace an existing file:

```powershell
Add-Type -AssemblyName System.Security
$jwtKeyFile = Join-Path $env:LOCALAPPDATA 'REPS\jwt-local-signing-key.dpapi'
if (Test-Path -LiteralPath $jwtKeyFile) { throw 'Existing local signing key preserved; use the recovery block.' }
[IO.Directory]::CreateDirectory((Split-Path -Parent $jwtKeyFile)) | Out-Null
$jwtRandom = New-Object byte[] 32
$jwtRng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtRng.GetBytes($jwtRandom)
    $jwtPlain = [Text.Encoding]::UTF8.GetBytes([Convert]::ToBase64String($jwtRandom))
    try {
        $jwtProtected = [Security.Cryptography.ProtectedData]::Protect($jwtPlain, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        [IO.File]::WriteAllBytes($jwtKeyFile, $jwtProtected)
    } finally { [Array]::Clear($jwtPlain, 0, $jwtPlain.Length) }
} finally { $jwtRng.Dispose(); [Array]::Clear($jwtRandom, 0, $jwtRandom.Length) }
```

Recover under the same Windows account into the current startup shell, without printing it:

```powershell
Add-Type -AssemblyName System.Security
$jwtKeyFile = Join-Path $env:LOCALAPPDATA 'REPS\jwt-local-signing-key.dpapi'
$jwtPlain = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($jwtKeyFile), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
try {
    $jwtLocalValue = [Text.Encoding]::UTF8.GetString($jwtPlain)
    $jwtDecoded = [Convert]::FromBase64String($jwtLocalValue)
    try {
        if ($jwtDecoded.Length -ne 32 -or [Convert]::ToBase64String($jwtDecoded) -cne $jwtLocalValue) { throw 'Invalid local signing key format' }
        $env:REPS_JWT_SIGNING_KEY = $jwtLocalValue
    } finally { [Array]::Clear($jwtDecoded, 0, $jwtDecoded.Length) }
} finally { [Array]::Clear($jwtPlain, 0, $jwtPlain.Length); $jwtLocalValue = $null }
```

Then run the established local launcher from that shell. Reload the same file after opening a new shell; do not regenerate it on each launch. The `email-local` replacement properties also require this override. No production secret is needed for JWT signing locally. Existing local MongoDB/OAuth/SMTP configuration is unchanged; these instructions do not authorize any live sends or database testing.

## Shared revocations and data compatibility

`jwt_token_revocations` is a new collection in the existing configured application database. It initializes on first logout/renewal. Every instance must share that database and have find/insert/update privileges. Only a SHA-256 digest as `_id` and an `expiresAt` BSON date are stored; no raw JWT, user, school or email payload is stored. The built-in `_id` index is sufficient for correctness and atomic renewal claims. Database unavailability fails authenticated requests closed with 503, not a fabricated expiry/401.

An operator may add this **optional retention index** after reviewing the new collection (not executed automatically):

```javascript
db.jwt_token_revocations.createIndex({expiresAt: 1}, {name: "jwt_revocation_expiry", expireAfterSeconds: 0})
```

Without it revocation still works, but expired digests accumulate. Existing user/school/student/OAuth records need no migration or rewrite. Tokens retain subject, login method, credential version and 20-minute lifetime. A fresh `jti` prevents same-second replacement collisions. Persisted roles and enabled/setup/credential-version checks remain authoritative.

## Cutover and rollback

1. Review/configure the signing secret, numeric version, IAM and shared database permissions before deploying the backend. Do not merge/deploy a backend that cannot resolve its required key.
2. Deploy the compatible frontend first: 403 preserves authentication, 401 requests sign-in, and active sessions renew before expiry. No mutation is automatically replayed. GET can retry once only when a concurrent renewal replaced its rejected token.
3. Deploy the backend with the pinned configuration, move all traffic to that version and prevent old random-key versions from continuing to serve traffic. Schedule a short authentication cutover and tell users to sign in again after it completes.
4. Old random-key sessions cannot be recovered safely and will be invalidated once. New sessions survive restart/scaling provided configuration/database remain consistent. Old in-flight requests may fail during cutover; verify saved disciplinary actions before deliberately retrying.
5. A future signing-key version change invalidates current sessions again; multi-key rotation is not implemented. Never change latest behind running instances. Rolling back to the old random-key backend reintroduces the defect and forces another sign-in. Retain the new collection and secrets; no rollback should reset school/user data.

## Verification boundaries and remaining risks

Tests use real JWT cryptography, actual Spring Security filters/controllers where authorization is asserted, mocked persistence/providers and synthetic accounts. They cover key sharing/restart, wrong/expired keys, unique same-second renewal, duplicate claims, configuration failure, disabled/changed roles, password login, existing Google-login handler coverage, 401/403 and store outage behavior, and no bearer/key output from the utility. Real MongoDB atomicity/TTL, live Secret Manager resolution/IAM and a deployed multi-instance login/browser check remain pending.

OAuth handshake/server cookie sessions remain separate from bearer JWTs; their instance affinity/shared-session requirements are not redesigned. Existing Google-login bearer tokens in redirect URLs, browser sessionStorage/XSS exposure, an absolute session lifetime, and session-only logout behavior need separate review. Renewal is still limited to valid access tokens, and a lost renewal response after durable revocation may require sign-in again. No deployed secret was created/rotated, no database was accessed or changed, and no Google/SMTP calls were performed during verification.
