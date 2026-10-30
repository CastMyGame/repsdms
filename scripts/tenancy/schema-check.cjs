"use strict";
// Strict evaluator for exactly the JSON Schema vocabulary used by the committed v2 manifest.
// Unknown future keywords fail closed; this is not a general-purpose validation framework.
const {stable}=require("./audit-core.cjs");
const allowed=new Set(["$schema","title","$defs","$ref","type","properties","additionalProperties","required","items","minItems","uniqueItems","minLength","pattern","minimum","maximum","const","enum","anyOf"]);
function validateSchema(value,schema){
    function checkSchema(s){if(!s||typeof s!=="object"||Array.isArray(s)||Object.keys(s).some(k=>!allowed.has(k)))throw Error("Unsupported schema");for(const child of Object.values(s.properties||{}))checkSchema(child);for(const child of Object.values(s.$defs||{}))checkSchema(child);if(s.items)checkSchema(s.items);for(const child of s.anyOf||[])checkSchema(child);}
    checkSchema(schema);
    function check(v,s){
        if(s.$ref){const m=/^#\/\$defs\/([A-Za-z0-9_-]+)$/.exec(s.$ref);if(!m||!schema.$defs?.[m[1]])return false;return check(v,schema.$defs[m[1]]);}
        if(s.anyOf&&!s.anyOf.some(t=>check(v,t)))return false;
        if(Object.hasOwn(s,"const")&&stable(v)!==stable(s.const))return false;
        if(s.enum&&!s.enum.some(t=>stable(t)===stable(v)))return false;
        if(s.type){const ok={object:v!==null&&typeof v==="object"&&!Array.isArray(v),array:Array.isArray(v),string:typeof v==="string",integer:Number.isSafeInteger(v),null:v===null,boolean:typeof v==="boolean"}[s.type];if(!ok)return false;}
        if(typeof v==="string"&&(s.minLength!==undefined&&v.length<s.minLength||s.pattern&&!new RegExp(s.pattern).test(v)))return false;
        if(typeof v==="number"&&(s.minimum!==undefined&&v<s.minimum||s.maximum!==undefined&&v>s.maximum))return false;
        if(Array.isArray(v)){if(s.minItems!==undefined&&v.length<s.minItems||s.uniqueItems&&new Set(v.map(stable)).size!==v.length)return false;if(s.items&&!v.every(x=>check(x,s.items)))return false;}
        if(v&&typeof v==="object"&&!Array.isArray(v)){if(s.required?.some(k=>!Object.hasOwn(v,k)))return false;if(s.additionalProperties===false&&Object.keys(v).some(k=>!Object.hasOwn(s.properties||{},k)))return false;for(const [k,t]of Object.entries(s.properties||{}))if(Object.hasOwn(v,k)&&!check(v[k],t))return false;}
        return true;
    }
    if(!check(value,schema))throw Error("Manifest schema rejected");return true;
}
module.exports={validateSchema};
