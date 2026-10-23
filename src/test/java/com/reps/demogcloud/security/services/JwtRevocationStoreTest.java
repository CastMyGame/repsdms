package com.reps.demogcloud.security.services;
import com.mongodb.client.result.UpdateResult;
import org.bson.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class JwtRevocationStoreTest {
 @Test void onlyDigestAndExpirationArePersistedAndQueriesUseSharedCollection() {
  var mongo=mock(MongoTemplate.class);var store=new JwtRevocationStore(mongo);String token="synthetic-bearer-not-a-credential";Date expiry=new Date(123456789);
  when(mongo.upsert(any(Query.class),any(Update.class),eq(JwtRevocationStore.COLLECTION))).thenReturn(UpdateResult.acknowledged(0,0L,new BsonString("digest")));
  assertTrue(store.revoke(token,expiry));
  var q=ArgumentCaptor.forClass(Query.class);var u=ArgumentCaptor.forClass(Update.class);
  verify(mongo).upsert(q.capture(),u.capture(),eq(JwtRevocationStore.COLLECTION));
  String id=q.getValue().getQueryObject().getString("_id");assertTrue(id.matches("[a-f0-9]{64}"));assertFalse(q.getValue().toString().contains(token));assertFalse(u.getValue().toString().contains(token));
  assertEquals(Set.of("expiresAt"),((Document)u.getValue().getUpdateObject().get("$setOnInsert")).keySet());
  when(mongo.exists(any(Query.class),eq(JwtRevocationStore.COLLECTION))).thenReturn(true);assertTrue(store.contains(token));
 }
 @Test void repeatedOrRacingClaimsCannotRenewAgain() {
  var mongo=mock(MongoTemplate.class);var store=new JwtRevocationStore(mongo);
  when(mongo.upsert(any(Query.class),any(Update.class),anyString())).thenReturn(UpdateResult.acknowledged(1,0L,null)).thenThrow(new org.springframework.dao.DuplicateKeyException("synthetic race"));
  assertFalse(store.revoke("synthetic",new Date()));assertFalse(store.revoke("synthetic",new Date()));
 }
 @Test void revocationStorageFailureIsNotTreatedAsAnUnrevokedToken() {
  var mongo=mock(MongoTemplate.class);var store=new JwtRevocationStore(mongo);
  when(mongo.exists(any(Query.class),anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"));
  assertThrows(org.springframework.dao.DataAccessException.class,()->store.contains("synthetic"));
 }
}
