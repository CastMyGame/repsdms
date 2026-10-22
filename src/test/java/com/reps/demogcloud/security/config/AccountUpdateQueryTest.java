package com.reps.demogcloud.security.config;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.security.models.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.mongodb.util.json.ParameterBindingDocumentCodec;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bind the actual repository BSON annotations using Spring Data's parser, without a database. */
class AccountUpdateQueryTest {
    @Test void passwordResetUpdatesOnlyPasswordAndRequiresEnabledStableAccountId() throws Exception {
        var method=UserRepository.class.getMethod("updatePasswordIfEnabled",String.class,String.class);
        var codec=new ParameterBindingDocumentCodec();
        var query=codec.decode(method.getAnnotation(Query.class).value(),new Object[]{"stable-id","synthetic-encoded"});
        var update=codec.decode(method.getAnnotation(Update.class).value(),new Object[]{"stable-id","synthetic-encoded"});
        assertEquals("stable-id",query.getString("_id"));
        assertEquals(false,query.get("enabled",Document.class).get("$ne"));
        assertEquals(new Document("$set",new Document("password","synthetic-encoded")),update);
    }
    @Test void activationUpdatePreservesCredentialsAndSupportsLegacySchoolMembership() throws Exception {
        var method=UserRepository.class.getMethod("updateEnabledInSchool",String.class,String.class,boolean.class);
        var codec=new ParameterBindingDocumentCodec();var args=new Object[]{"stable-id","Testing",false};
        var query=codec.decode(method.getAnnotation(Query.class).value(),args);
        var update=codec.decode(method.getAnnotation(Update.class).value(),args);
        assertEquals("stable-id",query.getString("_id"));
        assertEquals(List.of(new Document("school","Testing"),new Document("school",null).append("schoolName","Testing")),query.get("$or"));
        assertEquals(new Document("$set",new Document("enabled",false)),update);
    }
    @Test void staffArchiveUpdateIsBoundToSchoolAndCannotDeleteRecords() throws Exception {
        var method=EmployeeRepository.class.getMethod("updateArchivedInSchool",String.class,String.class,boolean.class);
        var codec=new ParameterBindingDocumentCodec();var args=new Object[]{"stable-id","Testing",true};
        assertEquals(new Document("_id","stable-id").append("school","Testing"),codec.decode(method.getAnnotation(Query.class).value(),args));
        assertEquals(new Document("$set",new Document("archived",true)),codec.decode(method.getAnnotation(Update.class).value(),args));
    }
}
