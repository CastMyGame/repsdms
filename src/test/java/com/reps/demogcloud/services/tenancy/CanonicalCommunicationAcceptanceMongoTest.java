package com.reps.demogcloud.services.tenancy;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.springframework.http.MediaType;

class CanonicalCommunicationAcceptanceMongoTest extends CanonicalAcceptanceFixture {
 Document intent(String id){return mongo.getCollection("Punishments").find(new Document("_id",id)).first().getList("requiredNotifications",Document.class).get(0);}
 void state(String id,String value,boolean safe){mongo.getCollection("Punishments").updateOne(new Document("_id",id),new Document("$set",new Document("requiredNotifications.0.state",value).append("requiredNotifications.0.retrySafe",safe)));}
 com.reps.demogcloud.services.email.RequiredNotificationService restartedWorker(){var service=new com.reps.demogcloud.services.email.RequiredNotificationService(mongo,app.getEnvironment(),app.getBean(com.reps.demogcloud.data.StudentRepository.class),app.getBean(com.reps.demogcloud.services.UserContextService.class),router,app.getBean(com.reps.demogcloud.services.email.EmailTemplateBuilderService.class));service.setCanonicalEmailOwnership(app.getBean(CanonicalEmailOwnership.class));org.springframework.test.util.ReflectionTestUtils.setField(service,"workflow",app.getBean(CanonicalWorkflowAccess.class));return service;}
 @Test void notificationStatusRetryResolutionAndRestartAreActualOwnedPaths()throws Exception{
  String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString()),notification=intent(id).getString("notificationId"),base="/email/v1/notifications/punishment/"+id;
  for(String path:List.of("/email/v1/notifications",base)){var r=call("GET",path,"teacherA",null);success(r);assertTrue(r.getResponse().getContentAsString().contains(id));if(path.equals(base))denied("GET",path,"teacherB",null);else {var b=call("GET",path,"teacherB",null);success(b);assertFalse(b.getResponse().getContentAsString().contains(id));}evidence("GET",path,"notificationStatusRetryResolutionAndRestartAreActualOwnedPaths","Owned parent + current authorized actor","Actual persisted obligation displayed","Other school denied or scoped list omits it","No raw provider credentials returned");}
  state(id,"FAILED",true);String retry=base+"/"+notification+"/retry";denied("POST",retry,"teacherB",null);denied("POST",retry,"otherA",null);success(call("POST",retry,"teacherA",null));org.springframework.security.core.context.SecurityContextHolder.clearContext();var restarted=restartedWorker();restarted.dispatchPending();restarted.dispatchPending();assertEquals("ACCEPTED",intent(id).getString("state"));verify(smtp,times(1)).send(any(jakarta.mail.internet.MimeMessage.class));
  assertEquals(409,call("POST",retry,"teacherA",null).getResponse().getStatus());
  evidence("POST",retry,"notificationStatusRetryResolutionAndRestartAreActualOwnedPaths","Current parent/intent actor ownership; retry only known safe failure","Retry queued and fresh worker accepted once","Other school/unrelated actor denied; accepted message cannot retry","New worker object uses same persisted data and real router with mocked SMTP");
  state(id,"UNCERTAIN",false);String resolve=base+"/"+notification+"/resolve",reason="{\"reason\":\"Synthetic operator-confirmed outcome\"}";denied("POST",resolve,"adminB",reason);denied("POST",resolve,"teacherA",reason);success(call("POST",resolve,"adminA",reason));assertEquals("RESOLVED",intent(id).getString("state"));evidence("POST",resolve,"notificationStatusRetryResolutionAndRestartAreActualOwnedPaths","Same-school current ADMIN and owned parent/intent","Resolution persisted without resending","Cross-school/admin and ordinary teacher denied","External acceptance never guessed");
  evidence("WORKER","RequiredNotificationService.dispatchPending","notificationStatusRetryResolutionAndRestartAreActualOwnedPaths","Recorded actor/version + exact parent/student/intent ownership","New worker sends pending once; subsequent run skips accepted","Revoked/forged ownership cases tested separately","SMTP boundary mocked, no live email");
 }
 @ParameterizedTest @ValueSource(strings={"tenant","actor","student","disabled","revoked","credential-version"}) void restartedWorkerFailsClosedForChangedOwnershipAndRelationships(String mode)throws Exception{
  String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());
  if(mode.equals("tenant"))mongo.getCollection("Punishments").updateOne(new Document("_id",id),new Document("$set",new Document("requiredNotifications.0.tenantSchoolId","school-B")));
  if(mode.equals("actor"))mongo.getCollection("Punishments").updateOne(new Document("_id",id),new Document("$set",new Document("requiredNotifications.0.actorAccountId","teacherB")));
  if(mode.equals("student"))mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$unset",new Document("tenantSchoolId","")));
  if(mode.equals("credential-version"))mongo.getCollection("users").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("credentialVersion",1L)));
  if(mode.equals("disabled"))mongo.getCollection("users").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("enabled",false)));
  if(mode.equals("revoked"))mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes",List.of())));
  org.springframework.security.core.context.SecurityContextHolder.clearContext();restartedWorker().dispatchPending();assertNotEquals("ACCEPTED",intent(id).getString("state"));assertEquals(0,intent(id).getInteger("attempts"));verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));assertEquals("school-A",mongo.getCollection("Punishments").find(new Document("_id",id)).first().getString("tenantSchoolId"));evidence("WORKER","RequiredNotificationService.dispatchPending","restartedWorkerFailsClosedForChangedOwnershipAndRelationships","Revalidate persisted actor/student/intent and relationships on every claim","Valid owner acceptance in companion test","Changed "+mode+" never reaches transport or consumes send attempt","Malformed ownership can remain quarantined rather than rewritten");
 }
 @ParameterizedTest @ValueSource(strings={"status","retry","resolve"}) void inconsistentEmbeddedNotificationOwnershipCannotBeReadOrMutated(String operation)throws Exception{
  String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString()),notification=intent(id).getString("notificationId");state(id,"FAILED",true);mongo.getCollection("Punishments").updateOne(new Document("_id",id),new Document("$set",new Document("requiredNotifications.0.tenantSchoolId","school-B")));
  String path="/email/v1/notifications/punishment/"+id+(operation.equals("status")?"":"/"+notification+"/"+operation),before=snapshot();int code=call(operation.equals("status")?"GET":"POST",path,"adminA",operation.equals("resolve")?"{\"reason\":\"Synthetic resolution\"}":null).getResponse().getStatus();assertTrue(Set.of(403,404,503).contains(code),"Conflicting embedded owner must fail closed: "+operation+" returned "+code);assertEquals(before,snapshot());verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));
 }
 void connectGmail(String actor,boolean refresh)throws Exception{
  String email=actor+"@example.test",proof="p".repeat(64);var start=call("POST","/auth/gmail/connect",actor,json(Map.of("browserProof",proof)));success(start);String url=new com.fasterxml.jackson.databind.ObjectMapper().readTree(start.getResponse().getContentAsString()).get("authorizationUrl").asText(),state=org.springframework.web.util.UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
  oauthHttp.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withSuccess("{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"token_type\":\"Bearer\",\"expires_in\":"+(refresh?1:3600)+",\"scope\":\"openid email https://www.googleapis.com/auth/gmail.send\"}",MediaType.APPLICATION_JSON));
  oauthHttp.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo")).andRespond(withSuccess(json(Map.of("sub","synthetic-subject-"+actor,"email",email,"email_verified",true)),MediaType.APPLICATION_JSON));
  if(refresh)oauthHttp.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withSuccess("{\"access_token\":\"synthetic-refreshed\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"openid email https://www.googleapis.com/auth/gmail.send\"}",MediaType.APPLICATION_JSON));
  success(call("POST","/auth/gmail/complete",actor,json(Map.of("state",state,"code","synthetic-code","browserProof",proof))));
 }
 @Test void gmailRefreshPersistenceAndDisconnectRemainAccountBound()throws Exception{
  connectGmail("teacherA",true);var before=mongo.getCollection("oauth2_authorized_clients").find().first();assertFalse(before.toJson().contains("synthetic-refreshed"));var own=call("GET","/auth/gmail/status","teacherA",null);success(own);assertTrue(own.getResponse().getContentAsString().contains("CONNECTED"));var foreign=call("GET","/auth/gmail/status","teacherB",null);success(foreign);assertFalse(foreign.getResponse().getContentAsString().contains("teacherA@example.test"));
  oauthHttp.verify();oauthHttp.reset();oauthHttp.expect(requestTo("https://oauth2.googleapis.com/revoke")).andRespond(withSuccess("",MediaType.TEXT_PLAIN));success(call("POST","/auth/gmail/disconnect","teacherA",null));assertTrue(call("GET","/auth/gmail/status","teacherA",null).getResponse().getContentAsString().contains("DISCONNECTED"));evidence("POST","/auth/gmail/disconnect","gmailRefreshPersistenceAndDisconnectRemainAccountBound","Bearer identity + persisted grant owner; refresh in same encrypted record","Short-lived grant refreshed and then revoked/disconnected through HTTP routes","Other tenant sees only its own disconnected state","OAuth/Gmail HTTP intercepted; original ciphertext never logged");
 }

 @Test void currentAdministratorCanReadAndResolveOwnedHistoryAfterOriginalSenderIsDisabled()throws Exception{
  String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString()),notification=intent(id).getString("notificationId"),base="/email/v1/notifications/punishment/"+id;state(id,"FAILED",true);mongo.getCollection("users").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("enabled",false)));
  success(call("GET",base,"adminA",null));denied("POST",base+"/"+notification+"/retry","adminA",null);success(call("POST",base+"/"+notification+"/resolve","adminA","{\"reason\":\"Synthetic reviewed closed outcome\"}"));assertEquals("RESOLVED",intent(id).getString("state"));verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));
 }
 @AfterEach void recordedWorkerIdentityIsAlwaysCleared(){assertNull(app.getBean(CanonicalEmailOwnership.class).recordedActor());}
}
