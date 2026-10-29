package com.reps.demogcloud.services.currency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.*;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import com.reps.demogcloud.services.tenancy.CanonicalAccountContext;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Isolated-only transaction engine; deliberately NOT substituted into legacy CurrencyService. */
@Service
@ConditionalOnProperty(name="reps.tenancy.currency-preparation-enabled",havingValue="true")
public class CanonicalCurrencyTransactions {
    private static final String RECEIPTS="currency_operations";
    private final MongoTemplate mongo;
    private final CanonicalAccountContext accounts;
    private final RequiredNotificationService notifications;
    private final Environment env;
    private final TransactionTemplate transactions;
    public CanonicalCurrencyTransactions(MongoTemplate mongo,CanonicalAccountContext accounts,RequiredNotificationService notifications,Environment env){
        this.mongo=mongo;this.accounts=accounts;this.notifications=notifications;this.env=env;
        var options=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).readPreference(ReadPreference.primary()).build();
        transactions=new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory(),options));
    }
    @jakarta.annotation.PostConstruct public void isolatedOnly(){
        if(!Arrays.asList(env.getActiveProfiles()).contains("tenant-isolated-verification"))throw new IllegalStateException("Canonical currency preparation requires isolated verification; production cutover is not implemented");
    }
    public CurrencyOperation execute(String kind,Object payload,Consumer<CurrencyOperation> work){
        isolatedOnly();
        if(!Set.of("AWARD","REDEEM","RESET_STAFF_BUDGETS").contains(kind))throw new IllegalArgumentException("Unsupported currency operation");
        var actor=accounts.requireCurrent();if(actor.roles().contains("STUDENT"))throw new AccessDeniedException("Staff currency access required");
        if(kind.equals("RESET_STAFF_BUDGETS") && !actor.roles().contains("ADMIN"))throw new AccessDeniedException("Administrator currency access required");
        String action=notifications.actionId();
        String id="v2-"+CurrencyReplayManifest.sha256(json(List.of("reps-currency/v2",actor.tenantSchoolId(),actor.accountId(),kind,action)));
        // Preserve the v1 serialization algorithm when comparing historical payload hashes.
        String digest=CurrencyReplayManifest.sha256(json(payload));
        var hello=mongo.executeCommand(new Document("hello",1));
        if((!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg"))) || !hello.containsKey("logicalSessionTimeoutMinutes")
                || ((Number)hello.getOrDefault("maxWireVersion",0)).intValue()<9)throw CurrencyReplayManifest.blocked();
        for(int attempt=0;attempt<5;attempt++)try{
            return transactions.execute(status->{
                // Re-read persisted authentication/membership inside the same transaction snapshot.
                if(!actor.equals(accounts.requireCurrent()))throw CurrencyReplayManifest.blocked();
                var manifest=manifest();var candidates=manifest.candidates(actor,kind,action);
                CurrencyOperation replay=resolve(manifest,candidates,id,digest,kind,actor);
                if(replay!=null)return replay;
                var account=mongo.findOne(Query.query(Criteria.where("_id").is(actor.accountId()).and("tenantSchoolId").is(actor.tenantSchoolId())),UserModel.class);
                if(account==null)throw CurrencyReplayManifest.blocked();
                var receipt=new CurrencyOperation();receipt.setId(id);receipt.setPayloadHash(digest);receipt.setActor(account.getUsername());
                receipt.setKind(kind);receipt.setCreatedAt(Instant.now());
                // Display only; canonical ownership is stored separately and never taken from a name.
                receipt.setSchool(account.getSchool());
                Document raw=new Document();mongo.getConverter().write(receipt,raw);
                raw.append("receiptVersion",2).append("tenantSchoolId",actor.tenantSchoolId()).append("actorAccountId",actor.accountId())
                        .append("actorProfileId",actor.profileId()).append("state","RESERVED");
                mongo.insert(raw,RECEIPTS); // unique _id serializes concurrent attempts
                com.reps.demogcloud.services.tenancy.CanonicalAuthorizationFence.actor(mongo,actor);
                work.accept(receipt); // Domain caller validates the entire batch and fences target relationships before wallet changes
                if(!id.equals(receipt.getId()) || !digest.equals(receipt.getPayloadHash()) || !kind.equals(receipt.getKind())
                        || !account.getUsername().equals(receipt.getActor()) || !Objects.equals(account.getSchool(),receipt.getSchool()))throw CurrencyReplayManifest.blocked();
                var query=Query.query(Criteria.where("_id").is(id).and("tenantSchoolId").is(actor.tenantSchoolId()).and("actorAccountId").is(actor.accountId()).and("state").is("RESERVED"));
                var update=new Update().set("caseIds",receipt.getCaseIds()).set("studentIds",receipt.getStudentIds()).set("employeeIds",receipt.getEmployeeIds())
                        .set("teacherBalance",receipt.getTeacherBalance()).set("amounts",receipt.getAmounts()).set("state","COMMITTED");
                if(mongo.updateFirst(query,update,RECEIPTS).getMatchedCount()!=1)throw CurrencyReplayManifest.blocked();
                return receipt;
            });
        }catch(RuntimeException failure){
            if(CurrencyTransactions.label(failure,"UnknownTransactionCommitResult")){
                // Lookup only: never execute the body again on an uncertain commit response.
                var manifest=manifest();var committed=resolve(manifest,manifest.candidates(actor,kind,action),id,digest,kind,actor);
                if(committed!=null)return committed;throw CurrencyReplayManifest.blocked();
            }
            if(!(failure instanceof org.springframework.dao.DuplicateKeyException) && !CurrencyTransactions.label(failure,"TransientTransactionError"))throw failure;
            if(attempt==4)throw new ResponseStatusException(HttpStatus.CONFLICT,"Currency changed concurrently; retry the identical action");
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10L*(attempt+1)));
        }
        throw CurrencyReplayManifest.blocked();
    }
    private CurrencyReplayManifest manifest(){
        String id=env.getProperty("reps.tenancy.currency-replay-manifest-id");
        if(id==null || id.isBlank())throw CurrencyReplayManifest.blocked();
        return new CurrencyReplayManifest(mongo.findById(id,Document.class,CurrencyReplayManifest.COLLECTION),env.getProperty("reps.tenancy.currency-replay-manifest-sha256"));
    }
    private CurrencyOperation resolve(CurrencyReplayManifest manifest,List<String> candidates,String id,String digest,String kind,CanonicalAccountContext.Actor actor){
        var history=new ArrayList<Document>();for(String legacy:candidates){var row=mongo.findById(legacy,Document.class,RECEIPTS);if(row!=null)history.add(row);}
        Document canonical=mongo.findById(id,Document.class,RECEIPTS);
        if(history.size()>1 || !history.isEmpty() && canonical!=null)throw CurrencyReplayManifest.blocked();
        if(!history.isEmpty()){
            Document row=history.get(0);manifest.verifyReceipt(row,actor);
            if(!kind.equals(row.getString("kind")))throw CurrencyReplayManifest.blocked();return matching(row,digest);
        }
        if(canonical==null)return null;
        if(!Integer.valueOf(2).equals(canonical.get("receiptVersion")) || !"COMMITTED".equals(canonical.getString("state"))
                || !actor.accountId().equals(canonical.getString("actorAccountId")) || !actor.profileId().equals(canonical.getString("actorProfileId"))
                || !actor.tenantSchoolId().equals(canonical.getString("tenantSchoolId")) || !kind.equals(canonical.getString("kind")))throw CurrencyReplayManifest.blocked();
        return matching(canonical,digest);
    }
    private CurrencyOperation matching(Document row,String digest){
        if(!digest.equals(row.getString("payloadHash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Action ID already used for different currency data");
        return mongo.getConverter().read(CurrencyOperation.class,row);
    }
    private static String json(Object value){try{return new ObjectMapper().writeValueAsString(value);}catch(Exception invalid){throw new IllegalArgumentException("Invalid currency request");}}
}
