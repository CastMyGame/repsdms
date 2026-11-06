"use strict";
const fs=require("node:fs");
const path=require("node:path");

function privateDirectory(file) {
    if(typeof process.getuid!=="function" || !path.isAbsolute(file)) throw Error();
    const parent=fs.realpathSync(path.dirname(file));
    const stat=fs.statSync(parent);
    if(stat.uid!==process.getuid() || (stat.mode&0o077)!==0) throw Error();
    for(let current=parent;;current=path.dirname(current)) {
        if(fs.existsSync(path.join(current,".git"))) throw Error();
        if(current===path.dirname(current)) break;
    }
    return parent;
}

function reserve(approval,approvalPath,output) {
    const parent=privateDirectory(approvalPath);
    privateDirectory(output);
    if(fs.existsSync(output)) throw Error();
    const fd=fs.openSync(path.join(parent,".run-"+approval.runId),"wx",0o600);
    try {
        fs.writeFileSync(fd,JSON.stringify({runId:approval.runId,startedAt:new Date().toISOString()}));
        fs.fsyncSync(fd);
    }finally{fs.closeSync(fd);}
}
module.exports={privateDirectory,reserve};
