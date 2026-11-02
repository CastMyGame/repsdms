package com.reps.demogcloud.services.tenancy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.bson.Document;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalStudentAdminAcceptanceMongoTest extends CanonicalAcceptanceFixture {
 static java.util.stream.Stream<String> reads(){return java.util.stream.Stream.of(
  "/student/v1/|childA|scoped", "/student/v1/studentid/childA|teacherA|object", "/student/v1/email/childA@example.test|teacherA|object",
  "/student/v1/allStudents|teacherA|scoped", "/student/v1/allStudents/|teacherA|scoped", "/student/v1/lastname/childA|teacherA|object", "/student/v1/parentEmail/guardian@example.test|teacherA|scoped", "/student/v1/archived|adminA|archived", "/student/v1/enrollment-directory|teacherA|scoped",
  "/student/v1/detentionList/Same School|teacherA|scoped", "/student/v1/issList/Same School|teacherA|scoped", "/student/v1/findBySpotter/teacherA@example.test|teacherA|object",
  "/DTO/v1/AdminOverviewData|adminA|scoped", "/DTO/v1/TeacherOverviewData|teacherA|scoped", "/DTO/v1/punishmentsDTO|teacherA|scoped", "/DTO/v1/StudentOverviewData|childA|scoped", "/DTO/v1/StudentOverviewData/childA@example.test|teacherA|object",
  "/employees/v1/employees|adminA|scoped", "/employees/v1/employees/email/teacherA@example.test|adminA|object", "/employees/v1/employees/TEACHER|adminA|scoped", "/users/v1/users|adminA|scoped", "/users/v1/users/TEACHER|adminA|scoped");}
 @ParameterizedTest @MethodSource("reads") void actualStudentAndAdministratorReads(String spec)throws Exception{
  var parts=spec.split("\\|");String path=parts[0],actor=parts[1],kind=parts[2];
  mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$set",new Document("spotters",List.of("teacherA@example.test"))));
  if(kind.equals("archived")){mongo.getCollection("students").updateMany(new Document(),new Document("$set",new Document("archived",true)));}
  if(path.contains("detentionList")||path.contains("issList")){
   int expectedDays=path.contains("issList")?2:1;var utils=app.getBean(com.reps.demogcloud.utils.StudentUtils.class);var today=java.time.LocalDate.now();var created=today.minusDays(1);while(utils.getWorkDaysBetweenTwoDates(created,today)!=expectedDays)created=created.minusDays(1);
   mongo.getCollection("Punishments").updateMany(new Document(),new Document("$set",new Document("timeCreated",java.util.Date.from(created.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))));
  }
  var own=call("GET",path,actor,null);success(own);assertFalse(own.getResponse().getContentAsString().toLowerCase(Locale.ROOT).contains("childb@example.test"));
  if(!path.equals("/student/v1/"))assertTrue(own.getResponse().getContentAsString().toLowerCase(Locale.ROOT).contains(path.startsWith("/employees")||path.startsWith("/users")?"teachera@example.test":"childa@example.test"),"Positive fixture must return owned data");
  String other=actor.startsWith("admin")?"adminB":actor.startsWith("child")?"childB":"teacherB";
  if(kind.equals("object"))denied("GET",path,other,null);else{var foreign=call("GET",path,other,null);success(foreign);assertFalse(foreign.getResponse().getContentAsString().contains("childA@example.test"));}
  denied("GET",path,null,null);
  String route=path.replace("childA@example.test","{email}").replace("/studentid/childA","/studentid/{studentId}");
  // Exact source patterns are resolved by the offline traceability map.
  evidence("GET",path,"actualStudentAndAdministratorReads","Persisted tenant + self/roster/admin; scoped lists never cross tenants","HTTP "+own.getResponse().getStatus(),kind.equals("object")?"Cross-school 403/404":"Other tenant scoped; no A student","Parameterized valid URL; path aliases mapped offline");
 }
 @Test void detentionReportCannotJoinForeignCaseToCurrentStudentEmail()throws Exception{
  var utils=app.getBean(com.reps.demogcloud.utils.StudentUtils.class);var today=java.time.LocalDate.now();var day=today.minusDays(1);while(utils.getWorkDaysBetweenTwoDates(day,today)!=1)day=day.minusDays(1);
  var foreign=new Document("_id","foreign-historical-case").append("tenantSchoolId","school-B").append("school","Same School").append("studentEmail","childA@example.test").append("teacherEmail","teacherB@example.test").append("status","OPEN").append("archived",false).append("timeCreated",java.util.Date.from(day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));mongo.getCollection("Punishments").insertOne(foreign);
  var result=call("GET","/student/v1/detentionList/Same School","teacherA",null);success(result);assertFalse(result.getResponse().getContentAsString().contains("foreign-historical-case"),"Foreign historical case leaked through name-based detention query");
 }
 @ParameterizedTest @ValueSource(strings={"time","notes","spotter-add","spotter-remove","spotter-alias","profile","archive"}) void studentMutationsUseRealPayloadsAndHaveNoUnauthorizedSideEffects(String operation)throws Exception{
  String method="PUT",path,body,actor=operation.equals("archive")||operation.equals("profile")?"adminA":"teacherA";
  switch(operation){
   case "time"->{method="POST";path="/student/v1/childA@example.test/add-time?hours=1&minutes=5";body=null;}
   case "notes"->{path="/student/v1/notes/childA";body="{\"event\":\"Meeting\",\"content\":\"Synthetic note\"}";}
   case "spotter-add","spotter-remove"->{path="/student/v1/"+(operation.equals("spotter-add")?"addAsSpotter":"removeAsSpotter");body="{\"studentEmail\":[\"childA@example.test\"],\"spotters\":[\"teacherA@example.test\"]}";}
   case "spotter-alias"->{path="/student/v1/remove-spotter/teacherA@example.test";body="{\"studentIdNumber\":\"childA\"}";}
   case "profile"->{path="/student/v1/updateStudents";body="[{\"studentIdNumber\":\"childA\",\"studentEmail\":\"childA@example.test\",\"profileRevision\":0,\"firstName\":\"Updated synthetic\"}]";}
   default->{path="/student/v1/archived/childA";body=null;}
  }
  if(operation.equals("spotter-remove")||operation.equals("spotter-alias"))mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$set",new Document("spotters",List.of("teacherA@example.test","otherA@example.test"))));
  denied(method,path,actor.equals("adminA")?"adminB":"teacherB",body);denied(method,path,"otherA",body);var r=call(method,path,actor,body);success(r);
  var persisted=mongo.getCollection("students").find(new Document("_id","childA")).first();assertEquals("school-A",persisted.getString("tenantSchoolId"));assertEquals(12,persisted.getInteger("currency"));
  switch(operation){
   case "time"->{assertEquals(1,persisted.get("timeBank",Document.class).getInteger("hours"));assertEquals(5,persisted.get("timeBank",Document.class).getInteger("minutes"));}
   case "notes"->assertTrue(persisted.toJson().contains("Synthetic note"));
   case "profile"->assertEquals("Updated synthetic",persisted.getString("firstName"));
   case "archive"->{assertTrue(persisted.getBoolean("archived"));assertFalse(mongo.getCollection("users").find(new Document("_id","childA")).first().getBoolean("enabled"));}
   case "spotter-add"->assertTrue(persisted.getList("spotters",String.class).stream().anyMatch(e->e.equalsIgnoreCase("teacherA@example.test")));
   default->assertTrue(persisted.getList("spotters",String.class).stream().noneMatch(e->e.equalsIgnoreCase("teacherA@example.test")));
  }
  evidence(method,path,"studentMutationsUseRealPayloadsAndHaveNoUnauthorizedSideEffects","Exact tenant/current independent relationship; admin-only retirement/profile","HTTP "+r.getResponse().getStatus(),"Cross-school and unrelated denied; full DB/SMTP snapshot unchanged","Valid mutation persisted; ownership and balance preserved");
 }
 @ParameterizedTest @ValueSource(strings={"user-role","employee-role","enabled","user-delete","employee-delete"}) void accountAdministrationIsTenantScoped(String op)throws Exception{
  String method=op.contains("delete")?"DELETE":"PUT";String path=op.equals("employee-role")?"/employees/v1/employees/otherA/roles":op.equals("employee-delete")?"/employees/v1/employees/otherA":"/users/v1/users/otherA"+(op.equals("user-role")?"/roles":op.equals("enabled")?"/enabled":"");String body=op.contains("role")?"[{\"role\":\"GUIDANCE\"}]":op.equals("enabled")?"{\"enabled\":false}":null;
  denied(method,path,"adminB",body);denied(method,path,"teacherA",body);success(call(method,path,"adminA",body));
  var user=mongo.getCollection("users").find(new Document("_id","otherA")).first();assertEquals("school-A",user.getString("tenantSchoolId"));assertNotNull(user);
  evidence(method,path,"accountAdministrationIsTenantScoped","Same-school persisted ADMIN; cannot administer other tenant","Authorized change retained records","Cross-school admin/teacher denied; no DB/SMTP change","Role sync/deactivation uses real repositories");
 }
 @Test void schoolSettingsAllowlistAndCanonicalIdentity()throws Exception{
  String path="/school/v1/school-A?currency=Stars&maxPunishLevel=4",body=null;denied("PUT",path,"adminB",body);denied("PUT",path,"teacherA",body);success(call("PUT",path,"adminA",body));assertEquals("Stars",mongo.getCollection("schools").find(new Document("_id","school-A")).first().getString("currency"));
  evidence("PUT","/school/v1/{schoolName}","schoolSettingsAllowlistAndCanonicalIdentity","Persisted same-school ADMIN + fixed allowlist","School A label updated","School B admin denied targeting school-A","Same display name cannot select target school");
 }

 @ParameterizedTest @ValueSource(strings={"student","student-bulk","employee","employee-bulk"}) void provisioningAndRosterEnrollmentPersistCanonicalMembership(String mode)throws Exception{
  boolean staff=mode.startsWith("employee"),bulk=mode.endsWith("bulk");String path=staff?"/employees/v1/employees"+(bulk?"/list":""):"/student/v1/"+(bulk?"addStudents":"newStudent");
  String payload=staff?"{\"email\":\"newstaff@example.test\",\"firstName\":\"Synthetic\",\"lastName\":\"Staff\",\"roles\":[{\"role\":\"TEACHER\"}]}":"{\"studentEmail\":\"newchild@example.test\",\"firstName\":\"Synthetic\",\"lastName\":\"Student\"}";
  if(bulk)payload="["+payload+"]";
  denied("POST",path,"childA",payload);if(staff)denied("POST",path,"teacherA",payload);
  String forged=payload.replace(staff?"\"email\"":"\"studentEmail\"","\"school\":\"school-B\","+(staff?"\"email\"":"\"studentEmail\""));denied("POST",path,"adminA",forged);
  success(call("POST",path,"adminA",payload));var row=mongo.getCollection(staff?"employee":"students").find(new Document(staff?"email":"studentEmail",staff?"newstaff@example.test":"newchild@example.test")).first();assertNotNull(row);assertEquals("school-A",row.getString("tenantSchoolId"));
  if(!staff){String roster="{\"expectedRosterRevision\":0,\"classToUpdate\":{\"className\":\"Math\",\"classPeriod\":\"1\",\"classRoster\":[\"childA@example.test\",\"newchild@example.test\"]}}";success(call("PUT","/employees/v1/updateClass/teacherA@example.test","teacherA",roster));assertTrue(mongo.getCollection("employee").find(new Document("_id","teacherA")).first().toJson().contains("newchild@example.test"));}
  evidence("POST",path,"provisioningAndRosterEnrollmentPersistCanonicalMembership","Persisted authorized actor derives new ownership; forged tenant rejected","Created canonical profile/account; student enrolled through real roster API","Student/teacher role denial and foreign ownership payload rejected without writes","Invitation transport mocked; no live recipient");
 }
 @Test void emailBatchLookupAndClassRemovalUseAuthorizedTargets()throws Exception{
  String batch="[\"childA@example.test\"]";denied("POST","/student/v1/getByEmailList","teacherB",batch);success(call("POST","/student/v1/getByEmailList","teacherA",batch));evidence("POST","/student/v1/getByEmailList","emailBatchLookupAndClassRemovalUseAuthorizedTargets","Independent student relationship + tenant","Authorized batch read","Foreign teacher denied; no writes","Whole batch authorization");
  String path="/employees/v1/deleteClass/teacherA@example.test",body="{\"expectedRosterRevision\":0,\"classToUpdate\":{\"className\":\"Math\",\"classPeriod\":\"1\",\"classRoster\":[\"childA@example.test\"]}}";denied("POST",path,"adminB",body);success(call("POST",path,"teacherA",body));assertTrue(mongo.getCollection("employee").find(new Document("_id","teacherA")).first().getList("classes",Document.class).isEmpty());evidence("POST",path,"emailBatchLookupAndClassRemovalUseAuthorizedTargets","Self/admin tenant roster authority + revision","Class removed with revision CAS","Foreign admin denied; no writes","Revoked roster no longer grants access");
 }
 @ParameterizedTest @ValueSource(strings={"/users/create/school-A","/users/v1/users/create/school-A"}) void bulkAccountRepairAliasesNeverBorrowAnotherSchool(String path)throws Exception{
  denied("POST",path,"adminB",null);denied("POST",path,"teacherA",null);success(call("POST",path,"adminA",null));evidence("POST",path,"bulkAccountRepairAliasesNeverBorrowAnotherSchool","Persisted same-tenant administrator","Scoped existing-account repair succeeds","Foreign admin and ordinary teacher denied without mutation","Existing accounts retained; new accounts need independent setup");
 }
}
