# Atlas inventory with the approved SRV security model

## Scope and accepted limitation

This separate tool uses the supported MongoDB Node driver 7.5.0 SRV connection model. The original disposable inventory tools and migration executor retain their own targets and restrictions.

**Replica-set validation is not guaranteed to precede every authentication exchange.** Speculative SCRAM and connection establishment can authenticate before topology events or the explicit hello check. Option B relies on independent target attestation, trusted TLS, restricted network access, a temporary cluster-restricted identity and exact effective find grants. It does not implement the experimental connection gate.

The fixed policy records organization `65674bdb19ad1d08d6473d15`, project `65674bdb87e3bc06eba69262`, cluster `RepsDevDB`, database `repsdevdb`, auth database `admin`, seed `repsdevdb.xgrmfbb.mongodb.net`, replica set `atlas-bafhxz-shard-0` and the three reviewed `ac-fykprpn-shard-00-0[0-2].xgrmfbb.mongodb.net:27017` members. The DMS endpoint is excluded. Organization, project and cluster ownership are independently attested control-plane facts; MongoDB hello cannot prove them.

## Connection and publication

The adapter checks the exact SRV set and TXT options before connecting and before publication. It snapshots member IP resolutions. Its driver lookup callback refuses unknown member names and changed addresses on each socket lookup. TLS uses normal CA and hostname verification plus an exact member-name check. No insecure options or caller-selected targets are accepted by the entry point. Driver monitoring uses the same options, while topology events abort on unexpected members, replica-set names or topology types. Known-member primary elections are allowed. Missing topology during initial discovery is provisional; an explicit primary hello with the exact three-member set is required before reading collections.

The operator must also approve an egress restriction covering only the reviewed DNS service and member destinations/ports. The application checks complement that network restriction. They do not create or attest a host firewall. Address changes require review, even if Atlas maintenance caused them.

The only application data operations are the ten fixed count aggregations from PR #194. They return numeric counts, use a five-second server limit, disable disk spill and automatic read retries, and share a 90-second abort timer. DNS/OS shutdown can take longer to settle after abort; no result is published after the timer expires. The driver may issue normal monitoring, authentication and session-maintenance commands. No application writes, index changes, arbitrary queries, records, personal data or source error text are emitted.

Effective privileges must consist of exactly `find` on `schools`, `users`, `employee`, `students`, `Punishments`, `OfficeReferrals`, `Guidance`, `TrackedBehaviorEvents`, `assignment_templates` and `assignment_template_bindings` in `repsdevdb`, for `reps_a7_inventory_reader@admin`. Collection find privileges permit document reads to anyone holding the credential; counts-only behavior is enforced by the reviewed tool.

The existing closed-schema publisher requires an owner-private POSIX directory outside Git. It validates and fsyncs a private staging file, publishes without overwriting an existing report, and removes the staging file on failure. Reports are nontransactional counts, not a backup, ownership manifest or migration approval. Proposed retention is 30 days, subject to owner approval and storage-specific deletion procedures.

## Separate approvals before any live execution

1. Review the exact commit, policy digest, control-plane attestation and current SRV/TXT/member/address evidence. Cluster name and database must reconcile with PR #193.
2. Separately approve network configuration and provisioning a temporary database user restricted to this cluster. Use the shortest supported expiry and revoke promptly after the run. Runtime `connectionStatus` verifies effective data grants, but does not establish Atlas user expiry or cluster restrictions. Owner control-plane evidence must establish those.
3. Approve one operator, run ID, one-hour-or-shorter window, metrics, query/load budget, audit evidence and private output location. Atlas Free has no database auditing, private endpoints or peering. A synthetic MongoDB hash check is not available as proof of no writes on Atlas Free.
4. Have the approving operator create a private approval artifact outside Git. It records `version: reps-atlas-approval/v1`, the policy digest, run ID, reviewer reference, attestation SHA-256, issued/expiry timestamps and `clusterRestrictedTemporaryUser: true`. This is an operator-controlled approval record, not a cryptographic signature or independent proof of its assertions. The tool refuses to reuse its run ID, even after failure. Do not remove the run marker to retry.

No approval artifact, deployed credential, or live connection has been created by this implementation.

After those separate approvals, on an approved POSIX operator host with Python 3 and Node 24, install the locked tooling dependencies with `npm ci --ignore-scripts --prefix scripts/tenancy/atlas-runtime`. The private launcher is `python3 scripts/tenancy/atlas-inventory-launch.py <private-approval.json> <private-report.json>`. It prompts without echo and pipes the password to Node stdin. Credentials never enter command-line arguments or tracked files. Rejects environment overrides for targets, credentials, TLS key logging, extra CA trust and Node injection. Memory reclamation is managed by the runtimes; secure zeroization of managed strings is not promised.

Abort on missing/stale approval, mismatched DNS/routing/TLS/topology, uncertain identity or grants, provider/network failure, expired deadline, invalid output, or unsafe storage permissions. Accept no partial report. A new run requires separate review and a new approval record.

## Synthetic verification and limitations

Run the existing offline tenancy suite plus `atlas-inventory.test.cjs`. The new Docker rehearsal uses three TLS MongoDB 6.0.6 nodes with the approved names mapped exclusively to loopback addresses inside a container with `--network none`. It installs synthetic users and records data/index snapshots before the adapter runs. The exact driver uses its real SRV connection path, with SRV/TXT responses injected only in the fixture process. It tests counts, private publication, overwrite refusal, wrong trust, authentication failure and excess grants. Unit tests cover DNS mutations, unknown destinations, changed IPs, topology and report validation. All fixture secrets are random and discarded with the container.

Use the existing verified mongosh 2.14.1 archive and cached MongoDB 6.0.6 image from the PR #194 rehearsal. Install locked dependencies before disabling external networking. From the backend root:

```powershell
node --test scripts/tenancy/*.test.cjs
$toolsPath = (Resolve-Path -LiteralPath '.\scripts\tenancy').Path
$shellArchive = (Resolve-Path -LiteralPath '<path to mongosh-2.14.1-linux-x64.tgz>').Path
docker run --pull never --rm --network none --tmpfs /data/db --tmpfs /data/configdb --tmpfs /tmp --mount "type=bind,source=$toolsPath,target=/tools,readonly" --mount "type=bind,source=$shellArchive,target=/opt/inventory-mongosh.tgz,readonly" --entrypoint /bin/bash mongo:6.0.6 /tools/atlas-inventory-rehearsal.sh
git diff --check
```

This does not reproduce real DNS resolver behavior, Atlas Free proxies, control-plane account expiry/cluster restrictions, real Atlas certificates or the deployed `connectionStatus` response. Those remain explicit preflight gates. No production-level guarantee is inferred from the synthetic fixture.
