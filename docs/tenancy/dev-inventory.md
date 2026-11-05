# A7 development inventory adapter rehearsal

This release accepts only the authenticated disposable TLS replica set created by the rehearsal. Neither Atlas endpoint is an executable target. The Phase 5D-1 launcher and disposable migration executor retain their own policies.

## Report and permissions

The first report covers `schools` and the nine mapped roots listed in [ownership-fields.json](ownership-fields.json). The synthetic user authenticates against `admin` and receives exactly `find` on those ten collections in `reps_dev_inventory_fixture`. The future Atlas role would use the same collection list in `repsdevdb`, with no extra actions, inherited grants, wildcards or project-wide cluster access.

MongoDB returns only fixed aggregate counts. Ownership is classified as missing for null or absent metadata, invalid for unsupported, blank or nonexistent IDs, conflicting when multiple persisted school keys have the same canonical representation, and present for one exact match. These counts describe root metadata. A missing count can include global templates. It does not establish a missing required assignment of ownership. Display names are used only for collision counts. The normalized metric uses trim and MongoDB's ASCII lowercase behavior. Relationship conflicts, assignment and notification evidence, receipts, grants and billing are outside this first report.

The adapter runs fixed pipelines with a five-second limit per aggregation and disk spill disabled. It validates exact numeric result fields and bucket totals before the existing publisher validates and atomically publishes the closed report schema. Use a private POSIX directory outside Git and review a separate overall execution deadline before any live run. A nontransactional scan can observe different moments across collections. It cannot establish a record-level mapping or certify cutover readiness.

## Synthetic verification

From the backend root, with Node and Docker installed:

```powershell
node --test scripts/tenancy/*.test.cjs
git diff --check
$toolsPath = (Resolve-Path -LiteralPath '.\scripts\tenancy').Path
$shellArchive = (Resolve-Path -LiteralPath '<path to mongosh-2.14.1-linux-x64.tgz>').Path
docker run --pull never --rm --network none --tmpfs /data/db --tmpfs /data/configdb --tmpfs /tmp --mount "type=bind,source=$toolsPath,target=/tools,readonly" --mount "type=bind,source=$shellArchive,target=/opt/inventory-mongosh.tgz,readonly" --entrypoint /bin/bash mongo:6.0.6 /tools/dev-inventory-rehearsal.sh
```

The harness uses temporary keys and certificates, checks the shell archive digest and runs without outbound networking. It verifies TLS trust rejection, failed authentication, excessive privileges, counts-only output, private permissions and unchanged data hashes and indexes. The container discards the synthetic credentials, reports and database on exit. The original rehearsal remains documented in [README.md](README.md).

## Owner gates before a deployed adapter can run

1. Obtain and review independent Atlas control-plane evidence for the development organization, project, cluster ID, SRV endpoint, member identity and topology. Reconcile it with the custodian and PR #193 evidence for `repsdevdb.xgrmfbb.mongodb.net/repsdevdb`. Explicitly exclude `reps.risjic2.mongodb.net`.
2. Separately approve and review a pinned Atlas target policy and constrained network path. Prove the driver cannot follow unexpected discovery, resolve to an unapproved member or send credentials before target verification. Test TLS trust and hostname verification. No caller-provided approval, destination, database or URI options.
3. Separately approve provisioning a dedicated short-lived Atlas database user restricted to the exact development cluster and the ten exact data resources. Verify complete effective privileges with `connectionStatus` before collection access. Fail closed if that evidence is unavailable.
4. Separately approve the exact tool revision, operator, run window, load limits, audit evidence, private output path, authorized readers and retention. Proposed retention is 30 days in restricted storage, followed by deletion under the owner's approved retention process. Restricted mapping evidence requires a distinct approval.

Stop before collection access on any unknown or differing target, routing, TLS, identity or grant evidence. Stop on timeouts, schema drift or unexpected output. Do not publish partial results or include raw driver errors, names, emails, identifiers, answers, descriptions or credentials in logs or reports. Deployed migration and canonical activation remain separate.
