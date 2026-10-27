# A7 coordinated cutover and compatibility prerequisites

## Proposed PR sequence

1. **Phase 1, merged PR #177:** detached schemas/validators, premature-activation guard, redacted read-only inventory, offline reviewed plans, synthetic tests and runbooks. No runtime tenant-policy change.
2. **Phase 2, partial protected-writer adoption:** Employee/Student now have HTTP-ignored tenantSchoolId metadata. BalanceSafeSave atomically preserves existing ownership and currency, rejects conflicts, and cannot assign ownership. Other mapped fields remain deferred; see writer-protection.md and writer-inventory.md. Complete full/stale saves, insert factories, DTO binding and background writers before adopting the remaining fields. Existing schoolId/schoolIdNumber fields are not overloaded. No request-DTO change that alters currency payload serialization. Add ownerAccountId to Gmail metadata with verified user binding only in later work. No backfill/startup migration.
3. **Trusted actor context and account compatibility:** derive one-school membership from persisted UserModel + Employee/Student agreement and existing School ID. Preserve roles, password/Google login and disabled/setup rules. Global unique username/email assumptions must be audited; do not introduce multi-school accounts or allow duplicate usernames via a compound index.
4. **Complete workflow ID isolation:** convert reads/writes/joins for accounts, roster, students, cases, guidance, tracked behaviors, assignments, currency, email and Stripe. Include background dispatch and exact-ID database predicates, not just controller checks. Missing/unknown/conflicting ownership denies access. No name fallback. Preparation guard must not be removed before the full coordinated release is complete.
5. **Frontend compatibility:** expose canonical identity through trusted DTOs, preserve display names and historical snapshots, and handle unmapped-account support states. Client IDs/names never establish authority. Only after ID cutover should administrator rename support be considered; no historical record cascade is required.
6. **Separately approved pilot backfill/cutover:** review inventory/provenance, conditional executor and isolated migration rehearsal. Deploy complete ID-only code, approve data operation separately, verify coverage and activate only after maintenance checks. These PRs may be developed in parallel but not partially activated against backfilled data.

## Compatibility obligations

| Area | Required preservation / future checks |
| --- | --- |
| School/actor membership | Reuse exact existing School._id; names become display/history only. IDs cannot be cleared, lowercased, renamed or moved through ordinary profile saves. |
| Accounts, login and setup | Keep existing account IDs, roles, passwords, setup history and JWT keys. Server-reloaded persisted membership is authoritative. Old setup/reset/Google sessions require ownership validation; unresolved accounts fail closed with a support path. Account transfers remain outside MVP; any future reassignment needs credential/session revocation review. |
| Classes/rosters | Keep embedded class structure, email references, IDs and revisions. Validate both ends against the parent employee's canonical tenant. Spotters remain subscriptions, not permission evidence. |
| Cases/guidance/behaviors | Preserve original ownership, record IDs/descriptions/reviews and links. Do not map historical cases from a student's current school. Unresolved historical conflicts remain inaccessible pending reviewed provenance. |
| Assignments/templates | Preserve pinned IDs, snapshots, answers, attempts and completion history. Add canonical ownership metadata alongside snapshots, without replacing work. Existing template/binding schoolId can contain names or IDs; do not reinterpret it in place. Only explicitly verified system defaults remain global. |
| Currency | Existing receipt _id hashes include the school name; request hashes reflect current DTO serialization. Keep receipt IDs/balances/amounts/case IDs unchanged. A versioned key/replay bridge must validate canonical tenant, actor and original payload before accepting an old action. Never blindly generate a new receipt for an old action after rename/cutover. Preserve Mongo transactions, retries and uncertain outcomes; no balance reconciliation. |
| Notifications | Embedded obligations inherit immutable case tenant. Preserve intent/action/message/thread IDs, provider outcomes, claims, failed/reconnect/uncertain states and retry rules. Resume only after sender/recipient/account ownership is checked by IDs; never resend due to migration. |
| Gmail grants | Current collection key and AES-GCM associated data use normalized email. Keep ciphertext, encryption key, provider subject and authorized mailbox unchanged. Add verified tenant/account metadata and check it before loading/refreshing/sending. Do not decrypt, re-encrypt, rotate keys or require reconnection merely to backfill metadata. Pending OAuth flows need drain/expiry handling at cutover. |
| Stripe | Existing registration intents already store a School ID; verify it against School/account canonical ownership. Preserve customer/subscription/event IDs and billing history. Signed webhooks/payment never grant tenant membership. Review pending intents/TTL expiration and idempotent event handling; do not change subscriptions during migration. |
| Global/legacy records | Preserve global infractions, roles, old assignments, revocations and provider event dedupe. Legacy passwordResetTokens contain old account snapshots; current setup uses account-local tickets. Do not reactivate retired credential paths or scan token values. |

## Index plan, not executed

Keep School._id uniqueness. Verify actual deployed indexes before proposing tenant-led query indexes and active-binding uniqueness. Audit duplicate emails/account identities first; current login/grants rely on global email identity. Never use display-name uniqueness as the authorization fix. NormalizedKey remains a dedupe concern, not authority. Any validator/index creation needs its own reviewed, conditional migration; this PR creates none.

## Cutover runbook — not authorized/executed by this PR

1. Obtain separate authorization for the exact read-only inventory target and separately for any writes. Complete pilot scope/provenance review; unresolved records stay preserved and denied.
2. Rehearse on an isolated MongoDB with synthetic identical-name schools, legacy data, interruption/replay and rollback. Verify runtime policies and all background paths, not just the detached tests in this PR.
3. Approve a maintenance window, backup/recovery point and exact compatible application versions. Drain requests/currency transactions, stop every old App Engine instance/background dispatcher (zero traffic alone does not prove workers stopped), and preserve retryable Stripe events rather than acknowledging unprocessed work.
4. Capture a fresh private inventory using the same audit key/target binding. Abort on unexpected counts, changed metadata, ambiguous IDs/references or stale evidence. Apply only the separately reviewed conditional pilot manifest with a future approved executor. No automatic application startup backfill.
5. Verify unchanged document IDs/totals, balances/receipt keys, snapshots/history, encrypted grants and notification states; verify all pilot references have consistent canonical ownership. Check index compatibility without silently creating indexes.
6. Deploy the FULL ID-aware release and compatible frontend. Only that future release may replace the preparation guard with a controlled ID-only startup/cutover check. No boolean that silently reverts an ID-owned database to name authorization. Unmapped records and accounts remain denied, not guessed.
7. Run same-name cross-tenant acceptance tests, controlled synthetic smoke checks and monitored request/worker recovery before reopening. Resume notification dispatch without changing retry/uncertain outcomes. Live sends need their own approval.

## Rollback

Before reopening traffic, rollback may restore the approved pre-cutover recovery point only while no new activity would be lost, under separately approved recovery procedures. After new activity, retain added IDs and use a known-good tenant-aware build, maintenance mode or a reviewed forward correction. Never roll back to name-based authorization, remove canonical fields, regenerate receipts, rotate unrelated keys or restore a stale database over new actions. A half-applied mapping must stop for a fresh inventory/review; no blind overwrite or deletion.

## Remaining gates

No live uniqueness, data counts, deployed indexes, historical provenance or billing associations have been verified. No live inventory/backfill permission exists. The real mongosh exporter has only synthetic interface tests until an approved shell/target is provided. Docker's daemon was stopped and no isolated Mongo test URI was configured during this preparation; opt-in DB checks must be reported as skipped unless independently run. A7 remains open until full runtime conversion, reviewed data coverage and coordinated cutover pass.
