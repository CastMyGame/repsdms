package com.reps.demogcloud.services.tenancy;

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
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Original Phase 5B acceptance scenarios, now sharing the isolated application fixture. */
class FullApplicationCanonicalMongoTest extends CanonicalApplicationFixture {
    @Test void ordinaryCreationAssignmentAndTenantDenialsAreRealDomainOperations()throws Exception{
        String action="00000000-0000-4000-8000-000000000001";String id=createCase("teacherA","Dress Code",0,action);
        var row=mongo.getCollection("Punishments").find(new Document("_id",id)).first();assertEquals("school-A",row.getString("tenantSchoolId"));assertEquals("dress-template",row.getString("assignmentTemplateId"));assertEquals(1,row.getList("requiredNotifications",Document.class).size());assertNotNull(row.get("historicalAccess"));
        assertEquals(id,createCase("teacherA","Dress Code",0,action));assertEquals(1,mongo.getCollection("Punishments").countDocuments(new Document("_id",id)));
        mvc.perform(getAs("/assignments/v1/cases/"+id,"childA")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(getAs("/assignments/v1/cases/"+id,"childB")).andExpect(status().isForbidden());
        long count=mongo.getCollection("Punishments").countDocuments();
        for(String actor:List.of("teacherB","adminB","otherA","unassignedA"))mvc.perform(postAs("/punish/v1/startPunish/formList",actor).header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(body("childA@example.test","Dress Code",0))).andExpect(status().isForbidden());
        assertEquals(count,mongo.getCollection("Punishments").countDocuments());verify(app.getBean(com.reps.demogcloud.services.email.EmailRoutingService.class),never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void actualAssignmentSavesRejectStaleWritesAndNeverGrantFormerAuthorReview()throws Exception{
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());
        String command="{\"version\":0,\"actionId\":\"synthetic-save-01\",\"answers\":{\"q1\":{\"text\":\"Saved work\"}},\"currentQuestionId\":\"q1\"}";
        mvc.perform(put("/assignments/v1/cases/"+id+"/save").header("Authorization","Bearer "+token("childA")).contentType(MediaType.APPLICATION_JSON).content(command)).andExpect(status().isOk());
        mvc.perform(getAs("/assignments/v1/cases/"+id,"childA")).andExpect(status().isOk()).andExpect(jsonPath("$.work.answers.q1.text").value("Saved work"));
        mvc.perform(put("/assignments/v1/cases/"+id+"/save").header("Authorization","Bearer "+token("childA")).contentType(MediaType.APPLICATION_JSON).content(command.replace("synthetic-save-01","synthetic-save-02"))).andExpect(status().isConflict());
        mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes",List.of())));
        mvc.perform(getAs("/history/v1/cases/punishment/"+id,"teacherA")).andExpect(status().isOk());
        mvc.perform(put("/assignments/v1/cases/"+id+"/review").header("Authorization","Bearer "+token("teacherA")).contentType(MediaType.APPLICATION_JSON).content(command)).andExpect(status().isForbidden());
    }
    @Test void productionCurrencyServiceCommitsAndReplaysOnce()throws Exception{
        manifest();String action=UUID.randomUUID().toString();String id=createCase("teacherA","Positive Behavior Shout Out!",3,action);assertEquals(id,createCase("teacherA","Positive Behavior Shout Out!",3,action));
        assertEquals(27,mongo.findById("teacherA",Employee.class).getCurrency());assertEquals(15,mongo.findById("childA",Student.class).getCurrency());
        var recognition=mongo.getCollection("Punishments").find(new Document("_id",id)).first();assertEquals("school-A",recognition.getString("tenantSchoolId"));assertEquals(1,recognition.getList("requiredNotifications",Document.class).size());assertEquals(1,mongo.getCollection("currency_operations").countDocuments(new Document("receiptVersion",2)));
    }
    @Test void productionBulkCurrencyRollsBackIfSecondRecognitionPersistenceFails()throws Exception{
        manifest();account("secondA","STUDENT","A",false);var employee=mongo.findById("teacherA",Employee.class);employee.getClasses().get(0).setClassRoster(List.of("childA@example.test","secondA@example.test"));app.getBean(EmployeeRepository.class).save(employee);
        var notifications=app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class);var calls=new java.util.concurrent.atomic.AtomicInteger();doAnswer(call->{if(calls.incrementAndGet()==2)throw new IllegalStateException("Synthetic persistence interruption");return call.callRealMethod();}).when(notifications).saveCurrencyCreated(any());
        String batch=body("childA@example.test","Positive Behavior Shout Out!",3).replace("]",",")+body("secondA@example.test","Positive Behavior Shout Out!",3).substring(1);
        long cases=mongo.getCollection("Punishments").countDocuments();mvc.perform(postAs("/punish/v1/startPunish/formList","teacherA").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(batch)).andExpect(status().is5xxServerError());
        assertEquals(30,mongo.findById("teacherA",Employee.class).getCurrency());assertEquals(12,mongo.findById("childA",Student.class).getCurrency());assertEquals(12,mongo.findById("secondA",Student.class).getCurrency());assertEquals(cases,mongo.getCollection("Punishments").countDocuments());assertEquals(0,mongo.getCollection("currency_operations").countDocuments(new Document("receiptVersion",2)));
    }
    @Test void recordedOwnerWorkerSendsOnceThroughMockAndRecoversInterruptedClaimWithoutResend()throws Exception{
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());var service=app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class);var router=app.getBean(com.reps.demogcloud.services.email.EmailRoutingService.class);

        org.springframework.security.core.context.SecurityContextHolder.clearContext();service.dispatchPending();service.dispatchPending();
        var row=mongo.getCollection("Punishments").find(new Document("_id",id)).first();var intent=row.getList("requiredNotifications",Document.class).get(0);assertEquals("ACCEPTED",intent.getString("state"),intent.getString("reason"));verify(router,times(1)).sendRequiredNotification(anyString(),anyList(),anyString(),anyString(),anyList(),eq("teacherA@example.test"),eq(false),anyString(),any(),any());
        assertNull(app.getBean(CanonicalEmailOwnership.class).recordedActor());
        mongo.getCollection("Punishments").updateOne(new Document("_id",id),new Document("$set",new Document("requiredNotifications.0.state","DISPATCHING").append("requiredNotifications.0.claimedAt",Date.from(java.time.Instant.now().minusSeconds(600)))));
        service.dispatchPending();assertEquals("UNCERTAIN",mongo.getCollection("Punishments").find(new Document("_id",id)).first().getList("requiredNotifications",Document.class).get(0).getString("state"));verify(router,times(1)).sendRequiredNotification(anyString(),anyList(),anyString(),anyString(),anyList(),any(),anyBoolean(),anyString(),any(),any());
    }
    @Test void revokedRelationshipPreventsWorkerSendButRetainsHistoricalSnapshot()throws Exception{
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes",List.of())));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class).dispatchPending();
        var intent=mongo.getCollection("Punishments").find(new Document("_id",id)).first().getList("requiredNotifications",Document.class).get(0);assertEquals("FAILED",intent.getString("state"));assertEquals(0,intent.getInteger("attempts"));
        verify(app.getBean(com.reps.demogcloud.services.email.EmailRoutingService.class),never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        mvc.perform(getAs("/history/v1/cases/punishment/"+id,"teacherA")).andExpect(status().isOk());mvc.perform(getAs("/punish/v1/"+id,"teacherA")).andExpect(status().isForbidden());
    }
    @Test void emailGatesDisabledCannotBypassCaseAuthorizationOrLoseTheCase()throws Exception{
        ((org.springframework.core.env.ConfigurableEnvironment)app.getEnvironment()).getPropertySources().addFirst(new MapPropertySource("email-disabled",Map.of("reps.email.notifications.enabled","false")));
        try {
            String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());var row=mongo.getCollection("Punishments").find(new Document("_id",id)).first();assertEquals("school-A",row.getString("tenantSchoolId"));
            assertTrue(!row.containsKey("requiredNotifications") || row.getList("requiredNotifications",Document.class).isEmpty());
            mvc.perform(postAs("/punish/v1/startPunish/formList","otherA").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(body("childA@example.test","Dress Code",0))).andExpect(status().isForbidden());
        }finally{((org.springframework.core.env.ConfigurableEnvironment)app.getEnvironment()).getPropertySources().remove("email-disabled");}
    }
    @Test void pendingCanonicalStudentCanUseSchoolAssistedSetupWithoutTenantChanges()throws Exception{
        account("setup","STUDENT","A",false);mongo.getCollection("users").updateOne(new Document("_id","setup"),new Document("$set",new Document("passwordSetupRequired",true)));
        var profile=mongo.findById("teacherA",Employee.class);profile.getClasses().get(0).setClassRoster(List.of("childA@example.test","setup@example.test"));app.getBean(EmployeeRepository.class).save(profile);
        mvc.perform(postAs("/accounts/v1/students/setup@example.test/setup","teacherB").content("{\"identityVerified\":true,\"privateEmailAllowed\":false}")).andExpect(status().isForbidden());
        var result=mvc.perform(postAs("/accounts/v1/students/setup@example.test/setup","teacherA").content("{\"identityVerified\":true,\"privateEmailAllowed\":false}")).andExpect(status().isOk()).andReturn();
        String credential=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("credential").asText();
        String completion="{\"username\":\"setup@example.test\",\"credential\":\""+credential+"\",\"newPassword\":\"Synthetic-long-password!\"}";
        mvc.perform(post("/account-access/complete").contentType(MediaType.APPLICATION_JSON).content(completion)).andExpect(status().isNoContent());
        var user=mongo.findById("setup",UserModel.class);assertFalse(user.isPasswordSetupRequired());assertEquals("school-A",user.getTenantSchoolId());assertEquals("STUDENT",user.getRoles().iterator().next().getRole());
        mvc.perform(post("/account-access/complete").contentType(MediaType.APPLICATION_JSON).content(completion)).andExpect(status().isBadRequest());
    }
    @Test void canonicalGoogleSetupRequiresPersistedMatchingMembershipAndNeverRepairsOwnership()throws Exception{
        account("newstaff","TEACHER","A",false);mongo.getCollection("users").updateOne(new Document("_id","newstaff"),new Document("$set",new Document("passwordSetupRequired",true)));
        var service=app.getBean(AccountAccessService.class);var user=mongo.findById("newstaff",UserModel.class);service.googleEstablished(user,true);assertFalse(mongo.findById("newstaff",UserModel.class).isPasswordSetupRequired());
        user.setTenantSchoolId("school-B");var forged=user;assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.googleEstablished(forged,true));assertEquals("school-A",mongo.findById("newstaff",UserModel.class).getTenantSchoolId());
        mongo.getCollection("employee").updateOne(new Document("_id","newstaff"),new Document("$set",new Document("tenantSchoolId","school-B")));var current=mongo.findById("newstaff",UserModel.class);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.googleEstablished(current,true));
    }
    @Test void officeCreationReplayAndAdminClosureUseRealOwnedCaseAndNoAssignment()throws Exception{
        String action=UUID.randomUUID().toString(),body="[{\"studentEmail\":\"childA@example.test\",\"referralDescription\":[\"Original office facts\"],\"referralCode\":{\"codeKey\":1,\"codeName\":\"Synthetic offense\"}}]";
        var result=mvc.perform(postAs("/officeReferral/v1/startPunish/adminReferral","teacherA").header("X-REPS-Action-Id",action).content(body)).andExpect(status().isAccepted()).andReturn();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get(0).get("officeReferralId").asText();
        mvc.perform(postAs("/officeReferral/v1/startPunish/adminReferral","teacherA").header("X-REPS-Action-Id",action).content(body)).andExpect(status().isAccepted());assertEquals(1,mongo.getCollection("OfficeReferrals").countDocuments(new Document("_id",id)));
        String closure="{\"id\":\""+id+"\",\"comment\":\"Private later outcome\"}";
        mvc.perform(postAs("/officeReferral/v1/closeId","teacherA").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(closure)).andExpect(status().isForbidden());
        mvc.perform(postAs("/officeReferral/v1/closeId","adminB").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(closure)).andExpect(status().isForbidden());
        mvc.perform(postAs("/officeReferral/v1/closeId","adminA").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(closure)).andExpect(status().isAccepted());
        var row=mongo.getCollection("OfficeReferrals").find(new Document("_id",id)).first();assertEquals("school-A",row.getString("tenantSchoolId"));assertEquals("CLOSED",row.getString("status"));assertFalse(row.containsKey("assignmentWork"));
        assertEquals(2,row.getList("requiredNotifications",Document.class).size());
        var history=mvc.perform(getAs("/history/v1/cases/office/"+id,"teacherA")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(history.contains("Private later outcome"));
    }
    @Test void actualStudentSubmissionRejectionAndResubmissionPreserveHistoryAndSafeReviewReplay()throws Exception{
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.function.BiFunction<Integer,String,String> submission=(v,action)->{try{return mapper.writeValueAsString(Map.of("version",v,"actionId",action,"answers",Map.of("q1",Map.of("text","Completed reflection"))));}catch(Exception e){throw new RuntimeException(e);}};
        mvc.perform(put("/assignments/v1/cases/"+id+"/submit").header("Authorization","Bearer "+token("childA")).contentType(MediaType.APPLICATION_JSON).content(submission.apply(0,"submit-action-0001"))).andExpect(status().isOk());
        String reject=mapper.writeValueAsString(Map.of("version",1,"actionId","review-action-0001","decision","REJECT","feedback","Please revise"));
        mvc.perform(put("/assignments/v1/cases/"+id+"/review").header("Authorization","Bearer "+token("teacherA")).contentType(MediaType.APPLICATION_JSON).content(reject)).andExpect(status().isOk());
        mvc.perform(put("/assignments/v1/cases/"+id+"/submit").header("Authorization","Bearer "+token("childA")).contentType(MediaType.APPLICATION_JSON).content(submission.apply(2,"submit-action-0002"))).andExpect(status().isOk());
        String accept=mapper.writeValueAsString(Map.of("version",3,"actionId","review-action-0002","decision","ACCEPT"));
        for(int i=0;i<2;i++)mvc.perform(put("/assignments/v1/cases/"+id+"/review").header("Authorization","Bearer "+token("teacherA")).contentType(MediaType.APPLICATION_JSON).content(accept)).andExpect(status().isOk());
        var row=mongo.getCollection("Punishments").find(new Document("_id",id)).first();assertEquals("CLOSED",row.getString("status"));assertEquals(List.of("Original facts"),row.get("infractionDescription"));assertEquals(2,row.get("assignmentWork",Document.class).getList("submissions",Document.class).size());
    }
    @Test void concurrentProductionAwardRetriesCommitOnlyOnce()throws Exception{
        manifest();String action=UUID.randomUUID().toString();var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.Callable<MvcResult> call=()->{start.await();return mvc.perform(postAs("/punish/v1/startPunish/formList","teacherA").header("X-REPS-Action-Id",action).content(body("childA@example.test","Positive Behavior Shout Out!",3))).andReturn();};
            var first=executor.submit(call);var second=executor.submit(call);start.countDown();var outcomes=List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(outcomes.stream().anyMatch(result->result.getResponse().getStatus()==200));
            for(var result:outcomes)assertTrue(Set.of(200,409).contains(result.getResponse().getStatus()),"Only success or bounded concurrent conflict is permitted");
            String replay=createCase("teacherA","Positive Behavior Shout Out!",3,action);
            for(var result:outcomes)if(result.getResponse().getStatus()==200)assertEquals(replay,new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get(0).get("punishment").get("punishmentId").asText());
            assertEquals(27,mongo.findById("teacherA",Employee.class).getCurrency());assertEquals(15,mongo.findById("childA",Student.class).getCurrency());assertEquals(1,mongo.getCollection("currency_operations").countDocuments(new Document("receiptVersion",2)));
        }finally{executor.shutdownNow();}
    }
    @Test void realAnnouncementSenderNeverReceivesCrossTenantOrUnownedBatch()throws Exception {
        String request="[{\"teacherEmail\":\"teacherA@example.test\",\"className\":\"Math\",\"classPeriod\":\"1\",\"subject\":\"Synthetic class\",\"msg\":\"Synthetic announcement\"}]";
        mvc.perform(postAs("/email/v1/classAnnouncement","teacherA").content(request)).andExpect(status().isOk());
        verify(smtp,times(1)).send(any(jakarta.mail.internet.MimeMessage.class));clearInvocations(smtp);
        mvc.perform(postAs("/email/v1/classAnnouncement","teacherA").content(request.replace("teacherA@example.test","teacherB@example.test"))).andExpect(status().isForbidden());
        mvc.perform(postAs("/email/v1/classAnnouncement","childA").content(request)).andExpect(status().isForbidden());
        var c=mongo.getCollection("employee");
        for(String email:List.of("childB@example.test","childA@example.test")){
            c.updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes.0.classRoster",List.of("childA@example.test",email))));
            if(email.equals("childA@example.test"))mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$unset",new Document("tenantSchoolId","")));
            mvc.perform(postAs("/email/v1/classAnnouncement","teacherA").content(request)).andExpect(status().isForbidden());
        }
        verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));
        assertEquals(0,mongo.getCollection("Punishments").countDocuments(new Document("requiredNotifications",new Document("$exists",true))));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->app.getBean(com.reps.demogcloud.services.email.ClassAnnouncementAccess.class).requireRecipients("teacherA@example.test",List.of("childA@example.test")));
    }
    @Test void actualRosterAndTrackedBehaviorRoutesValidateWholeBatchAndCurrentRelationships()throws Exception {
        String roster="{\"expectedRosterRevision\":0,\"classToUpdate\":{\"className\":\"Math\",\"classPeriod\":\"1\",\"classRoster\":[\"childA@example.test\"]}}";
        mvc.perform(put("/employees/v1/updateClass/teacherA@example.test").header("Authorization","Bearer "+token("teacherA")).contentType(MediaType.APPLICATION_JSON).content(roster)).andExpect(status().isOk());
        mvc.perform(put("/employees/v1/updateClass/teacherA@example.test").header("Authorization","Bearer "+token("teacherA")).contentType(MediaType.APPLICATION_JSON).content(roster)).andExpect(status().isConflict());
        mvc.perform(put("/employees/v1/updateClass/teacherA@example.test").header("Authorization","Bearer "+token("adminB")).contentType(MediaType.APPLICATION_JSON).content(roster)).andExpect(status().isForbidden());
        String event="[{\"studentEmail\":\"childA@example.test\",\"behaviorCode\":\"PHONE_OUT\",\"consequenceCode\":\"WARNING\"}]";
        mvc.perform(postAs("/tracked-behaviors/v1/save","teacherA").content(event)).andExpect(status().isOk());
        for(String actor:List.of("teacherB","otherA","unassignedA"))mvc.perform(postAs("/tracked-behaviors/v1/save",actor).content(event)).andExpect(status().isForbidden());
        mvc.perform(getAs("/tracked-behaviors/v1/student/childA@example.test/timeline","childA")).andExpect(status().isOk());
        mvc.perform(getAs("/tracked-behaviors/v1/student/childA@example.test/timeline","childB")).andExpect(status().isForbidden());
    }
    @Test void passwordLoginAndActualGmailConnectPersistEncryptedOwnershipAndPreventGrantBorrowing()throws Exception {
        mvc.perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"teacherA@example.test\",\"password\":\"Synthetic-password!\"}")).andExpect(status().isOk());
        String proof="p".repeat(64);
        var result=mvc.perform(postAs("/auth/gmail/connect","teacherA").content("{\"browserProof\":\""+proof+"\"}")).andExpect(status().isOk()).andReturn();
        String url=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("authorizationUrl").asText();
        String state=org.springframework.web.util.UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        oauthHttp.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://oauth2.googleapis.com/token")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"openid email https://www.googleapis.com/auth/gmail.send\"}",MediaType.APPLICATION_JSON));
        oauthHttp.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://openidconnect.googleapis.com/v1/userinfo")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"sub\":\"synthetic-subject\",\"email\":\"teacherA@example.test\",\"email_verified\":true}",MediaType.APPLICATION_JSON));
        mvc.perform(postAs("/auth/gmail/complete","teacherA").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("state",state,"code","synthetic-code","browserProof",proof)))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONNECTED"));
        var grant=mongo.getCollection("oauth2_authorized_clients").find().first();assertNotNull(grant);assertFalse(grant.toJson().contains("synthetic-refresh"));assertFalse(grant.toJson().contains("synthetic-access"));
        mvc.perform(getAs("/auth/gmail/status","teacherB")).andExpect(status().isOk()).andExpect(jsonPath("$.authorized").value(false));
        gmailHttp.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"id\":\"synthetic-gmail-message\",\"threadId\":\"synthetic-thread\"}",MediaType.APPLICATION_JSON));
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());org.springframework.security.core.context.SecurityContextHolder.clearContext();notifications.dispatchPending();
        var intent=mongo.getCollection("Punishments").find(new Document("_id",id)).first().getList("requiredNotifications",Document.class).get(0);assertEquals("ACCEPTED",intent.getString("state"),intent.getString("reason"));assertEquals("synthetic-gmail-message",intent.getString("providerMessageId"));verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));
        mongo.getCollection("oauth2_authorized_clients").updateOne(new Document("_id",grant.get("_id")),new Document("$set",new Document("tenantSchoolId","school-B")));
        mvc.perform(getAs("/auth/gmail/status","teacherA")).andExpect(status().isServiceUnavailable());
    }
    @Test void allRuntimeMappingsAreReconciledAndSampledWithoutTreatingMalformedBodiesAsAuthorizationProof()throws Exception {
        var mapping=app.getBean("requestMappingHandlerMapping",org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        var evidence=new ArrayList<Map<String,Object>>();
        try(var stripe=mockStatic(com.stripe.model.checkout.Session.class)){
            for(var entry:mapping.getHandlerMethods().entrySet()){
                var handler=entry.getValue();if(!handler.getBeanType().getPackageName().startsWith("com.reps.demogcloud"))continue;
                for(String pattern:entry.getKey().getPatternValues())for(var method:entry.getKey().getMethodsCondition().getMethods()){
                    var row=new LinkedHashMap<String,Object>();row.put("method",method.name());row.put("path",pattern);row.put("handler",handler.getBeanType().getSimpleName()+"."+handler.getMethod().getName());
                    String target=pattern;
                    var variables=java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(pattern);
                    while(variables.find()){
                        String v=variables.group(1),value;
                        if(v.equals("school")||v.equals("schoolName"))value="Same School";
                        else if(v.equals("role"))value="TEACHER";
                        else if(v.equals("kind"))value="punishment";
                        else if(v.equals("operation"))value="save";
                        else if(v.equals("status"))value="OPEN";
                        else if(v.equals("index")||v.equals("level"))value="1";
                        else if(v.equals("studentId"))value="childB";
                        else if(v.equals("id")||v.equals("caseId")||v.equals("punishmentId"))value=handler.getBeanType().getSimpleName().contains("Guidance")?"GUIDANCEB":handler.getBeanType().getSimpleName().contains("Office")?"OFFICEB":handler.getBeanType().getSimpleName().matches("EmployeeController|UserControllers")?"teacherB":"PUNISHMENTB";
                        else if(v.toLowerCase().contains("teacher")||v.equals("spotterEmail"))value="teacherB@example.test";
                        else value="childB@example.test";
                        target=target.replace(variables.group(),value);
                    }
                    // Binding failures are recorded as gaps. This matrix is not a claim of exhaustive authorization acceptance.
                    var results=new LinkedHashMap<String,Integer>();
                    for(String actor:List.of("anonymous","teacherA","childA","guideA","adminA")){
                        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method.name()),target).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).header("X-REPS-Action-Id",UUID.randomUUID().toString()).header("Stripe-Signature","synthetic-invalid");
                        if(!actor.equals("anonymous"))request.header("Authorization","Bearer "+token(actor));
                        if(!method.name().equals("GET"))request.content("{}");
                        var result=mvc.perform(request).andReturn();int status=result.getResponse().getStatus();results.put(actor,status);
                        if(!handler.getBeanType().getSimpleName().equals("SchoolController"))assertFalse(result.getResponse().getContentAsString().contains("childB@example.test"),"Cross-tenant student disclosed: "+method+" "+pattern+" "+actor);
                    }
                    row.put("bindingProbeStatuses",results);evidence.add(row);
                }
            }
            stripe.verifyNoInteractions();
        }
        evidence.sort(java.util.Comparator.comparing(r->r.get("path").toString()+r.get("method")));
        assertEquals(172,evidence.size(),"Route additions require inventory review");
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/canonical-runtime-route-evidence.json"),new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
    @Test void billingCheckoutDisabledForAllRolesAndUnsignedCallbacksCannotCreateMembership()throws Exception {
        long accounts=mongo.getCollection("users").countDocuments();
        try(var stripe=mockStatic(com.stripe.model.checkout.Session.class)){
            for(String actor:List.of("anonymous","teacherA","childA","guideA","adminA")){
                var request=post("/stripe/v1/create-checkout-session").contentType(MediaType.APPLICATION_JSON).content("{}");if(!actor.equals("anonymous"))request.header("Authorization","Bearer "+token(actor));
                mvc.perform(request).andExpect(status().isServiceUnavailable());
            }
            mvc.perform(post("/stripe/v1/webhook").header("Stripe-Signature","synthetic-invalid").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());stripe.verifyNoInteractions();
        }
        assertEquals(accounts,mongo.getCollection("users").countDocuments());
    }
    @ParameterizedTest @ValueSource(strings={"missing-student","conflicting-student","missing-actor","archived-student","disabled-actor","stale-spotter"})
    void invalidOwnershipAndRevokedRelationshipsHaveNoCaseOrNotificationSideEffects(String scenario)throws Exception {
        String bearer=token("teacherA");
        switch(scenario){
            case "missing-student"->mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$unset",new Document("tenantSchoolId","")));
            case "conflicting-student"->mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$set",new Document("tenantSchoolId","school-B")));
            case "missing-actor"->mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$unset",new Document("tenantSchoolId","")));
            case "archived-student"->mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$set",new Document("archived",true)));
            case "disabled-actor"->mongo.getCollection("users").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("enabled",false)));
            case "stale-spotter"->{mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes",List.of())));mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$set",new Document("spotters",List.of("teacherA@example.test"))));}
        }
        long before=mongo.getCollection("Punishments").countDocuments();
        int result=mvc.perform(post("/punish/v1/startPunish/formList").header("Authorization","Bearer "+bearer).header("X-REPS-Action-Id",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body("childA@example.test","Dress Code",0))).andReturn().getResponse().getStatus();
        assertTrue(Set.of(401,403).contains(result));assertEquals(before,mongo.getCollection("Punishments").countDocuments());verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));
    }
    @ParameterizedTest @ValueSource(strings={"teacherA","guideA","adminA"})
    void eachAuthorizedStaffRoleCreatesAnOwnedCase(String actor)throws Exception {
        String id=createCase(actor,"Dress Code",0,UUID.randomUUID().toString());assertEquals("school-A",mongo.getCollection("Punishments").find(new Document("_id",id)).first().getString("tenantSchoolId"));
    }
    @Test void fullCompositionHasAllFourCanonicalPathsAndNoAutomaticScheduler(){
        for(Class<?> type:List.of(CanonicalCoreWorkflows.class,CanonicalDomainAccess.class,com.reps.demogcloud.services.currency.CanonicalCurrencyTransactions.class,CanonicalEmailOwnership.class))assertNotNull(app.getBean(type));
        assertFalse(app.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
        assertEquals("false",app.getEnvironment().getProperty(TenantPreparationGuard.PROPERTY));
        assertEquals(IsolatedCanonicalBootstrap.marker,mongo.getCollection("a7_activation_control").find().first());
    }


    @Test void detentionReportNeverReturnsForeignCaseThatReferencesAnAuthorizedStudent()throws Exception{
        var utils=app.getBean(com.reps.demogcloud.utils.StudentUtils.class);var today=java.time.LocalDate.now();var day=today.minusDays(1);
        while(utils.getWorkDaysBetweenTwoDates(day,today)!=1)day=day.minusDays(1);
        for(String owner:List.of("A","B"))mongo.getCollection("Punishments").insertOne(new Document("_id","detention-case-"+owner).append("tenantSchoolId","school-"+owner)
                .append("school","Same School").append("studentEmail","childA@example.test").append("teacherEmail","teacher"+owner+"@example.test")
                .append("status","OPEN").append("archived",false).append("timeCreated",Date.from(day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())));
        var response=mvc.perform(getAs("/student/v1/detentionList/Same School","teacherA")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("detention-case-A"));assertFalse(response.contains("detention-case-B"));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        verify(smtp,never()).send(any(jakarta.mail.internet.MimeMessage.class));
    }
}
