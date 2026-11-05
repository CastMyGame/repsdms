// Execute with the reviewed mongosh binary using --norc --quiet --nodb.
(async function () {
    try {
        const env = process.env;
        const tool = require("/tools/dev-inventory.cjs");
        await tool.run({
            env,
            connect: uri => new Mongo(uri),
            output: "/tmp/dev-inventory/report.json",
            async checkPrivacy() {
                for (const [name, expected] of [
                    ["disableLogging",true],["forceDisableTelemetry",true],["disableSchemaSampling",true],
                    ["historyLength",0],["updateURL",""],["snippetAutoload",false],["browser",false]
                ]) {
                    if (await config.get(name) !== expected) throw Error();
                }
                if (!env.REPS_DEV_INVENTORY_PASSWORD ||
                    Object.keys(env).some(key => /^(MONGODB_LOG_|NODE_OPTIONS$|SSLKEYLOGFILE$)/.test(key) && env[key])) {
                    throw Error();
                }
            }
        });
        print("Synthetic aggregate inventory published.");
    } catch (error) {
        const safe = /^Inventory refused or failed during (TLS and topology|authentication|effective privileges|aggregate scan|aggregate publication)\.$/;
        print(safe.test(error?.message) ? error.message : "Inventory preflight refused.");
        exit(1);
    }
})();
