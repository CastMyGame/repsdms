package com.reps.demogcloud.security.config;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import com.reps.demogcloud.security.models.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class StripeBillingMongoIntegrationTest {
    static MongoClient client;static MongoTemplate mongo;static String database;static AnnotationConfigApplicationContext app;static UserRepository users;
    @Configuration @EnableMongoRepositories(basePackages="com.reps.demogcloud.security.models")
    static class Config {@Bean MongoTemplate mongoTemplate(){return mongo;}}
    @BeforeAll static void start(){
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")));assertNull(uri.getDatabase());assertNull(uri.getCredential());
        database="reps_a7_billing_test_"+UUID.randomUUID().toString().replace("-","");client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);app=new AnnotationConfigApplicationContext(Config.class);users=app.getBean(UserRepository.class);
    }
    @AfterAll static void stop(){try{if(app!=null)app.close();if(mongo!=null && database.matches("reps_a7_billing_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){mongo.getCollection("users").deleteMany(new Document());mongo.insert(new Document("_id","account").append("username","synthetic@example.test").append("schoolName","Same School").append("tenantSchoolId","school-A").append("stripeCustomerId","customer").append("stripeSubscriptionId","subscription").append("stripePriceId","price").append("roles",List.of(new Document("role","TEACHER"))).append("retainedHistory","marker"),"users");}
    @Test void legacyEnabledDefaultAndCanonicalMetadataArePreserved(){assertEquals(1,users.confirmExistingBilling("account","customer","subscription",Instant.now()));var row=mongo.getCollection("users").find().first();assertEquals("school-A",row.getString("tenantSchoolId"));assertEquals("Same School",row.getString("schoolName"));assertEquals("price",row.getString("stripePriceId"));assertEquals("marker",row.getString("retainedHistory"));assertFalse(row.containsKey("enabled"));}
    @Test void disabledAccountCannotBeMarkedActive(){mongo.getCollection("users").updateOne(new Document("_id","account"),new Document("$set",new Document("enabled",false)));var before=mongo.getCollection("users").find().first();assertEquals(0,users.confirmExistingBilling("account","customer","subscription",Instant.now()));assertEquals(before,mongo.getCollection("users").find().first());}
    @Test void concurrentBillingRelinkFailsConditionalUpdate(){var before=mongo.getCollection("users").find().first();assertEquals(0,users.confirmExistingBilling("account","customer","other-subscription",Instant.now()));assertEquals(before,mongo.getCollection("users").find().first());}
}
