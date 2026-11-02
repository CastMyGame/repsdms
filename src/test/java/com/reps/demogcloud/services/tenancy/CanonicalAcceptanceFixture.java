package com.reps.demogcloud.services.tenancy;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

abstract class CanonicalAcceptanceFixture extends CanonicalApplicationFixture {
 @BeforeEach void realisticRecords(){
  for(String school:List.of("A","B")){
   for(var kind:CanonicalCoreWorkflows.Kind.values())mongo.getCollection(kind.collection).updateOne(new Document("_id",kind.name()+school),new Document("$set",new Document("archived",false).append("school","Same School").append("status","OPEN").append("infractionName","Dress Code").append("infractionLevel","1").append("closedTimes",1).append("timeCreated",java.util.Date.from(java.time.LocalDate.now().minusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())).append("adminEmail","admin"+school+"@example.test").append("referralDescription",List.of("Synthetic original facts")).append("infractionDescription",List.of("Synthetic original facts"))));
   var template=new Document(mongo.getCollection("assignment_templates").find(new Document("_id","dress-template")).first());template.put("_id","owned-template-"+school);template.put("tenantSchoolId","school-"+school);template.put("scope","TEACHER_DEFAULT");template.put("createdByUserId","teacher"+school+"@example.test");mongo.getCollection("assignment_templates").insertOne(template);
   mongo.getCollection("Punishments").updateOne(new Document("_id","PUNISHMENT"+school),new Document("$set",new Document("assignmentTemplateId","owned-template-"+school)));
   var collection=mongo.getCollection(mongo.getCollectionName(com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent.class));
   collection.deleteOne(new Document("_id","BEHAVIOR"+school));
   collection.insertOne(new Document("_id","BEHAVIOR"+school).append("tenantSchoolId","school-"+school).append("school","Same School").append("studentEmail","child"+school+"@example.test").append("teacherEmail","teacher"+school+"@example.test").append("behaviorCode","PHONE_OUT").append("consequenceCode","WARNING").append("timeCreated",new java.util.Date()));
  }
 }
 @Override String createCase(String actor,String name,int amount,String action)throws Exception{
  // Remove only the synthetic read-fixture placeholder; real cases/retries remain untouched.
  mongo.getCollection("Punishments").deleteOne(new Document("_id","PUNISHMENTA"));
  return super.createCase(actor,name,amount,action);
 }
 String json(Object value)throws Exception{return app.getBean(com.fasterxml.jackson.databind.ObjectMapper.class).writeValueAsString(value);}
 MvcResult call(String method,String path,String actor,String body)throws Exception{
  var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create(path.replace(" ","%20"))).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).header("X-REPS-Action-Id",UUID.randomUUID().toString());
  if(actor!=null)request.header("Authorization","Bearer "+token(actor));if(body!=null)request.content(body);return mvc.perform(request).andReturn();
 }
 void success(MvcResult r)throws Exception{assertTrue(r.getResponse().getStatus()>=200&&r.getResponse().getStatus()<300,"Expected success, got "+r.getResponse().getStatus()+": "+r.getResponse().getContentAsString()+" handler="+r.getHandler()+" exception="+r.getResolvedException()+" frames="+(r.getResolvedException()==null?"":java.util.Arrays.stream(r.getResolvedException().getStackTrace()).limit(5).toList()));}
 String snapshot(){var result=new TreeMap<String,List<String>>();for(String collection:mongo.getCollectionNames()){var list=new ArrayList<String>();for(var d:mongo.getCollection(collection).find())list.add(d.toJson());Collections.sort(list);result.put(collection,list);}try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
 int denied(String method,String path,String actor,String body)throws Exception{
  String before=snapshot();int sends=mockingDetails(smtp).getInvocations().size();var r=call(method,path,actor,body);int status=r.getResponse().getStatus();assertTrue(Set.of(401,403,404).contains(status),"Not an authorization denial: "+method+" "+path+" "+actor+" -> "+status+" "+r.getResponse().getContentAsString());assertEquals(before,snapshot(),"Denied request changed persistence");assertEquals(sends,mockingDetails(smtp).getInvocations().size(),"Denied request touched SMTP");return status;
 }
 void evidence(String method,String route,String test,String policy,String success,String denial,String limitation)throws Exception{
  AcceptanceEvidence.record(Map.of("method",method,"path",route,"test",getClass().getSimpleName()+"#"+test,"policy",policy,"authorized",success,"denied",denial,"limitation",limitation));
 }
 static final class AcceptanceEvidence {
  static final java.nio.file.Path FILE=java.nio.file.Path.of("target/canonical-acceptance-evidence.jsonl");
  static {try{java.nio.file.Files.writeString(FILE,"");}catch(Exception e){throw new IllegalStateException(e);}}
  static synchronized void record(Map<String,String> row)throws Exception{java.nio.file.Files.writeString(FILE,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(row)+"\n",java.nio.file.StandardOpenOption.APPEND);}
 }
}
