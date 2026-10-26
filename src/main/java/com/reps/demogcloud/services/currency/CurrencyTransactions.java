package com.reps.demogcloud.services.currency;

import com.mongodb.*;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Service
public class CurrencyTransactions {
    private final MongoTemplate mongo;
    private final UserContextService users;
    private final RequiredNotificationService notifications;
    private final TransactionTemplate transactions;
    public CurrencyTransactions(MongoTemplate mongo, UserContextService users, RequiredNotificationService notifications) {
        this.mongo=mongo; this.users=users; this.notifications=notifications;
        var options=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT)
                .writeConcern(WriteConcern.MAJORITY).readPreference(ReadPreference.primary()).build();
        this.transactions=new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory(),options));
    }
    public CurrencyOperation execute(String kind,Object payload,Consumer<CurrencyOperation> work) {
        // Capability checks never examine application records. Standalone MongoDB fails closed.
        Document hello;
        try {hello=mongo.executeCommand(new Document("hello",1));}
        catch(org.springframework.dao.DataAccessException unavailable) {throw unavailable("MongoDB transaction capability could not be established");}
        if ((!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg")))
                || !hello.containsKey("logicalSessionTimeoutMinutes") || ((Number)hello.getOrDefault("maxWireVersion",0)).intValue()<9)
            throw unavailable("Currency operations require a transaction-capable MongoDB replica set");
        String actor=users.getCurrentStaffEmail(), school=users.getCurrentUserSchool();
        String id=hash(actor.toLowerCase(Locale.ROOT)+":"+school.toLowerCase(Locale.ROOT)+":"+kind+":"+notifications.actionId());
        String digest;
        try { digest=hash(new ObjectMapper().writeValueAsString(payload)); }
        catch(Exception invalid) { throw new IllegalArgumentException("Invalid currency request"); }
        for(int attempt=0;attempt<5;attempt++) {
            CurrencyOperation previous=mongo.findById(id,CurrencyOperation.class);
            if(previous!=null) return matching(previous,digest);
            try {
                return transactions.execute(status->{
                    CurrencyOperation existing=mongo.findById(id,CurrencyOperation.class);
                    if(existing!=null) return matching(existing,digest);
                    var receipt=new CurrencyOperation();receipt.setId(id);receipt.setPayloadHash(digest);
                    receipt.setActor(actor);receipt.setSchool(school);receipt.setKind(kind);receipt.setCreatedAt(Instant.now());
                    // Insert first: an action cannot commit twice, even on a lost commit response.
                    mongo.insert(receipt);
                    work.accept(receipt);
                    mongo.save(receipt);
                    return receipt;
                });
            } catch(RuntimeException failure) {
                // An unknown commit outcome must NEVER rerun the transaction body automatically.
                if(label(failure,"UnknownTransactionCommitResult")) {
                    CurrencyOperation committed=mongo.findById(id,CurrencyOperation.class);
                    if(committed!=null) return matching(committed,digest);
                    throw unavailable("Currency outcome is unconfirmed. Retry the identical action; do not create a new action");
                }
                if(!(failure instanceof org.springframework.dao.DuplicateKeyException) && !label(failure,"TransientTransactionError")) throw failure;
                java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10L*(attempt+1)));
                if(attempt==4) throw new ResponseStatusException(HttpStatus.CONFLICT,"Currency changed concurrently. Retry the same action");
            }
        }
        throw unavailable("Currency transaction unavailable");
    }
    private CurrencyOperation matching(CurrencyOperation value,String digest) {
        if(!digest.equals(value.getPayloadHash())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Action ID already used for different currency data");
        return value;
    }
    static boolean label(Throwable failure,String label) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) if(cause instanceof MongoException mongo && mongo.hasErrorLabel(label)) return true;
        return false;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static ResponseStatusException unavailable(String message) {return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message);}
}
