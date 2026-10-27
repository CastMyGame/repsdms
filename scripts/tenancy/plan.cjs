"use strict";
// Offline JSON-only planner. It cannot connect to MongoDB or execute a backfill.
const fs=require("node:fs"),path=require("node:path"),core=require("./audit-core.cjs");
try {
  const [command,snapshotFile,manifestFile,outputFile]=process.argv.slice(2);
  if(!["summary","plan"].includes(command)||!snapshotFile)throw new Error();
  const snapshot=JSON.parse(fs.readFileSync(snapshotFile,"utf8"));
  if(command==="summary")process.stdout.write(JSON.stringify(core.summarize(snapshot),null,2)+"\n");
  else {
    const result=core.plan(snapshot,JSON.parse(fs.readFileSync(manifestFile,"utf8")));
    const repo=fs.realpathSync(path.resolve(__dirname,"../..")),parent=fs.realpathSync(path.dirname(path.resolve(outputFile)));
    if(parent.toLowerCase()===repo.toLowerCase()||parent.toLowerCase().startsWith(repo.toLowerCase()+path.sep))throw new Error();
    fs.writeFileSync(path.join(parent,path.basename(outputFile)),JSON.stringify(result,null,2)+"\n",{flag:"wx",mode:0o600});
    process.stdout.write(JSON.stringify({executable:false,updates:result.updates.length,alreadyBound:result.alreadyBound,counts:result.counts},null,2)+"\n");
  }
} catch (_) {process.stderr.write("Offline tenant preparation failed: invalid, conflicting, ambiguous or stale evidence. No database connection or writes occurred; details withheld.\n");process.exitCode=1;}
