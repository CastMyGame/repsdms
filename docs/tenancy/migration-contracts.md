> Phase 5C: a [local-only executor/rehearsal](local-executor.md) now validates and conditionally applies reviewed root additions on marked disposable databases. Deployed execution, protected-data adoption and canonical activation remain unapproved and unsupported.

# A7 Phase 5B — offline migration contracts v2

Baseline: merged backend #186 (`61eb924`). This document supersedes v1 generic-planner instructions. No deployed inventory, database executor, runtime activation, provider call or migration is included. All application Java, replay algorithms and feature defaults remain unchanged.

## Collection and embedded ownership inventory

| Source | Permitted plan treatment | Protected data and review obligations |
| --- | --- | --- |
| `schools` | Reference existing exact `_id`; no updates | School display names identify collisions only, never ownership. Preserve IDs. |
| `users` | Root `tenantSchoolId` only | Exact reviewed employee/student linkage and role agreement; preserve account IDs, passwords/setup tickets, credential versions, subscriptions and enabled state. Passwords/tickets are not read by the exporter. |
| `employee` / `students` | Root `tenantSchoolId` only | Validate account/profile/roster/caseload references. Preserve classes, notes, balances and identities. Spotters are subscriptions, not independent ownership evidence. |
| `Punishments`, `OfficeReferrals`, `Guidance`, `TrackedBehaviorEvents` | Root `tenantSchoolId` only | Historical ownership requires independent reviewed historical evidence, not a student's current membership. Conflicting references remain quarantined; no automatic transfer or history repair. |
| `assignment_templates`, `assignment_template_bindings` | Root `tenantSchoolId` only | Pinned references and teacher/account/school agreement. Verified system defaults stay global and cannot receive a tenant through this planner. |
| Case `assignmentWork.template`, each `submissions[].template` | **Review-only component maps** | Recognize Mongo `_id`, JSON `id`, and reject conflicts. Preserve the complete work, answers, earlier submissions, questions, feedback and snapshots. Missing templates are not substituted. |
| Case `requiredNotifications[]` | **Review-only component maps** | Bind original intent fingerprint to reviewed tenant/account/profile and historical credential version. Missing versions cannot be filled from today's account. Preserve every state, claim, attempt, outcome, provider/message/thread ID, recipient and content field. |
| Case `historicalAccess[]` | **Review-only component maps** | Require existing immutable account/profile/case/tenant provenance and supported format/basis. Never synthesize historical views from current facts. |
| `currency_operations` (v1 and v2) | **Compatibility-only root maps; zero writes** | Entire typed source record is fingerprinted. IDs, payload hashes, amounts, timestamps, actor text, wallets, case links, ownership and completed transactions stay unchanged. Unknown historical fields are included. |
| `oauth2_authorized_clients` and `gmailAuthorization` | **Compatibility-only root and component maps; zero writes** | Reviewed mailbox/account/profile provenance plus original version/protected fingerprint. Preserve normalized-email key, opaque cipher/AAD, provider subject, pending proof, scopes within encrypted payload, and state. Never decrypt, reconnect, rotate or re-encrypt to adopt ownership. |
| `registration_intents`, account Stripe associations | **Compatibility-only intent maps; review-only association components** | Require existing exact School ID and consistent account/subscription references. Preserve customer, session, subscription, price, status and timestamps/TTL evidence. Payment does not grant school membership. |
| `currency_replay_manifests` | Separately reviewed runtime artifact, **not emitted/imported by this tool** | PR #182's pinned Java/BSON manifest remains authoritative for the runtime bridge. See below. |
| Global/retired records | Excluded from all update descriptors | Infractions, roles, retired legacy assignments/points, revocations, Stripe event dedupe and retired password-reset snapshots are preserved. This tool is not permission to revive or migrate their APIs. New collections require an explicit contract, not generic updates. |

## Strict review manifest

Use `manifest.schema.json`, `reps-tenant-manifest/v2`, with v2 inventory only. V1 files are rejected rather than auto-upgraded. Regenerate inventory only after separate authorization, then re-review the mappings; changing a version string is not a conversion.

Top-level binding retains exact source/target/key/inventory digests, pilot School IDs and a nonpersonal reviewer reference. Three arrays are mandatory:

- `entries`: `recordKey`, `beforeDigest`, `protectedDigest`, exact `tenantSchoolId`, `mode` and `evidence`. Mode is `bind-root` for the nine allowed root collections and `compatibility-only` for receipts/grants/intents. A receipt additionally needs reviewed `accountKey` and `profileKey`.
- `components`: every embedded component of each reviewed record needs its own `componentKey`, fingerprint, exact owner (null only for verified global snapshots) and evidence. Notification/Gmail/history components require reviewed account/profile keys. Notifications also require the original numeric `credentialVersion`, independently reviewed when absent from the source. Existing recorded IDs/versions cannot be overridden.
- `currencyActors`: exact reviewed account/profile/tenant keys, unique historical `namespaceKeys`, `historyComplete: true` and historical evidence. No default/empty-history inference. An observed receipt contradicting an empty or incomplete review blocks the plan.

Every evidence object requires `validationStatus: "reviewed"`, a permitted kind and an opaque private review reference. Historical cases, compatibility records and components require `reviewed-historical-record`. A digest proves integrity, **not** reviewer identity, truth or approval; future execution must authenticate the reviewed artifacts and approval separately. Names, subscriptions, current student assignment and current email ownership never generate a mapping. Duplicate, orphaned, cross-tenant, unsupported or stale evidence fails closed.

The source inventory contains only HMAC record/reference/component tags, reviewed School IDs, allowed status/role metadata, counts and integrity fingerprints. It contains no cleartext student answers, names, email addresses, message content, cipher bytes, provider subjects/versions or receipt payload hashes. It remains private and excluded from Git. Never attach a real manifest/inventory to a PR.

## Fingerprints and expanded read approval

V2 exports whole protected assignment/notification/history/grant subtrees **into memory only** to compute canonical-EJSON HMAC fingerprints. It reads complete currency receipt documents, including unknown fields, for immutable-record evidence. Other root records use a documented metadata projection; it is not a claim to fingerprint omitted passwords, setup tickets or all profile fields. Output contains only HMACs and nonpersonal validation metadata. No encryption keys, password/reset/setup records or decrypted tokens are requested. No content, ciphertext or errors containing source values are printed.

This is a materially expanded read scope compared with v1. The wrapper now requires BOTH `-ReadOnlyInventoryApproved` and `-ProtectedFingerprintReadApproved`. A future owner must approve reading those protected subtrees/full receipts and private output/key custody, not merely collection counts. No such approval or live read occurred here. Shell privacy and authenticated read-only privilege checks remain mandatory and are not bypassed. The earlier real-shell privacy/version blocker is still unresolved.

`beforeDigest` and `protectedDigest` omit only the root `tenantSchoolId` for the nine allowed collections, enabling metadata-only reruns. Compatibility records include **all selected data and existing ownership**. Embedded fingerprints include the full selected embedded value, preserving answers, historical outcomes and cipher changes as drift. The versioned fingerprint contract distinguishes this evidence from Java's BSON serialization.

## Currency replay preservation

The planner never outputs a receipt update, rekey, v2 alias, callback, award or balance adjustment. It rejects write-mode receipts and tampered plan descriptors, including receipts disguised as an allowed collection. Rehearsal revalidates exact source/collection/evidence and only mutates sanitized root-owner copies, never raw documents. Repeated rehearsals produce zero remaining root updates and never create currency operations.

Reviewed normalized historical namespace tags locate potential receipts but do not prove ownership. Each receipt separately needs exact full-source fingerprint and reviewed immutable account/profile ownership. Duplicate actors, namespace overlap, ambiguous owners, observed unreviewed history, unknown versions, incomplete coverage and cross-school borrowing are rejected. Do not create a new action ID to bypass ambiguity.

These sanitized maps do **not** generate or replace PR #182's private `reps-currency-replay/v1` document. Runtime receipt `recordSha256` uses `CurrencyReplayManifest.fingerprint` over Java `Document.toJson` with preserved BSON types; inventory HMACs must never be substituted for it. A later separately approved private BSON artifact validation/generation step must bind exact IDs, full fingerprints, original payload hashes and reviewed namespaces to this review, retain writer-drain attestations and pin the runtime artifact ID/digest. Missing evidence continues to block runtime actions. Runtime Java code and receipt hashing remain unchanged.

## Quarantine, holds and preservation decisions

Summary/plan reports include duplicate-name/identity findings, missing/conflicting ownership, component counts and a deterministic transitive quarantine list. Existing-bound records are checked too; a dependency cannot become safe because it was previously labeled. Unreviewed records stay preserved and denied.

`cutoverHolds` identifies reviewed components still missing persisted owners/actor IDs/versions and active OAuth flows/notification claims. **A reviewed sidecar map does not make those records runtime-ready.** No embedded owner, grant metadata, intent version, snapshot or delivery state is changed by this PR.

For legacy assignment/history snapshots without trustworthy existing ownership/provenance, keep access blocked. A future owner-reviewed runtime compatibility approach could consult an immutable, independently reviewed sidecar without rewriting the snapshot; alternatively leave the historical record quarantined. Do not alter frozen snapshots or invent authorship to make cutover pass. This PR chooses neither a runtime sidecar architecture nor a historical rewrite.

## Remaining executor and activation gates

1. Approve exact deployed read-only target/principal, expanded protected-data read scope, privacy-compatible pinned shell and private evidence custody; no live uniqueness/count/index claims exist yet.
2. Review pilot mappings, actor/receipt history, component holds and quarantine dispositions. Validate the separate Java/BSON runtime replay artifact; do not mark incomplete history complete.
3. Implement/review a conditional executor separately: authenticated artifact approval, exact typed-ID resolution, target/key/count/revision/fingerprint rechecks, nine-collection/one-field allowlist, CAS, checkpoints, crash/restart/resume and drift rejection. No generic update operators, replacement saves, receipt writes, inserts or reconciliation.
4. Obtain backup/restore evidence and maintenance/writer/worker drain approval. Include currency transactions, pending OAuth callbacks, setup/reset changes, TTLs and signed webhook retry handling. A nontransactional inventory is not a point-in-time backup.
5. Resolve component/grant metadata adoption without changing frozen content, keys/AAD, delivery states or original actor versions. Requires a separate reviewed contract/executor or an approved runtime sidecar decision.
6. Finish canonical production-route/worker acceptance and a deployable unified activation mechanism; current global guard is still false-only. The 100-route full-verification gap is not closed by offline tests.
7. Separately approve database writes and later enforcement/traffic/worker reopening. No mixed legacy writers, name-based fallback or partial activation. After canonical activity, use a compatible canonical build or maintenance/forward repair; never strip IDs, relabel receipts, reset notifications or restore stale balances over new work.

Plans remain `executable:false` and `activationReady:false` even when all offline validation passes. No conditional database executor, deployment or migration is implemented here.
