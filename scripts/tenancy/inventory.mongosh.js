// Standalone aggregate-only inventory. No application startup or database writes.
(async function () {
    let stage = "preflight";
    try {
        const path = require("node:path");
        const directory = process.env.REPS_TENANT_AUDIT_TOOL_DIR;
        const safety = require(path.join(directory, "inventory-safety.cjs"));
        const inventory = require(path.join(directory, "inventory-aggregate.cjs"));
        const uri = process.env.REPS_TENANT_AUDIT_URI;
        safety.requireTarget(uri, process.env.REPS_TENANT_AUDIT_DATABASE);
        if (process.env.REPS_TENANT_AUDIT_EXPECTED_HOST || process.env.REPS_TENANT_AUDIT_CONTENT_APPROVED ||
            process.env.REPS_TENANT_AUDIT_APPROVED !== "read-only-inventory-approved") throw Error();
        stage = "shell privacy";
        for (const [name, value] of [["disableLogging",true],["enableTelemetry",false],
            ["disableSchemaSampling",true],["historyLength",0],["updateURL",""],["snippetAutoload",false],["browser",false]]) {
            if (name === "enableTelemetry" && await config.get("forceDisableTelemetry") === true) continue;
            if (await config.get(name) !== value) throw Error();
        }
        if (Object.keys(process.env).some(key => key.startsWith("MONGODB_LOG_") && process.env[key])) throw Error();
        if (!process.env.REPS_TENANT_AUDIT_PASSWORD) throw Error();
        stage = "replica identity";
        const connection = await new Mongo(uri);
        const database = connection.getDB(safety.database);
        const hello = await database.runCommand({hello:1});
        if (hello.setName !== "repsinventory" || !hello.isWritablePrimary ||
            hello.hosts?.length !== 1 || hello.hosts[0] !== "127.0.0.1:27031") throw Error();
        stage = "authentication";
        const authenticated = await database.auth(safety.principal, process.env.REPS_TENANT_AUDIT_PASSWORD);
        if (authenticated.ok !== 1 && authenticated !== 1) throw Error();
        stage = "effective privileges";
        safety.requirePrivileges(await database.runCommand({connectionStatus:1,showPrivileges:true}), inventory.collections);
        stage = "collection scan";
        const input = {};
        let total = 0;
        for (const collection of inventory.collections) {
            const rows = await database.getCollection(collection).find({}, inventory.projectionFor(collection))
                .limit(10001).maxTimeMS(15000).toArray();
            total += rows.length;
            if (rows.length > 10000 || total > 50000) throw Error();
            input[collection] = EJSON.serialize(rows, {relaxed:false});
        }
        stage = "aggregate publication";
        safety.publish(process.env.REPS_TENANT_AUDIT_OUT, inventory.aggregate(input), inventory.schema);
        print("Aggregate inventory published.");
    } catch (_) {
        print("Inventory refused or failed during " + stage + ".");
        exit(1);
    }
})();
