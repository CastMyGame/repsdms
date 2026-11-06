"use strict";
const fs=require("node:fs");
const path=require("node:path");

function checkOutput(file) {
    if(typeof process.getuid!=="function" || !path.isAbsolute(file)) throw Error();
    const parent=fs.realpathSync(path.dirname(file));
    const stat=fs.statSync(parent);
    if(stat.uid!==process.getuid() || (stat.mode&0o077)!==0) throw Error();
    for(let current=parent;;current=path.dirname(current)) {
        if(fs.existsSync(path.join(current,".git"))) throw Error();
        if(current===path.dirname(current)) break;
    }
    if(fs.existsSync(file)) throw Error();
}
module.exports={checkOutput};
