"use strict";
// Cached mongosh 1.10 embeds Node 16. This process-local JSON/EJSON clone shim uses built-ins only.
// Native structuredClone is retained on newer supported shells. No database or environment side effects.
if(typeof globalThis.structuredClone!=="function"){
    const v8=require("node:v8");globalThis.structuredClone=value=>v8.deserialize(v8.serialize(value));
}

module.exports={clone:globalThis.structuredClone};
