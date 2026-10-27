package com.reps.demogcloud.data;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import com.reps.demogcloud.models.student.Student;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual fragment writes against a disposable loopback-only DB; never Boot/cloud configuration. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class TenantOwnershipWriterMongoIntegrationTest {
    static MongoClient client;
    static MongoTemplate mongo;
    static String database;

    @BeforeAll static void connect() {
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")),"Loopback required");
        assertNull(uri.getCredential(),"No deployed credentials allowed");assertNull(uri.getDatabase(),"No existing database allowed");
        database="reps_a7_writer_test_"+UUID.randomUUID().toString().replace("-","");
        client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
    }
    @AfterAll static void close() {
        try { if(mongo!=null && database.matches("reps_a7_writer_test_[0-9a-f]{32}"))mongo.getDb().drop(); }
        finally { if(client!=null)client.close(); }
    }
    @BeforeEach void seed() {
        for(String name:mongo.getCollectionNames())mongo.getCollection(name).deleteMany(new Document());
        mongo.getCollection("students").insertOne(new Document("_id","child").append("school","Same School")
                .append("currency",20).append("firstName","Before")
                .append("retainedLegacyHistory",List.of("untouched")));
        mongo.getCollection("schools").insertMany(List.of(new Document("_id","school-A").append("schoolName","Same School"),
                new Document("_id","school-B").append("schoolName","Same School")));
    }
    Student read() {return mongo.findById("child",Student.class);}
    void bind(String id) {mongo.getCollection("students").updateOne(new Document("_id","child"),new Document("$set",new Document("tenantSchoolId",id)));}
    Student save(Student s) {return new StudentBalanceSafeRepositoryImpl(mongo).save(s);}

    @Test void legacySavesDoNotAddOwnershipAndPreserveUnmappedHistory() {
        var s=read();s.setFirstName("After");save(s);
        var raw=mongo.getCollection("students").find().first();
        assertFalse(raw.containsKey("tenantSchoolId"));assertEquals(List.of("untouched"),raw.get("retainedLegacyHistory"));
    }
    @Test void staleOwnerlessProfileCannotEraseBackfilledOwnerOrCurrency() {
        var stale=read();bind("school-A");
        mongo.updateFirst(new Query(),new Update().inc("currency",3),Student.class);
        stale.setFirstName("After");var saved=save(stale);
        assertEquals("school-A",saved.getTenantSchoolId());assertEquals(23,saved.getCurrency());
    }
    @Test void sameNameSchoolsCannotBeSubstitutedByOrdinarySave() {
        bind("school-A");var s=read();s.setTenantSchoolId("school-B");
        assertThrows(OptimisticLockingFailureException.class,()->save(s));assertEquals("school-A",read().getTenantSchoolId());
    }
    @Test void ownerInstalledBetweenReadAndUpdateReturnsConflict() {
        var stale=read();var spy=spy(mongo);
        doAnswer(call->{bind("school-A");return call.callRealMethod();}).when(spy)
                .findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Student.class));
        assertThrows(OptimisticLockingFailureException.class,()->new StudentBalanceSafeRepositoryImpl(spy).save(stale));
        assertEquals("Before",read().getFirstName());assertEquals("school-A",read().getTenantSchoolId());
    }
    @Test void profileAndAtomicCurrencyWritesDoNotClobberOneAnother() throws Exception {
        bind("school-A");var stale=read();stale.setFirstName("After");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var profile=pool.submit(()->{start.await();save(stale);return true;});
            var currency=pool.submit(()->{start.await();mongo.updateFirst(new Query(),new Update().inc("currency",3),Student.class);return true;});
            start.countDown();assertTrue(profile.get(10,TimeUnit.SECONDS));assertTrue(currency.get(10,TimeUnit.SECONDS));
            assertEquals(23,read().getCurrency());assertEquals("After",read().getFirstName());assertEquals("school-A",read().getTenantSchoolId());
        } finally {pool.shutdownNow();}
    }
    @Test void explicitNullOwnershipIsNotTreatedAsLegacyMissing() {
        bind(null);assertThrows(OptimisticLockingFailureException.class,()->save(read()));
        assertTrue(mongo.getCollection("students").find().first().containsKey("tenantSchoolId"));
    }
    @Test void orphanedOwnerDoesNotResolveByAnIdenticalSchoolName() {
        bind("nonexistent");assertThrows(OptimisticLockingFailureException.class,()->save(read()));
        assertEquals("nonexistent",read().getTenantSchoolId());assertEquals("Before",read().getFirstName());
    }
}
