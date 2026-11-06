"use strict";
// Private input is supplied by the accompanying masked-input launcher through stdin.
const {run}=require("./atlas-inventory.cjs");

(async()=>{
    try {
        if(process.argv.length!==4 || process.argv[2]!=="--owner-approved-run" || process.stdin.isTTY ||
            Object.keys(process.env).some(key=>
                /^(NODE_OPTIONS|NODE_EXTRA_CA_CERTS|NODE_TLS_REJECT_UNAUTHORIZED|SSL_CERT_FILE|SSL_CERT_DIR|OPENSSL_CONF|SSLKEYLOGFILE|MONGODB_LOG_.*|REPS_.*(?:URI|EXPECTED_HOST|TARGET|PASSWORD))$/.test(key)
                && process.env[key])) throw Error();
        require("./atlas-output-files.cjs").checkOutput(process.argv[3]);
        let password="";
        for await(const chunk of process.stdin) {
            password+=chunk.toString("utf8");
            if(Buffer.byteLength(password)>1024) throw Error();
        }
        const {MongoClient}=require("./atlas-runtime/node_modules/mongodb");
        await run({acknowledged:true,password,output:process.argv[3],makeClient:(uri,options)=>new MongoClient(uri,options)});
        password="";
        console.log("Aggregate inventory published.");
    } catch(_) {
        console.error("Atlas inventory refused or failed. No report should be accepted.");
        process.exitCode=1;
    }
})();
