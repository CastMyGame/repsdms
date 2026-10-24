# Disciplinary assignment lifecycle

Office referrals **do not have student assignments**. An authorized school administrator/principal handles their consequences and closure individually. No automatic referral-code-to-assignment mapping is introduced.

## Confirmed defects addressed

- Creation used teacher binding → school binding → system default, while student retrieval used a different teacher query and school template lookup. Retrieval ignored `assignmentTemplateId`, so changing a binding could change already issued work.
- Exact Mongo `active=true`/`scope=SYSTEM_DEFAULT` predicates excluded legacy structured records with absent fields, despite Java read defaults. Infraction/email matching was not normalized, and school IDs and school names were used inconsistently. These are confirmed compatibility failure modes; the particular deployed records were not read, so their exact metadata still needs owner verification.
- Only `mapIndex` was saved. Answers lived in React state; reading answers were not recorded, and exploratory responses could skip two questions. Reloading resumed an index without its answers.
- Submission used student email plus infraction, chose the first open case, and could close with an empty answer list. Rejection rewrote the description rather than maintaining independent work/history.
- Office-referral loading was explicitly unwired in the student player, while UI/API paths still offered student assignment actions.

## Selection and binding

One resolver serves issuance: active authorized teacher binding, school binding (including an existing applicable `SCHOOL_DEFAULT` template), then a verified system default. Explicit invalid bindings do not silently fall through. Multiple distinct defaults/bindings are actionable conflicts. Teacher/infraction keys use trim, whitespace normalization and case folding; school-name/ID aliases use the persisted school record. Explicitly inactive templates remain unavailable.

Legacy structured templates with absent active/scope fields use existing Java defaults on read. Missing question IDs are generated **in the case snapshot only**. Unknown ownership is never assumed to be system ownership. The global legacy `assignments` collection is not automatically migrated or substituted for `assignment_templates`.

New ordinary disciplinary cases store `assignmentTemplateId` plus an immutable template snapshot in `assignmentWork`. Special concern/shout-out/incomplete-work paths and office escalation do not gain template assignments. An invalid level is not replaced with the maximum level. Resolution/configuration failure preserves the disciplinary case with `assignmentWork.state=UNAVAILABLE`; the frontend warns that attachment needs staff attention.

Catalog edits cannot change the questions already issued. If a draft's original template is removed/deactivated or no longer authorized for its school, student editing/submission stops with an unavailable message; authorized staff may explicitly recover it. Already submitted work remains reviewable against its original snapshot, including after catalog retirement. No alternative template is selected automatically. Every new submission/recovered draft retains its own template snapshot.

## API and persistence

- `GET /assignments/v1/cases/{punishmentId}` returns case status, availability/recovery indication and saved work. GET never repairs or writes records.
- `PUT /assignments/v1/cases/{punishmentId}/save|submit|review|bind` takes `version` and a stable `actionId`; draft/submission bodies include `answers` keyed by question ID and `currentQuestionId`.
- Written answers use `text`; selections use actual `optionIds`, not client-provided correctness booleans; reading retries use `retryText`. The backend enforces required answers, length/selection constraints, configured grading and the existing normalized-word-coverage retry rule.
- `review` takes `decision=ACCEPT|REJECT` and feedback; rejection feedback is required. `bind` requires `templateId` and `confirmRestart=true`; legacy progress/pending recovery also requires an explanation.
- Draft, position, history and state live together in the case document. Writes compare the existing case revision, increment work/case versions and return HTTP 409 on conflicts. Exact retries reuse their action ID/digest; they do not repeat a successful transition or notification. A stale or changed request cannot overwrite newer work.
- Required-notification intents and trusted conversation/actor metadata are included in the same atomic case update for submission/review. Provider transports are not invoked by this API. Existing email gates/defaults remain unchanged.
- Limits protect embedded Mongo documents: at most 200 questions, a 256-KiB template snapshot, a 64-KiB answer payload, 20,000 characters per written/retry answer, 100 history entries and an 8-MB work record. Exceeding a limit fails explicitly without deleting saved history.

| Work state | Case status | Allowed next actions |
| --- | --- | --- |
| ASSIGNED / IN_PROGRESS | OPEN | Student save/submit |
| SUBMITTED | PENDING | Authorized staff accept/reject |
| REJECTED | OPEN | Student revise/save/resubmit |
| COMPLETED | CLOSED | Read-only history |
| UNAVAILABLE | Existing status retained | Authorized explicit recovery; never student completion |

Persisted identity, account state, school and student/case relationships authorize access. Students can save/submit only their own work. Review/recovery uses the existing recorded-participant/admin policy; a roster association alone is not review permission. Principal-only office closure uses persisted administrator authorization regardless of email gates. Unsafe global maintenance/deletion routes and index-only assignment writes cannot bypass these rules.

## Legacy compatibility and rollback

No migration, index creation, backfill, template rewrite or database command runs at startup. Existing descriptions, answer-history maps, case IDs and completion history remain unchanged. A bound legacy OPEN case with index zero and no previous submission history can initialize work on its first explicit save; a nonzero index, prior submissions or an unbound case requires staff-confirmed recovery. Answers that the old client never persisted cannot be reconstructed. Historical template content versions were not stored: a bound legacy case can retain its original template ID, but the pre-edit content cannot be reconstructed from source. Recovery is an explicit state transition and keeps original record/history. Closed legacy cases are not reopened automatically.

The old student submit-by-infraction API returns 409 rather than selecting an arbitrary case or closing empty work. New-work review uses the case API; authorized legacy staff approval remains available. Legacy rejection requires explicit recovery rather than rewriting descriptions. Office student submit/index/reject APIs refuse assignment actions; principal closure remains available and repeated closure is safe.

Deploy backend and frontend as a coordinated release: backend first, frontend immediately afterward, then reload old browser tabs. Old clients receive safe errors on retired write paths during the cutover. Do not operate old and new writers concurrently. After new work exists, rolling back to an old writer is unsafe because it lacks these lifecycle guards; disable assignment editing during rollback and retain all new fields/history. No automatic rollback data deletion is supplied.

## Verification and operational limits

Automated verification uses mocked persistence/providers, real Spring Security chains and real Spring Mongo document conversion. It is not a real MongoDB/browser end-to-end test. Before pilot use, verify two-tab conflicts, real disposable Mongo compare-and-set behavior, refresh/sign-out recovery, mobile rendering and one complete synthetic teacher–student–staff loop without live email. No database or provider was contacted during this work.

Owner-only read-only metadata diagnostic for the previously reported infractions (not executed here):

```javascript
db.assignment_templates.find(
  {infractionName: {$regex: "^(Horseplay|Dress Code|Inappropriate Language)$", $options: "i"}},
  {_id:1, infractionName:1, level:1, active:1, scope:1, createdBySystem:1, createdByUserId:1, schoolId:1}
)
```

Check corresponding binding IDs, school-name/ID aliases and question-schema compatibility without modifying records. Do not infer that any unknown-owned template should be made globally visible. Legacy authoring/migration screens that write the global `assignments` collection remain intentionally blocked; use authorized structured-template authoring/recovery instead.
