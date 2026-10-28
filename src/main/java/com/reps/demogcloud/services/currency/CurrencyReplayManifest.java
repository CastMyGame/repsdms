package com.reps.demogcloud.services.currency;

import com.reps.demogcloud.services.tenancy.CanonicalAccountContext.Actor;
import org.bson.Document;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Reviewed private metadata, never a request DTO or a name-based ownership inference. */
final class CurrencyReplayManifest {
    static final String COLLECTION="currency_replay_manifests";
    private final Document document;
    CurrencyReplayManifest(Document document,String expectedDigest) {
        this.document=document;
        try { validate(expectedDigest); } catch (RuntimeException invalid) { throw blocked(); }
    }
    private void validate(String expectedDigest) {
        if(document==null || expectedDigest==null || !expectedDigest.matches("[0-9a-f]{64}")
                || !expectedDigest.equals(fingerprint(document)) || !"reps-currency-replay/v1".equals(document.getString("schema"))
                || !Boolean.TRUE.equals(document.get("historyReviewed")) || !Boolean.TRUE.equals(document.get("legacyWritersDrained"))
                || !text(document.get("reviewReference")))throw blocked();
        var accounts=new HashSet<String>();var namespaces=new HashSet<String>();var receipts=new HashSet<String>();
        for(Document actor:rows(document,"actors")) {
            if(!text(actor.get("accountId")) || !text(actor.get("profileId")) || !text(actor.get("tenantSchoolId"))
                    || !Boolean.TRUE.equals(actor.get("historyComplete")) || !text(actor.get("evidenceReference"))
                    || !accounts.add(actor.getString("accountId")))throw blocked();
            for(Document namespace:rows(actor,"legacyNamespaces")) {
                String prefix=prefix(namespace);
                // A shared namespace cannot establish exclusive historical ownership.
                if(!namespaces.add(prefix))throw blocked();
            }
        }
        for(Document receipt:rows(document,"receipts"))
            if(!hex(receipt.get("receiptId")) || !hex(receipt.get("recordSha256")) || !text(receipt.get("accountId"))
                    || !text(receipt.get("tenantSchoolId")) || !receipts.add(receipt.getString("receiptId")))throw blocked();
    }
    List<String> candidates(Actor actor,String kind,String action) {
        var matching=rows(document,"actors").stream().filter(a->actor.accountId().equals(a.getString("accountId"))).toList();
        if(matching.size()!=1)throw blocked();var entry=matching.get(0);
        if(!actor.profileId().equals(entry.getString("profileId")) || !actor.tenantSchoolId().equals(entry.getString("tenantSchoolId")))throw blocked();
        return rows(entry,"legacyNamespaces").stream().map(n->sha256(prefix(n)+kind+":"+action)).toList();
    }
    void verifyReceipt(Document receipt,Actor actor) {
        var matches=rows(document,"receipts").stream().filter(r->Objects.equals(receipt.get("_id"),r.get("receiptId"))).toList();
        if(matches.size()!=1)throw blocked();var evidence=matches.get(0);
        if(!actor.accountId().equals(evidence.get("accountId")) || !actor.tenantSchoolId().equals(evidence.get("tenantSchoolId"))
                || !fingerprint(receipt).equals(evidence.get("recordSha256")))throw blocked();
    }
    static String fingerprint(Document value){return sha256(((Document)ordered(value)).toJson());}
    private static Object ordered(Object value){
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),ordered(v)));return new Document(sorted);}
        if(value instanceof List<?> list)return list.stream().map(CurrencyReplayManifest::ordered).toList();return value;
    }
    static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException("SHA-256 unavailable");}}
    private static String prefix(Document row){
        String actor=row.getString("actor"),school=row.getString("school");
        if(!text(actor) || !text(school) || !actor.equals(actor.toLowerCase(Locale.ROOT)) || !school.equals(school.toLowerCase(Locale.ROOT)))throw blocked();
        return actor+":"+school+":";
    }
    private static boolean text(Object value){return value instanceof String s && !s.isBlank() && s.equals(s.trim());}
    private static boolean hex(Object value){return value instanceof String s && s.matches("[0-9a-f]{64}");}
    private static List<Document> rows(Document document,String field){
        Object value=document.get(field);if(!(value instanceof List<?> list) || list.size()>10000 || list.stream().anyMatch(v->!(v instanceof Document)))throw blocked();
        return list.stream().map(Document.class::cast).toList();
    }
    static ResponseStatusException blocked(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Currency replay ownership requires reviewed resolution");}
}
