# MVP currency transactions

## Deployment and compatibility

Currency writes now require MongoDB 4.4+ with logical sessions and a replica set (or a transaction-capable sharded cluster). There is no standalone fallback. The existing development replica set passed a metadata-only topology check and an aborted read-only snapshot transaction on two nonexistent probe namespaces; no application records were read or changed. No topology change or data migration is required for that deployment.

`currency_operations` is an additive receipt collection, using its built-in unique `_id` index. Recognition cases gain optional `currencyOperationId` and `currencyAwarded` fields. Existing balances, cases and unknown profile fields are retained. Receipts must be retained with financial history; do not apply a TTL or delete them to retry an action. No historical reconciliation/backfill runs. Null/negative historical balances require explicit staff review; operations do not silently initialize them. New profiles start with zero currency, including new staff; the existing same-school administrator budget-reset action remains available (sets active staff budgets to five).

The application database user must have read/write permission on the new receipt collection as well as existing wallet/case collections; the non-mutating deployment check did not exercise write permissions.

Deploy the backend before the companion frontend. Pause currency traffic during the rollout and drain old backend versions/instances before resuming awards: an old binary still contains unsafe wallet saves. Older clients still receive ordinary case responses plus additive receipt fields; clients must provide a stable UUID in `X-REPS-Action-Id` for award, redemption and staff-budget-reset requests. The existing frontend disciplinary interceptor already provides award IDs; the companion frontend adds redemption/reset IDs and checks committed award receipts. Do not roll back to an unsafe wallet-writing backend while currency operations are in use. A frontend-only rollback is compatible with the additive backend response.

## Transaction boundaries

A Positive Shout Out batch contains only positive recognitions, at most 200 distinct students. Zero-currency recognition remains supported; it is not a transfer. Negative/fractional/overflow amounts, mixed referral batches and duplicate students are rejected. Every student must be active, in the authenticated staff member's school, and accessible through established administrator, roster, guidance or spotter relationships. Caller-supplied sender identity is ignored.

The transaction validates all cases, amounts and balances, then atomically commits a unique actor/action receipt, one total teacher debit, all student credits, all recognition records and their embedded required-notification intents. Linked notes/guidance writes also participate in the same session. Transient transaction failures roll back before bounded retry. Unknown commit outcomes are not automatically re-executed: the service checks the receipt or reports an unconfirmed outcome, requiring the identical action ID on retry. Reusing an ID with different data returns 409. A lost HTTP response is safe to retry without a second award.

Currency recognition always captures its notification obligation, even when delivery gates are off. The existing email routing/capture/dispatch gates still control dispatcher availability and sending; all defaults remain false. No synchronous legacy email is sent from a recognition transaction. A separate dispatcher can see intents only after commit. It retains the existing per-message retry/uncertain-outcome protections; provider delivery itself cannot be made part of a MongoDB transaction.

Redemption validates the full batch and requires positive integers and sufficient balances. The existing staff-budget reset is same-school ADMIN-only, transactional, receipt-backed and limited to 200 staff records. Direct unrecorded transfers are rejected. Bulk student profile updates cannot change currency, school or account email. Student/employee repository `save` and `saveAll` update non-wallet fields only; currency mutations use conditional targeted updates inside the transaction service, preventing stale profile saves from overwriting wallets.

## Local MongoDB without deployed database access

Use Docker Desktop in Linux-container mode. These commands create a new disposable instance with no shared volume or credentials. Choose a free port; do not attach an existing database volume.

```powershell
docker run --detach --name reps-currency-rs --publish 127.0.0.1:27019:27019 mongo:8.0 mongod --replSet rs0 --port 27019 --bind_ip_all
docker exec reps-currency-rs mongosh --port 27019 --quiet --eval 'rs.initiate({_id:"rs0",members:[{_id:0,host:"127.0.0.1:27019"}]})'
docker exec reps-currency-rs mongosh --port 27019 --quiet --eval 'db.hello().isWritablePrimary'
```

Wait for `true`. The URI is `mongodb://127.0.0.1:27019/reps_local?replicaSet=rs0&directConnection=true`. For application development use the established local-only configuration with cloud imports/providers disabled and synthetic records; a replica-set URI alone does not isolate unrelated services. Each developer continues to use their own local JWT/Gmail keys outside Git. No deployed database/secret access is required for the tests below.

## Focused real-database tests

Requires Java 17 and existing Maven dependencies. The opt-in test URI permits only `127.0.0.1:<port>` and no credentials. Tests create a unique `reps_currency_test_<uuid>` database and drop only that database afterward. They do not launch Spring Boot, import Secret Manager, scan application startup configuration, or construct live email/OAuth transports.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17' # or your Java 17 installation
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:REPS_CURRENCY_TEST_MONGODB_URI = 'mongodb://127.0.0.1:27019/reps_currency_test?replicaSet=rs0&directConnection=true'
.\mvnw.cmd '-Dtest=Currency*Test' test
Remove-Item Env:REPS_CURRENCY_TEST_MONGODB_URI
```

Without the explicit URI, the real MongoDB suite reports skipped tests; mocked/security suites still run. CI must provision a disposable replica set and set the opt-in URI to claim real transaction verification. The implementation run used the existing cached MongoDB 6.0 image (wire version 17), Java 17, and no cloud-connected application services.

After reviewing your test run, stop the named disposable container with `docker stop reps-currency-rs`. Remove it only when its synthetic data is no longer needed. Reruns can use `docker start reps-currency-rs`; never reinitialize a running replica set.

## Operational limits

Receipt replay confirms the original committed action, not a new award. After an uncertain response keep the input/action ID unchanged; consult case/notification status before beginning another action. Historical actions created before receipts cannot be retroactively certified. Existing fictitious recipient addresses still cause delivery failures; notification delivery is separate from committed awards. There is no automatic reconciliation, live-data migration, provider send or new cloud infrastructure in this change.
