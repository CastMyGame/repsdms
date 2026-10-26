# MVP 1 shared infraction catalog: read-only application policy

## Confirmed scope and endpoint inventory

Infraction stores only infractionId, infractionName and infractionLevel in the global Infractions collection. It has no school owner, tenant ID or school-specific mutation workflow. Before this fix, createNewInfraction saved caller-supplied IDs (including existing records) and deleteInfraction deleted them without role/tenant authorization. Any authenticated account could modify definitions used by other schools. No current frontend catalog writer was found.

| Route | MVP 1 behavior |
| --- | --- |
| GET `/infraction/v1/all` | Existing authenticated catalog list preserved |
| GET `/infraction/v1/infractionId/{id}` | Existing authenticated lookup preserved |
| GET `/infraction/v1/infractionName/{name}` | Existing authenticated lookup preserved |
| POST `/infraction/v1/createInfraction` | Disabled, including overwrite of an existing ID |
| DELETE `/infraction/v1/delete/infraction` | Disabled |
| All other POST/PUT/PATCH/DELETE paths under `/infraction/v1`, including root, update, bulk and legacy aliases | Disabled by namespace fallback; no alternate update/bulk/legacy mutation route is supported |

Authenticated requests of every role, including administrators and forged operator/school claims, receive HTTP 405 with `Infraction catalog editing is unavailable for MVP 1.` Anonymous requests retain normal HTTP 401 authentication failure. The response contains no submitted IDs, school data or sensitive details. Creation and deletion service methods also throw the same error without a repository dependency, preventing direct-service bypass. This policy is unconditional and independent of all email flags.

Catalog records, IDs, severity levels, templates, teacher/school assignment bindings and existing case references are unchanged. No schema or school ownership is introduced. School-specific assignment-template editing and legitimate bindings remain governed by their existing authorization; they are not global catalog mutation APIs. Punishment maintenance routes that rewrite stored case names/levels remain denied by existing WorkflowAccessService policy.

## Separately controlled catalog maintenance

This is an operational runbook, not a new API, operator role, UI, approval platform or executable migration.

1. A maintainer obtains explicit owner authorization for a particular catalog change and target environment. Record the change ticket, author, reviewer, rationale and exact intended IDs. Ordinary school administrators cannot approve changes for all schools through the application.
2. Use separately authorized, least-privilege database maintenance access; verify environment identity and a usable backup/recovery point. Do not enable the retired API, borrow application user credentials or edit deployed records during development tests.
3. Prepare a reviewed one-time migration with dry-run output, expected record counts, preconditions and rollback. Inspect all references: Punishment.infractionId/name/level, assignment template infraction keys, teacher/school bindings, progression lookup and frontend supported selections. Names/levels are semantic keys too; changing only an ID does not eliminate compatibility risk.
4. Prefer additive, explicitly reviewed definitions with new stable IDs. Do not repurpose/delete a referenced ID, silently rename an existing infraction, renumber severity levels or reinterpret historical cases. If a referenced definition must change, separately design and approve compatibility/versioning before executing anything. Do not auto-rewrite old records or templates.
5. Verify uniqueness and supported name/level combinations against synthetic cases and templates in an isolated environment. Use explicit selectors and expected old values so concurrent/unexpected data aborts the operation. Preserve all unrelated definitions.
6. Execute only the approved scoped migration in the approved maintenance window. Keep sanitized before/after metadata, affected IDs/counts, maintainer identity, reviewer, migration version and timestamps in the audit trail. Do not store student information, credentials or database exports in Git.
7. Verify catalog reads, infraction selection, existing cases, assignment binding resolution and new synthetic case creation. If verification fails, stop further maintenance and use the reviewed rollback plan; do not automatically delete definitions that acquired references.

No catalog maintenance is executed in this PR. No backend/frontend configuration, secret, feature gate, schema, historical data or indexes change. Ordinary application users cannot toggle editing back on.

## Tests and deployment

Real Spring Security-chain tests exercise all reads and create/update/delete/bulk/legacy mutations for students, teachers, guidance, administrators, parent/unsupported claims, anonymous requests and forged school ownership, with email flags on/off. Repositories and external providers are mocked. Direct service calls are also tested. Broader case-creation, assignment, binding/security, roster/student, currency and email regressions ensure catalog consumption remains compatible.

This PR is independently based on dev after #174 and does not depend on the school-settings PR. No frontend companion change is needed for catalog selection. No deployed database is accessed, no live email is sent and no production migration/deployment is performed. Real Mongo currency tests are opt-in and separately reported if skipped.

Verification for this branch: Java 17 offline regressions discovered 1,164 tests: 1,141 passed, zero failures/errors, 23 opt-in Mongo currency tests skipped. This includes 8 new security-chain tests (4 per email-gate configuration), with repeated requests across all roles/mutation methods/aliases. Git whitespace checks passed.
