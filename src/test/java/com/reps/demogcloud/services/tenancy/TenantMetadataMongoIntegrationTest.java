package com.reps.demogcloud.services.tenancy;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, loopback-only synthetic BSON checks. Never application startup or deployed records. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class TenantMetadataMongoIntegrationTest {
    static MongoClient client;static com.mongodb.client.MongoDatabase db;static String name;
    @BeforeAll static void connect(){
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(host->host.matches("127\\.0\\.0\\.1:[0-9]+")),"Isolated tests require loopback only");
        assertNull(uri.getCredential(),"Isolated tests must not use deployed credentials");
        assertNull(uri.getDatabase(),"Do not name an existing database in the isolated test URI");
        name="reps_a7_test_"+UUID.randomUUID().toString().replace("-","");
        client=MongoClients.create(uri);db=client.getDatabase(name);
    }
    @AfterAll static void close(){try{if(db!=null && name.matches("reps_a7_test_[0-9a-f]{32}"))db.drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){
        for(String collection:db.listCollectionNames())db.getCollection(collection).deleteMany(new Document());
        db.getCollection("schools").insertMany(List.of(new Document("_id","school-A").append("schoolName","Same School"),new Document("_id","school-B").append("schoolName","Same School")));
        db.getCollection("cases").insertMany(List.of(new Document("_id","A").append("tenantSchoolId","school-A"),new Document("_id","B").append("tenantSchoolId","school-B")));
    }
    @Test void sameDisplayNameDoesNotExpandAnExactIdQuery(){assertEquals(2,db.getCollection("schools").countDocuments(new Document("schoolName","Same School")));assertEquals(1,db.getCollection("cases").countDocuments(new Document("tenantSchoolId","school-A")));}
    @Test void conditionalMetadataUpdateIsIdempotentAndPreservesEveryOtherField(){
        var original=new Document("_id","legacy").append("currency",19).append("receiptId","unchanged").append("cipher","synthetic-cipher").append("requiredNotifications",List.of(new Document("state","UNCERTAIN"))).append("answers",List.of("synthetic-answer"));
        db.getCollection("cases").insertOne(original);
        var condition=new Document("_id","legacy").append("tenantSchoolId",new Document("$exists",false));var change=new Document("$set",new Document("tenantSchoolId","school-A"));
        assertEquals(1,db.getCollection("cases").updateOne(condition,change).getModifiedCount());assertEquals(0,db.getCollection("cases").updateOne(condition,change).getMatchedCount());
        var saved=db.getCollection("cases").find(new Document("_id","legacy")).first();assertEquals("school-A",saved.remove("tenantSchoolId"));assertEquals(original,saved);
    }
    @Test void conflictingOwnerCannotMatchAnAbsentOwnerCondition(){assertEquals(0,db.getCollection("cases").updateOne(new Document("_id","B").append("tenantSchoolId",new Document("$exists",false)),new Document("$set",new Document("tenantSchoolId","school-A"))).getMatchedCount());assertEquals("school-B",db.getCollection("cases").find(new Document("_id","B")).first().getString("tenantSchoolId"));}
    @Test void missingLegacyOwnerIsNotIncludedInTenantQueries(){db.getCollection("cases").insertOne(new Document("_id","legacy").append("school","Same School"));assertEquals(1,db.getCollection("cases").countDocuments(new Document("tenantSchoolId","school-A")));}
    @Test void displayRenameDoesNotTransferCaseOwnership(){db.getCollection("schools").updateOne(new Document("_id","school-A"),new Document("$set",new Document("schoolName","Changed Display")));assertEquals(1,db.getCollection("cases").countDocuments(new Document("tenantSchoolId","school-A")));}
    @Test void immutableValidatorRejectsCrossTenantChangeBeforeMongoWrite(){assertThrows(org.springframework.security.access.AccessDeniedException.class,()->CanonicalOwnership.requireUnchanged(new CanonicalOwnership.StoredOwner("school-A"),new CanonicalOwnership.StoredOwner("school-B"),Set.of("school-A","school-B")));assertEquals("school-A",db.getCollection("cases").find(new Document("_id","A")).first().getString("tenantSchoolId"));}
}
