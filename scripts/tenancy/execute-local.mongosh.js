// LOCAL DISPOSABLE TARGETS ONLY. No application startup, provider calls or deployed mode.
(async function(){
    const fs=require("node:fs"),path=require("node:path"),base=process.env.REPS_LOCAL_EXECUTOR_TOOL_DIR;
    let descriptor;
    try{
        require(path.join(base,"shell-runtime.cjs"));
        const exec=require(path.join(base,"local-executor.cjs")),{adapter}=require(path.join(base,"mongo-executor-adapter.cjs"));
        const read=name=>JSON.parse(fs.readFileSync(process.env[name],"utf8"));
        const request={mode:process.env.REPS_LOCAL_EXECUTOR_MODE||"dry-run",atomicity:process.env.REPS_LOCAL_EXECUTOR_ATOMICITY||"transaction",
            uri:process.env.REPS_LOCAL_EXECUTOR_URI,database:process.env.REPS_LOCAL_EXECUTOR_DATABASE,
            auditKey:exec.key(process.env.REPS_TENANT_AUDIT_KEY),approvalKey:exec.key(process.env.REPS_LOCAL_EXECUTOR_APPROVAL_KEY),
            snapshot:read("REPS_LOCAL_EXECUTOR_SNAPSHOT"),manifest:read("REPS_LOCAL_EXECUTOR_MANIFEST"),plan:read("REPS_LOCAL_EXECUTOR_PLAN"),approval:read("REPS_LOCAL_EXECUTOR_APPROVAL"),
            schema:JSON.parse(fs.readFileSync(path.resolve(base,"../../docs/tenancy/manifest.schema.json"),"utf8"))};
        exec.preflight(request); // Must happen BEFORE constructing Mongo, even in dry-run.
        const output=process.env.REPS_LOCAL_EXECUTOR_REPORT;if(!output||!path.isAbsolute(output))throw Error();
        const parent=fs.realpathSync(path.dirname(output)),repo=fs.realpathSync(path.resolve(base,"../.."));if(parent.toLowerCase()===repo.toLowerCase()||parent.toLowerCase().startsWith(repo.toLowerCase()+path.sep))throw Error();
        descriptor=fs.openSync(path.join(parent,path.basename(output)),"wx",0o600);
        const sink={write:event=>{fs.writeSync(descriptor,JSON.stringify(event)+"\n");fs.fsyncSync(descriptor);}};
        const connection=await new Mongo(request.uri);const result=await exec.run(request,adapter(connection,request.database,EJSON),sink);
        request.auditKey.fill(0);request.approvalKey.fill(0);print(JSON.stringify(result));if(!["COMPLETED","DRY_RUN_VALIDATED"].includes(result.status))exit(1);
    }catch(_){print("Local migration refused or interrupted; no details or credentials are displayed. Inspect the sanitized journal and revalidate the approved source before any rerun.");exit(1);}
    finally{if(descriptor!==undefined)fs.closeSync(descriptor);}
})();
