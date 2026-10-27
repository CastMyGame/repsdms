package com.reps.demogcloud.security.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserInsertOnlyRepositoryTest {
    @Test void creationInsertsWithoutReplacingAnyExistingDocument() {
        var mongo=mock(MongoTemplate.class);var account=new UserModel();when(mongo.insert(account)).thenReturn(account);
        assertSame(account,new UserInsertOnlyRepositoryImpl(mongo).save(account));verify(mongo,never()).save(any(UserModel.class));
    }
    @Test void duplicateOrStaleSaveCannotOverwriteCredentialsMembershipOrBilling() {
        var mongo=mock(MongoTemplate.class);var account=new UserModel();account.setId("old-account");
        when(mongo.insert(account)).thenThrow(new DuplicateKeyException("synthetic"));
        assertThrows(OptimisticLockingFailureException.class,()->new UserInsertOnlyRepositoryImpl(mongo).save(account));
        verify(mongo,never()).save(any(UserModel.class));
    }
    @Test void ordinarySaveCannotAssignAnOwner() {
        var mongo=mock(MongoTemplate.class);var account=new UserModel();account.setTenantSchoolId("school-A");
        assertThrows(OptimisticLockingFailureException.class,()->new UserInsertOnlyRepositoryImpl(mongo).save(account));verifyNoInteractions(mongo);
    }
    @Test void httpCannotSetOrExposeMembershipMetadata() throws Exception {
        var json=new ObjectMapper();var account=json.readValue("{\"username\":\"synthetic@example.test\",\"tenantSchoolId\":\"forged\"}",UserModel.class);
        assertNull(account.getTenantSchoolId());account.setTenantSchoolId("school-A");assertFalse(json.writeValueAsString(account).contains("tenantSchoolId"));
    }
}
