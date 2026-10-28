package com.reps.demogcloud.services.currency;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.config.*;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.*;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import com.reps.demogcloud.services.tenancy.CanonicalAccountContext;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SecurityConfig, JWT/account context, Mongo transactions and replay engine.
 * Test-only wallet/case callback; production CurrencyService is deliberately disconnected. */
@EnabledIfEnvironmentVariable(named="REPS_CURRENCY_TEST_MONGODB_URI",matches=".+")
class CanonicalCurrencyMongoSecurityTest {
    static MongoClient client;static MongoTemplate mongo;static String database;
    static AnnotationConfigWebApplicationContext app;static MockMvc mvc;static JwtUtils jwt;
    static MockEnvironment bridgeEnv;static volatile boolean failAfterDebit;static AtomicInteger bodies=new AtomicInteger();
    static final String ACTION="00000000-0000-4000-8000-000000000001";
    @Configuration @EnableWebMvc @EnableWebSecurity
    @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    @Import({SecurityConfig.class,JwtFilterRequest.class,CustomUserDetailsService.class,CanonicalAccountContext.class,GlobalExceptionHandler.class,Probe.class})
    static class Config {
        @Bean MongoTemplate mongoTemplate(){return mongo;}
        @Bean JwtRevocationStore revocations(){return new JwtRevocationStore(mongo);}
        @Bean JwtUtils jwt(JwtRevocationStore store){return new JwtUtils(JwtTestSupport.environment(),store);}
        @Bean CustomOAuth2UserService oidc(){return mock(CustomOAuth2UserService.class);}
        @Bean OAuth2LoginSuccessHandler success(){return mock(OAuth2LoginSuccessHandler.class);}
        @Bean OAuth2LoginFailureHandler failure(){return mock(OAuth2LoginFailureHandler.class);}
        @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
        @Bean RequiredNotificationService notifications(){var mock=mock(RequiredNotificationService.class);when(mock.actionId()).thenCallRealMethod();return mock;}
        @Bean CanonicalCurrencyTransactions canonical(CanonicalAccountContext accounts,RequiredNotificationService notifications){return new CanonicalCurrencyTransactions(mongo,accounts,notifications,bridgeEnv);}
    }
    record Award(int amount){}
    @RestController static class Probe {
        CanonicalCurrencyTransactions canonical;final CanonicalAccountContext accounts;final RequiredNotificationService notifications;
        Probe(CanonicalCurrencyTransactions canonical,CanonicalAccountContext accounts,RequiredNotificationService notifications){this.canonical=canonical;this.accounts=accounts;this.notifications=notifications;}
        @PostMapping("/__test__/currency/{kind}") Object send(@PathVariable String kind,@RequestBody Award payload){return canonical.execute(kind,payload,r->wallet(r,payload));}
        @PostMapping("/__test__/legacy") Object legacy(@RequestBody Award payload){
            var users=mock(UserContextService.class);var actor=accounts.requireCurrent();var account=mongo.findById(actor.accountId(),UserModel.class);
            when(users.getCurrentStaffEmail()).thenReturn(account.getUsername());when(users.getCurrentUserSchool()).thenReturn(account.getSchool());
            return new CurrencyTransactions(mongo,users,notifications).execute("AWARD",payload,r->wallet(r,payload));
        }
        void wallet(CurrencyOperation receipt,Award payload){
            bodies.incrementAndGet();if(payload.amount()<=0)throw new IllegalArgumentException("Positive award required");
            var actor=accounts.requireCurrent();
            Query teacher=Query.query(Criteria.where("_id").is(actor.profileId()).and("tenantSchoolId").is(actor.tenantSchoolId()).and("currency").gte(payload.amount()));
            if(mongo.updateFirst(teacher,new Update().inc("currency",-payload.amount()),Employee.class).getMatchedCount()!=1)throw new IllegalArgumentException("Insufficient funds");
            if(failAfterDebit)throw new IllegalStateException("Synthetic failure after debit");
            var child=mongo.findOne(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId())),Student.class);
            mongo.updateFirst(Query.query(Criteria.where("_id").is(child.getStudentIdNumber()).and("tenantSchoolId").is(actor.tenantSchoolId())),new Update().inc("currency",payload.amount()),Student.class);
            receipt.getStudentIds().add(child.getStudentIdNumber());receipt.getAmounts().put(child.getStudentIdNumber(),payload.amount());
            mongo.insert(new Document("_id",receipt.getId()).append("tenantSchoolId",actor.tenantSchoolId()).append("requiredNotifications",List.of(new Document("state","PENDING"))),"synthetic_recognitions");
        }
    }
    @BeforeAll static void start(){
        var uri=new ConnectionString(System.getenv("REPS_CURRENCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")));assertNull(uri.getCredential());assertNull(uri.getDatabase());
        database="reps_currency_bridge_test_"+UUID.randomUUID().toString().replace("-","");client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
        bridgeEnv=new MockEnvironment();bridgeEnv.setActiveProfiles("tenant-isolated-verification");
        app=new AnnotationConfigWebApplicationContext();app.setServletContext(new MockServletContext());app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated",Map.of("auth.sso.google.enabled","false")));
        app.register(Config.class);app.refresh();jwt=app.getBean(JwtUtils.class);mvc=MockMvcBuilders.webAppContextSetup(app).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
    }
    @AfterAll static void stop(){try{if(app!=null)app.close();if(mongo!=null && database.matches("reps_currency_bridge_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){
        app.getBean(Probe.class).canonical=app.getBean(CanonicalCurrencyTransactions.class);failAfterDebit=false;bodies.set(0);for(String name:mongo.getCollectionNames())mongo.getCollection(name).deleteMany(new Document());
        for(String collection:List.of("currency_operations","currency_replay_manifests","synthetic_recognitions"))if(!mongo.collectionExists(collection))mongo.createCollection(collection);
        for(String school:List.of("A","B")){
            var s=new School();s.setSchoolIdNumber("school-"+school);s.setSchoolName("Same School");mongo.insert(s);
            account("teacher"+school,"TEACHER",school);account("admin"+school,"ADMIN",school);account("child"+school,"STUDENT",school);
        }
        install(manifest());
    }
    static void account(String id,String role,String school){
        var u=new UserModel();u.setId(id);u.setUsername(id+"@example.test");u.setPassword("synthetic");u.setSchool("Same School");u.setTenantSchoolId("school-"+school);u.setRoles(Set.of(new RoleModel(null,role)));mongo.insert(u);
        if(role.equals("STUDENT")){var s=new Student();s.setStudentIdNumber(id);s.setStudentEmail(u.getUsername());s.setSchool(u.getSchool());s.setTenantSchoolId(u.getTenantSchoolId());s.setCurrency(0);mongo.insert(s);}
        else{var e=new Employee();e.setEmployeeId(id);e.setEmail(u.getUsername());e.setSchool(u.getSchool());e.setTenantSchoolId(u.getTenantSchoolId());e.setRoles(u.getRoles());e.setCurrency(20);mongo.insert(e);}
    }
    Document manifest(){
        var actors=new ArrayList<Document>();
        for(String school:List.of("A","B"))for(String role:List.of("teacher","admin")){
            String id=role+school;actors.add(new Document("accountId",id).append("profileId",id).append("tenantSchoolId","school-"+school).append("historyComplete",true)
                    .append("evidenceReference","synthetic-reviewed-onboarding").append("legacyNamespaces",List.of(new Document("actor",(id+"@example.test").toLowerCase(Locale.ROOT)).append("school","same school"))));
        }
        return new Document("_id","review-1").append("schema","reps-currency-replay/v1").append("historyReviewed",true).append("legacyWritersDrained",true)
                .append("reviewReference","synthetic-review").append("actors",actors).append("receipts",new ArrayList<Document>());
    }
    void install(Document manifest){mongo.getCollection(CurrencyReplayManifest.COLLECTION).deleteMany(new Document());mongo.insert(manifest,CurrencyReplayManifest.COLLECTION);
        bridgeEnv.setProperty("reps.tenancy.currency-replay-manifest-id",manifest.getString("_id"));bridgeEnv.setProperty("reps.tenancy.currency-replay-manifest-sha256",CurrencyReplayManifest.fingerprint(manifest));}
    String token(String id){return jwt.generateToken(app.getBean(CustomUserDetailsService.class).loadUserByUsername(id+"@example.test"));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String id,String action,String body){return post("/__test__/currency/AWARD").header("Authorization","Bearer "+token(id)).header("X-REPS-Action-Id",action).contentType(MediaType.APPLICATION_JSON).content(body);}
    void award(String id)throws Exception{mvc.perform(request(id,ACTION,"{\"amount\":3}")).andExpect(status().isOk());}
    int balance(String id){return mongo.findById(id,Employee.class).getCurrency();}
    Document history()throws Exception{
        mvc.perform(post("/__test__/legacy").header("Authorization","Bearer "+token("teacherA")).header("X-REPS-Action-Id",ACTION).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":3}")).andExpect(status().isOk());
        return mongo.getCollection("currency_operations").find().first();
    }
    void reviewed(Document receipt){var m=manifest();m.getList("receipts",Document.class).add(new Document("receiptId",receipt.getString("_id")).append("accountId","teacherA").append("tenantSchoolId","school-A").append("recordSha256",CurrencyReplayManifest.fingerprint(receipt)));install(m);}
    @Test void historicalRetryBeforeAndAfterAdoptionDoesNotRewriteOrRepeatTransfer()throws Exception{
        var before=history();reviewed(before);award("teacherA");award("teacherA");
        assertEquals(before,mongo.getCollection("currency_operations").find().first());assertEquals(1,mongo.getCollection("currency_operations").countDocuments());assertEquals(17,balance("teacherA"));assertEquals(1,bodies.get());
    }
    @Test void currentMembershipCannotAuthorizeUnreviewedHistoricalReceipt()throws Exception{history();mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(17,balance("teacherA"));assertEquals(1,bodies.get());}
    @Test void changedHistoricalReceiptFingerprintBlocksReplay()throws Exception{var old=history();reviewed(old);mongo.getCollection("currency_operations").updateOne(new Document("_id",old.get("_id")),new Document("$set",new Document("teacherBalance",999)));mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(1,bodies.get());}
    @Test void canonicalRetryAfterEngineRestartCommitsOnlyOnce()throws Exception{award("teacherA");var restarted=new CanonicalCurrencyTransactions(mongo,app.getBean(CanonicalAccountContext.class),app.getBean(RequiredNotificationService.class),bridgeEnv);app.getBean(Probe.class).canonical=restarted;award("teacherA");assertEquals(17,balance("teacherA"));assertEquals(1,bodies.get());assertEquals(1,mongo.getCollection("synthetic_recognitions").countDocuments());}
    @Test void sameNameSchoolsHaveSeparateCanonicalReceipts()throws Exception{award("teacherA");award("teacherB");assertEquals(17,balance("teacherA"));assertEquals(17,balance("teacherB"));assertEquals(1,mongo.getCollection("currency_operations").countDocuments(new Document("tenantSchoolId","school-A")));assertEquals(1,mongo.getCollection("currency_operations").countDocuments(new Document("tenantSchoolId","school-B")));}
    @Test void foreignHistoricalReceiptEvidenceCannotBeBorrowed()throws Exception{var old=history();var m=manifest();m.getList("receipts",Document.class).add(new Document("receiptId",old.getString("_id")).append("accountId","teacherB").append("tenantSchoolId","school-B").append("recordSha256",CurrencyReplayManifest.fingerprint(old)));install(m);mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(1,bodies.get());}
    @Test void missingOrTamperedManifestBlocksNewMoney()throws Exception{mongo.getCollection(CurrencyReplayManifest.COLLECTION).deleteMany(new Document());mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(20,balance("teacherA"));var m=manifest();install(m);mongo.getCollection(CurrencyReplayManifest.COLLECTION).updateOne(new Document("_id","review-1"),new Document("$set",new Document("reviewReference","changed")));mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(0,bodies.get());}
    @Test void overlappingLegacyNamespacesAreAmbiguousEvenForSameNameSchools()throws Exception{var m=manifest();var actors=m.getList("actors",Document.class);actors.get(2).put("legacyNamespaces",actors.get(0).get("legacyNamespaces"));install(m);mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(0,bodies.get());}
    @Test void payloadMismatchNeverCreatesAnotherReceipt()throws Exception{award("teacherA");mvc.perform(request("teacherA",ACTION,"{\"amount\":4}")).andExpect(status().isConflict());assertEquals(17,balance("teacherA"));assertEquals(1,mongo.getCollection("currency_operations").countDocuments());}
    @Test void transactionFailureRollsBackReceiptDebitCreditAndNotification(){failAfterDebit=true;try{mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isInternalServerError());}catch(Exception failure){throw new AssertionError(failure);}assertEquals(20,balance("teacherA"));assertEquals(0,mongo.findById("childA",Student.class).getCurrency());assertEquals(0,mongo.getCollection("currency_operations").countDocuments());assertEquals(0,mongo.getCollection("synthetic_recognitions").countDocuments());}
    @Test void concurrentCanonicalRetriesNeverDoubleCredit()throws Exception{
        var pool=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);var tasks=new ArrayList<Future<Integer>>();
        try{for(int i=0;i<4;i++)tasks.add(pool.submit(()->{start.await();return mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andReturn().getResponse().getStatus();}));start.countDown();for(var task:tasks)assertTrue(Set.of(200,409).contains(task.get(30,TimeUnit.SECONDS)));award("teacherA");}
        finally{pool.shutdownNow();}assertEquals(17,balance("teacherA"));assertEquals(3,mongo.findById("childA",Student.class).getCurrency());assertEquals(1,mongo.getCollection("currency_operations").countDocuments());assertEquals(1,mongo.getCollection("synthetic_recognitions").countDocuments());
    }
    @Test void anonymousStudentDisabledAndMissingMembershipCannotStartTransactions()throws Exception{
        mvc.perform(post("/__test__/currency/AWARD").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":3}")).andExpect(status().isUnauthorized());
        mvc.perform(request("childA",ACTION,"{\"amount\":3}")).andExpect(status().isForbidden());
        String token=token("teacherA");mongo.getCollection("users").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("enabled",false)));
        mvc.perform(post("/__test__/currency/AWARD").header("Authorization","Bearer "+token).header("X-REPS-Action-Id",ACTION).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":3}")).andExpect(status().isUnauthorized());
        mongo.getCollection("users").updateOne(new Document("_id","teacherB"),new Document("$unset",new Document("tenantSchoolId","")));mvc.perform(request("teacherB",ACTION,"{\"amount\":3}")).andExpect(status().isForbidden());assertEquals(0,bodies.get());
    }
    @Test void insufficientFundsRollsBackReservedReceipt()throws Exception{mvc.perform(request("teacherA",ACTION,"{\"amount\":30}")).andExpect(status().isBadRequest());assertEquals(20,balance("teacherA"));assertEquals(0,mongo.getCollection("currency_operations").countDocuments());}
    @Test void forgedCanonicalReceiptOwnerBlocksReplay()throws Exception{award("teacherA");mongo.getCollection("currency_operations").updateOne(new Document("tenantSchoolId","school-A"),new Document("$set",new Document("tenantSchoolId","school-B")));mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(17,balance("teacherA"));}
    @Test void concurrentHistoricalRetriesNeverRunBodyAgain()throws Exception{
        var old=history();reviewed(old);var pool=Executors.newFixedThreadPool(3);
        try{var tasks=new ArrayList<Future<Integer>>();for(int i=0;i<3;i++)tasks.add(pool.submit(()->mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andReturn().getResponse().getStatus()));for(var task:tasks)assertEquals(200,task.get(30,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}assertEquals(1,bodies.get());assertEquals(old,mongo.getCollection("currency_operations").find().first());
    }
    @Test void incompleteHistoryDeclarationBlocksEvenAbsentReceipts()throws Exception{var m=manifest();m.getList("actors",Document.class).get(0).put("historyComplete",false);install(m);mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(0,bodies.get());}
    @Test void conflictingHistoricalNamespacesWithTwoReceiptsRequireResolution()throws Exception{
        var old=history();var another=new Document(old);String otherId=CurrencyReplayManifest.sha256("teachera@example.test:previous school:AWARD:"+ACTION);another.put("_id",otherId);another.put("school","Previous School");mongo.insert(another,"currency_operations");
        var m=manifest();m.getList("actors",Document.class).get(0).put("legacyNamespaces",List.of(new Document("actor","teachera@example.test").append("school","same school"),new Document("actor","teachera@example.test").append("school","previous school")));install(m);
        mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(1,bodies.get());assertEquals(2,mongo.getCollection("currency_operations").countDocuments());
    }

    @Test @SuppressWarnings({"unchecked","rawtypes"}) void lostCommitResponseUsesLookupWithoutRepeatingBody()throws Exception{
        var engine=app.getBean(CanonicalCurrencyTransactions.class);
        var original=(org.springframework.transaction.support.TransactionTemplate)org.springframework.test.util.ReflectionTestUtils.getField(engine,"transactions");
        var fault=spy(original);var once=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{Object result=call.callRealMethod();if(once.getAndSet(false)){var error=new com.mongodb.MongoException("Synthetic lost commit response");error.addLabel("UnknownTransactionCommitResult");throw error;}return result;}).when(fault).execute(any(org.springframework.transaction.support.TransactionCallback.class));
        org.springframework.test.util.ReflectionTestUtils.setField(engine,"transactions",fault);
        try{award("teacherA");assertEquals(1,bodies.get());assertEquals(17,balance("teacherA"));}
        finally{org.springframework.test.util.ReflectionTestUtils.setField(engine,"transactions",original);}
    }
    @Test @SuppressWarnings({"unchecked","rawtypes"}) void unresolvedCommitOutcomeDoesNotRerunBody()throws Exception{
        var engine=app.getBean(CanonicalCurrencyTransactions.class);
        var original=(org.springframework.transaction.support.TransactionTemplate)org.springframework.test.util.ReflectionTestUtils.getField(engine,"transactions");
        var fault=spy(original);var error=new com.mongodb.MongoException("Synthetic unknown commit");error.addLabel("UnknownTransactionCommitResult");doThrow(error).when(fault).execute(any(org.springframework.transaction.support.TransactionCallback.class));
        org.springframework.test.util.ReflectionTestUtils.setField(engine,"transactions",fault);
        try{mvc.perform(request("teacherA",ACTION,"{\"amount\":3}")).andExpect(status().isServiceUnavailable());assertEquals(0,bodies.get());assertEquals(20,balance("teacherA"));}
        finally{org.springframework.test.util.ReflectionTestUtils.setField(engine,"transactions",original);}
    }

}
