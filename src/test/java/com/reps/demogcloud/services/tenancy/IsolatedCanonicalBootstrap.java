package com.reps.demogcloud.services.tenancy;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import org.bson.Document;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import com.reps.demogcloud.security.utils.JwtTestSupport;
import java.util.*;

/** TEST CLASSPATH ONLY. Cannot be enabled by any property in the deployed application JAR. */
public final class IsolatedCanonicalBootstrap implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static MongoClient client; static String database; static Document marker;
    static final Map<String,Object> PROPERTIES=new HashMap<>();
    static synchronized void prepare(){
        if(client!=null)return;
        for(String key:List.of("GAE_ENV","GAE_APPLICATION","GAE_SERVICE","K_SERVICE","K_REVISION","GOOGLE_APPLICATION_CREDENTIALS"))if(System.getenv(key)!=null&&!System.getenv(key).isBlank())throw new IllegalStateException("Isolated activation refuses hosted/credential-bearing environment");
        String source=System.getenv("REPS_TENANCY_TEST_MONGODB_URI");
        if(!"mongodb://127.0.0.1:27029/?replicaSet=a7activation&directConnection=true&serverSelectionTimeoutMS=5000".equals(source))throw new IllegalStateException("Use the explicit disposable activation replica set");
        client=MongoClients.create(new ConnectionString(source));database="reps_a7_activation_"+UUID.randomUUID().toString().replace("-","");
        var db=client.getDatabase(database);var hello=db.runCommand(new Document("hello",1));
        if(!"a7activation".equals(hello.getString("setName"))||!hello.getList("hosts",String.class).equals(List.of("127.0.0.1:27029")))throw new IllegalStateException("Unknown activation replica set");
        marker=new Document("_id","isolated-canonical/v1").append("database",database).append("purpose","synthetic-test-only").append("nonce",UUID.randomUUID().toString());db.getCollection("a7_activation_control").insertOne(marker);
        PROPERTIES.put("spring.data.mongodb.uri",source.replace("/?","/"+database+"?"));
        PROPERTIES.put("spring.config.import","");PROPERTIES.put("spring.cloud.gcp.core.enabled","false");PROPERTIES.put("spring.cloud.gcp.secretmanager.enabled","false");PROPERTIES.put("spring.cloud.gcp.parametermanager.enabled","false");
        PROPERTIES.put("reps.tenancy.id-enforcement-enabled","false");PROPERTIES.put("reps.tenancy.isolated-test-activation","true");
        for(String part:List.of("core","domain","currency","email"))PROPERTIES.put("reps.tenancy."+part+"-preparation-enabled","true");
        for(String flag:List.of("routing.phase1-enabled","gmail.connection-enabled","notifications.enabled","notifications.dispatch-enabled"))PROPERTIES.put("reps.email."+flag,"true");
        PROPERTIES.put("reps.auth.jwt.signing-key",JwtTestSupport.key());PROPERTIES.put("reps.email.gmail.encryption-key",Base64.getEncoder().encodeToString("fake".repeat(8).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        PROPERTIES.put("reps.accounts.public-base-url","http://localhost:3000");PROPERTIES.put("reps.email.gmail.redirect-uri","http://localhost:3000/account/gmail/callback");
        PROPERTIES.put("mail.service.provider","hybrid");PROPERTIES.put("mail.service.user-emails.enabled","true");PROPERTIES.put("mail.service.system-emails.provider","smtp");
        PROPERTIES.put("auth.sso.google.enabled","false");PROPERTIES.put("spring.mail.host","127.0.0.1");PROPERTIES.put("spring.mail.port","1");
        PROPERTIES.put("spring.security.oauth2.client.registration.google.client-id","synthetic-client");PROPERTIES.put("spring.security.oauth2.client.registration.google.client-secret","synthetic-secret");PROPERTIES.put("spring.security.oauth2.client.registration.google.scope","openid,email,profile");
    }
    @Override public void initialize(ConfigurableApplicationContext context){prepare();context.getEnvironment().setActiveProfiles("tenant-isolated-verification");context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-canonical-full-app",PROPERTIES));}
}
