package com.reps.demogcloud.services.tenancy;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real repository fragment discovery and concurrent Mongo writes. No Boot/provider configuration. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class CanonicalAccountMongoIntegrationTest {
    static MongoClient client;static MongoTemplate mongo;static String database;
    static AnnotationConfigApplicationContext application;
    UserRepository users;EmployeeRepository employees;CanonicalAccountContext context;
    @Configuration @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    static class App {@Bean MongoTemplate mongoTemplate(){return mongo;}}
    @BeforeAll static void connect(){
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")),"Loopback only");
        assertNull(uri.getCredential(),"No deployed credentials");assertNull(uri.getDatabase(),"No named existing database");
        database="reps_a7_account_test_"+UUID.randomUUID().toString().replace("-","");
        client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);application=new AnnotationConfigApplicationContext(App.class);
    }
    @AfterAll static void close(){try{if(application!=null)application.close();if(mongo!=null && database.matches("reps_a7_account_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){
        for(String c:mongo.getCollectionNames())mongo.getCollection(c).deleteMany(new Document());
        users=application.getBean(UserRepository.class);employees=application.getBean(EmployeeRepository.class);
        context=new CanonicalAccountContext(users,employees,application.getBean(StudentRepository.class),application.getBean(SchoolRepository.class));
        for(String suffix:List.of("A","B")){
            var school=new School();school.setSchoolIdNumber("school-"+suffix);school.setSchoolName("Same School");mongo.insert(school);
            var account=new UserModel();account.setId("account-"+suffix);account.setUsername(suffix.toLowerCase()+"@example.test");account.setPassword("synthetic-password");
            account.setSchool("Same School");account.setRoles(Set.of(new RoleModel(null,"TEACHER")));account.setTenantSchoolId(school.getSchoolIdNumber());
            account.setStripeCustomerId("synthetic-customer");mongo.insert(account); // Explicit synthetic fixture seeding, not an application creation path.
            var e=new Employee();e.setEmployeeId("profile-"+suffix);e.setEmail(account.getUsername());e.setSchool("Same School");
            e.setTenantSchoolId(school.getSchoolIdNumber());e.setCurrency(19);e.setRoles(account.getRoles());mongo.insert(e);
        }
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void login(String email){var account=users.findByUsername(email);var principal=new AccountPrincipal(account,List.of());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));}
    Document raw(){return mongo.getCollection("users").find(new Document("_id","account-A")).first();}

    @Test void realSameNameMembershipAndConflictsUseExactPersistedIds(){
        login("a@example.test");assertEquals("school-A",context.requireCurrent().tenantSchoolId());
        login("b@example.test");assertEquals("school-B",context.requireCurrent().tenantSchoolId());
        mongo.getCollection("employee").updateOne(new Document("_id","profile-B"),new Document("$set",new Document("tenantSchoolId","school-A")));
        assertThrows(AccessDeniedException.class,context::requireCurrent);
    }
    @Test void genericStaleAccountSaveCannotReplaceOwnershipPasswordOrStripeData(){
        var before=raw();var stale=users.findById("account-A").orElseThrow();stale.setTenantSchoolId(null);stale.setPassword("stale");stale.setStripeCustomerId(null);
        assertThrows(OptimisticLockingFailureException.class,()->users.save(stale));assertEquals(before,raw());
    }
    @Test void legacyAccountStaysUnboundAndCannotBeReplaced(){
        mongo.getCollection("users").updateOne(new Document("_id","account-A"),new Document("$unset",new Document("tenantSchoolId","")));
        var before=raw();var stale=users.findById("account-A").orElseThrow();assertNull(stale.getTenantSchoolId());
        assertThrows(OptimisticLockingFailureException.class,()->users.save(stale));assertEquals(before,raw());
        login("a@example.test");assertThrows(AccessDeniedException.class,context::requireCurrent);
    }
    @Test void fixedPasswordActivationAndRoleUpdatesPreserveMembership(){
        users.updatePasswordIfEnabled("account-A","new-synthetic-password");
        users.updateRolesInSchool("account-A","Same School",Set.of(new RoleModel(null,"GUIDANCE")));
        users.updateEnabledInSchool("account-A","Same School",false);
        assertEquals("school-A",raw().getString("tenantSchoolId"));assertEquals("new-synthetic-password",raw().getString("password"));
        assertEquals("synthetic-customer",raw().getString("stripeCustomerId"));assertEquals(false,raw().get("enabled"));
    }
    @Test void disableRacingWithStaleSaveCannotReenableOrTransferAccount()throws Exception{
        var stale=users.findById("account-A").orElseThrow();stale.setTenantSchoolId(null);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var disable=pool.submit(()->{start.await();users.updateEnabledInSchool("account-A","Same School",false);return true;});
            var save=pool.submit(()->{start.await();assertThrows(OptimisticLockingFailureException.class,()->users.save(stale));return true;});
            start.countDown();assertTrue(disable.get(10,TimeUnit.SECONDS));assertTrue(save.get(10,TimeUnit.SECONDS));
            assertEquals(false,raw().get("enabled"));assertEquals("school-A",raw().getString("tenantSchoolId"));
        }finally{pool.shutdownNow();}
    }
    @Test void currentProfileSavePreservesBothMembershipsAndBalance(){
        var e=employees.findById("profile-A").orElseThrow();e.setTenantSchoolId(null);e.setFirstName("Updated");e.setCurrency(999);
        var saved=employees.save(e);assertEquals("school-A",saved.getTenantSchoolId());assertEquals(19,saved.getCurrency());
        login("a@example.test");assertEquals("school-A",context.requireCurrent().tenantSchoolId());
    }
    @Test void newLegacyAccountSaveStillInsertsButCannotAdoptCanonicalOwner(){
        var account=new UserModel();account.setId("new-account");account.setUsername("new@example.test");account.setSchool("Same School");
        users.save(account);assertFalse(mongo.getCollection("users").find(new Document("_id","new-account")).first().containsKey("tenantSchoolId"));
        var forged=new UserModel();forged.setTenantSchoolId("school-A");assertThrows(OptimisticLockingFailureException.class,()->users.save(forged));
    }
    @Test void invitationAndSingleUsePasswordSetupPreserveCanonicalOwnership()throws Exception{
        mongo.getCollection("users").updateOne(new Document("_id","account-A"),new Document("$set",new Document("passwordSetupRequired",true)));
        var sender=org.mockito.Mockito.mock(com.reps.demogcloud.services.email.EmailSenderService.class);
        var encoder=org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        org.mockito.Mockito.when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("new-synthetic-encoded");
        var service=new com.reps.demogcloud.security.services.AccountAccessService(users,mongo,encoder,sender,
                new org.springframework.mock.env.MockEnvironment().withProperty("reps.accounts.public-base-url","https://repsdev.vercel.app"),
                org.mockito.Mockito.mock(com.reps.demogcloud.services.WorkflowAccessService.class),org.mockito.Mockito.mock(com.reps.demogcloud.services.UserContextService.class));
        service.createdStaff(users.findById("account-A").orElseThrow());
        assertEquals("SENT",raw().getString("invitationStatus"));assertEquals("school-A",raw().getString("tenantSchoolId"));
        var body=org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(sender).sendSystemBulkEmail(org.mockito.ArgumentMatchers.eq("a@example.test"),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.anyString(),body.capture(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull());
        // Recover only this synthetic test's one-time token in memory; never print/persist it.
        String message=body.getValue();String marker="/set-password#";
        int begin=message.indexOf(marker)+marker.length();String credential=message.substring(begin,message.indexOf('"',begin));
        service.complete(null,credential,"new-synthetic-password-123");
        assertEquals("school-A",raw().getString("tenantSchoolId"));assertEquals(false,raw().get("passwordSetupRequired"));assertFalse(raw().containsKey("accessTicket"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.complete(null,credential,"new-synthetic-password-123"));
        login("a@example.test");assertEquals("school-A",context.requireCurrent().tenantSchoolId());
    }
    @Test void fixedAccountChangesLeaveGrantsReceiptsAndCaseHistoryUntouched(){
        var fixtures=new LinkedHashMap<String,Document>();
        fixtures.put("oauth2_authorized_clients",new Document("_id","a@example.test").append("gmailAuthorization",new Document("cipher","synthetic-encrypted-grant")));
        fixtures.put("currency_operations",new Document("_id","historic-receipt").append("payloadHash","synthetic-historic-hash"));
        fixtures.put("Punishments",new Document("_id","historic-case").append("assignmentWork",new Document("answers",List.of("historical")))
                .append("requiredNotifications",List.of(new Document("state","UNCERTAIN").append("notificationId","original-intent"))));
        fixtures.forEach((collection,document)->mongo.getCollection(collection).insertOne(document));
        users.updatePasswordIfEnabled("account-A","new-synthetic-encoded");users.updateEnabledInSchool("account-A","Same School",false);
        fixtures.forEach((collection,document)->assertEquals(document,mongo.getCollection(collection).find().first()));
        assertEquals("synthetic-customer",raw().getString("stripeCustomerId"));
    }
}
