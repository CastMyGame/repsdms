package com.reps.demogcloud.integration;

import com.reps.demogcloud.DemoGcloudApplication;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.*;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.services.stripe.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import com.reps.demogcloud.services.translation.GoogleCloudTranslationService;
import com.fasterxml.jackson.databind.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.bson.Document;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.client.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Test-classpath-only launcher. No production endpoint, profile, or bypass is added. */
@SuppressWarnings("removal")
public class IsolatedEmailVerification {
    static final String SCHOOL="Synthetic Isolation School",TEACHER="teacher@example.test",ADMIN="admin@example.test",STUDENT="student@example.test";
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static final List<MimeMessage> SMTP=new CopyOnWriteArrayList<>(),GMAIL=new CopyOnWriteArrayList<>();
    static final AtomicReference<String> SMTP_MODE=new AtomicReference<>("OK"),GMAIL_MODE=new AtomicReference<>("OK"),OAUTH_USER=new AtomicReference<>(TEACHER);
    static final AtomicInteger PASSED=new AtomicInteger(), BLOCKED_CONNECTIONS=new AtomicInteger(), BLOCKED_CREDENTIAL_READS=new AtomicInteger();
    static ConfigurableApplicationContext context;
    static MongoTemplate mongo;
    static RequiredNotificationService notifications;
    static JavaMailSender smtp;
    static Map<String,Object> overrides=new ConcurrentHashMap<>();
    static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);PASSED.incrementAndGet();System.out.println("ISOLATED_PASS "+label);}
    static class LoopbackOnly extends SecurityManager {
        @Override public void checkPermission(Permission p){if(p instanceof RuntimePermission && "setSecurityManager".equals(p.getName()))throw new SecurityException("Isolation guard cannot be removed");}
        @Override public void checkPermission(Permission p,Object context){checkPermission(p);}
        @Override public void checkConnect(String host,int port){if(!Set.of("127.0.0.1","localhost","::1","0:0:0:0:0:0:0:1").contains(host) && !(port==-1 && (host.equals(System.getenv("COMPUTERNAME")) || host.equals("kubernetes.docker.internal")))){BLOCKED_CONNECTIONS.incrementAndGet();System.err.println("ISOLATED_BLOCKED_CONNECT host="+host+" port="+port);throw new SecurityException("Non-loopback outbound connection blocked");}}
        @Override public void checkRead(String file){String p=file.replace('\\','/').toLowerCase(Locale.ROOT);if(p.contains("/gcloud/")||p.endsWith("/application_default_credentials.json")||p.contains("/.aws/")){BLOCKED_CREDENTIAL_READS.incrementAndGet();System.err.println("ISOLATED_BLOCKED_CREDENTIAL_READ basename="+Path.of(file).getFileName());throw new SecurityException("Cloud credential discovery blocked");}}
    }
    static void start() throws Exception {
        System.setProperty("spring.config.location","classpath:/isolated-email.properties");
        System.setProperty("spring.profiles.active","isolated-email");
        Path key=Path.of("target/isolated-email-test-key");
        if(!Files.exists(key)){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);Files.writeString(key,Base64.getEncoder().encodeToString(bytes));}
        overrides.put("reps.email.gmail.encryption-key",Files.readString(key));
        smtp=mock(JavaMailSender.class);
        when(smtp.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i->{MimeMessage message=i.getArgument(0);allowRecipients(message);if("AUTH".equals(SMTP_MODE.get()))throw new MailAuthenticationException("Synthetic rejected authentication");if("TIMEOUT".equals(SMTP_MODE.get()))throw new MailSendException("Synthetic uncertain timeout");SMTP.add(message);return null;}).when(smtp).send(any(MimeMessage.class));
        RestTemplate oauth=GoogleOAuthTokenStore.googleHttp();
        var oauthServer=MockRestServiceServer.bindTo(oauth).ignoreExpectOrder(true).build();
        oauthServer.expect(ExpectedCount.manyTimes(),requestTo("https://oauth2.googleapis.com/token")).andRespond(request->withSuccess("{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"expires_in\":3600,\"token_type\":\"Bearer\",\"scope\":\"openid email https://www.googleapis.com/auth/gmail.send\"}",MediaType.APPLICATION_JSON).createResponse(request));
        oauthServer.expect(ExpectedCount.manyTimes(),requestTo("https://openidconnect.googleapis.com/v1/userinfo")).andRespond(request->withSuccess("{\"email_verified\":true,\"email\":\""+OAUTH_USER.get()+"\",\"sub\":\"synthetic-subject-"+OAUTH_USER.get()+"\"}",MediaType.APPLICATION_JSON).createResponse(request));
        oauthServer.expect(ExpectedCount.manyTimes(),requestTo("https://oauth2.googleapis.com/revoke")).andRespond(withSuccess());
        RestTemplate gmail=new RestTemplate();
        var gmailServer=MockRestServiceServer.bindTo(gmail).build();
        gmailServer.expect(ExpectedCount.manyTimes(),requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")).andRespond(request->{
            if("REJECT".equals(GMAIL_MODE.get()))return withStatus(HttpStatus.BAD_REQUEST).createResponse(request);
            if("REVOKED".equals(GMAIL_MODE.get()))return withStatus(HttpStatus.UNAUTHORIZED).createResponse(request);
            if("TIMEOUT".equals(GMAIL_MODE.get()))throw new IOException("Synthetic uncertain Gmail transport failure");
            var payload=JSON.readTree(((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString());
            try { MimeMessage message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(Base64.getUrlDecoder().decode(payload.path("raw").asText())));allowRecipients(message);GMAIL.add(message); } catch(Exception invalid) { throw new IOException("Synthetic MIME validation failed",invalid); }
            return withSuccess("{\"id\":\"mock-message-"+GMAIL.size()+"\",\"threadId\":\"mock-thread\"}",MediaType.APPLICATION_JSON).createResponse(request);
        });
        var registration=ClientRegistration.withRegistrationId("google").clientId("synthetic-client").clientSecret("synthetic-secret").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1:18089/login/oauth2/code/google").authorizationUri("http://127.0.0.1:1/authorize").tokenUri("http://127.0.0.1:1/token").userInfoUri("http://127.0.0.1:1/userinfo").userNameAttributeName("sub").scope("openid","email","profile").build();
        var registrations=new InMemoryClientRegistrationRepository(registration);
        SpringApplication app=new SpringApplication(DemoGcloudApplication.class);
        app.addInitializers(c->{
            c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-runtime-only",overrides));
            c.addBeanFactoryPostProcessor(factory->{
                var bf=(DefaultListableBeanFactory)factory;
                replace(bf,"stripeConfig",mock(StripeConfig.class));replace(bf,"stripeBillingService",mock(StripeBillingService.class));replace(bf,"stripeWebhookService",mock(StripeWebhookService.class));
                replace(bf,"mailSender",smtp);replace(bf,"clientRegistrationRepository",registrations);
                if(bf.containsBeanDefinition("googleOAuthTokenStore"))bf.removeBeanDefinition("googleOAuthTokenStore");
                bf.registerBeanDefinition("googleOAuthTokenStore",BeanDefinitionBuilder.genericBeanDefinition(GoogleOAuthTokenStore.class,()->new GoogleOAuthTokenStore(c.getEnvironment(),bf.getBean(MongoTemplate.class),Optional.of(registrations),oauth)).getBeanDefinition());
                if(bf.containsBeanDefinition("googleMailService"))bf.removeBeanDefinition("googleMailService");
                bf.registerBeanDefinition("googleMailService",BeanDefinitionBuilder.genericBeanDefinition(GoogleMailService.class,()->new GoogleMailService(bf.getBean(GoogleOAuthTokenStore.class),gmail)).getBeanDefinition());
                String uri=c.getEnvironment().getProperty("spring.data.mongodb.uri");
                if(!"mongodb://127.0.0.1:27029/reps_email_isolated_20261021?directConnection=true&serverSelectionTimeoutMS=3000".equals(uri)||!"".equals(c.getEnvironment().getProperty("spring.config.import")))throw new IllegalStateException("Isolation config rejected");
            });
        });
        context=app.run();mongo=context.getBean(MongoTemplate.class);notifications=context.getBean(RequiredNotificationService.class);
        check(mongo.getDb().getName().equals("reps_email_isolated_20261021"),"real disposable loopback MongoDB connected");
        check(context.getBean(JavaMailSender.class)==smtp,"only mocked SMTP bean exists");
        var translation = context.getBean(GoogleCloudTranslationService.class);
        check(!mockingDetails(translation).isMock() && "Synthetic".equals(translation.translate("Synthetic","en","en")),"real English translation service starts without cloud credential discovery");
    }
    static void replace(DefaultListableBeanFactory bf,String name,Object bean){if(bf.containsBeanDefinition(name))bf.removeBeanDefinition(name);bf.registerSingleton(name,bean);}
    static void allowRecipients(MimeMessage message) throws Exception {for(Address a:message.getAllRecipients())if(!a.toString().endsWith("@example.test"))throw new SecurityException("Non-synthetic recipient blocked");}
    static void seed(){
        var school=new School();school.setSchoolIdNumber("synthetic-school");school.setSchoolName(SCHOOL);context.getBean(SchoolRepository.class).save(school);
        for(String email:List.of(TEACHER,ADMIN,"google@example.test")){
            var role=new RoleModel();role.setRole(email.equals(ADMIN)?"ADMIN":"TEACHER");
            var user=new UserModel();user.setId(email);user.setUsername(email);user.setFirstName("Synthetic");user.setLastName("Staff");user.setSchool(SCHOOL);user.setRoles(Set.of(role));user.setEnabled(true);user.setPassword(context.getBean(PasswordEncoder.class).encode("Synthetic-password-only"));context.getBean(UserRepository.class).save(user);
            var employee=new Employee();employee.setEmployeeId(email);employee.setEmail(email);employee.setFirstName("Synthetic");employee.setLastName("Staff");employee.setSchool(SCHOOL);employee.setRoles(Set.of(role));employee.setCurrency(100);employee.setClasses(new ArrayList<>());context.getBean(EmployeeRepository.class).save(employee);
        }
        var student=new Student();student.setStudentIdNumber("synthetic-student");student.setFirstName("Synthetic");student.setLastName("Student");student.setStudentEmail(STUDENT);student.setParentEmail("guardian@example.test");student.setAdminEmail(ADMIN);student.setSchool(SCHOOL);student.setCurrency(0);student.setPoints(0);student.setSpotters(List.of(TEACHER));context.getBean(StudentRepository.class).save(student);
    }
    static String token(String email,String method){return context.getBean(JwtUtils.class).generateToken(context.getBean(CustomUserDetailsService.class).loadUserByUsername(email),method);}
    static JsonNode http(String method,String path,Object body,String email,String login,String action) throws Exception {
        var builder=java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:18089"+path)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token(email,login));
        if(action!=null)builder.header("X-REPS-Action-Id",action);
        if(body==null)builder.method(method,java.net.http.HttpRequest.BodyPublishers.noBody());else builder.header("Content-Type","application/json").method(method,java.net.http.HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var response=java.net.http.HttpClient.newBuilder().proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI u,SocketAddress a,IOException e){}}).build().send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()/100!=2)throw new AssertionError(method+" "+path+" returned "+response.statusCode());return JSON.readTree(response.body());
    }
    static void connect(String email) throws Exception {OAUTH_USER.set(email);String proof="p".repeat(43);var begin=http("POST","/auth/gmail/connect",Map.of("browserProof",proof),email,"PASSWORD",null);String url=begin.path("authorizationUrl").asText();String state=Arrays.stream(URI.create(url).getRawQuery().split("&")).filter(p->p.startsWith("state=")).map(p->URLDecoder.decode(p.substring(6),java.nio.charset.StandardCharsets.UTF_8)).findFirst().orElseThrow();http("POST","/auth/gmail/complete",Map.of("browserProof",proof,"state",state,"code","synthetic-code"),email,"PASSWORD",null);check(context.getBean(GoogleOAuthTokenStore.class).getConnectionState(email)==GoogleOAuthTokenStore.ConnectionState.CONNECTED,"mocked OAuth connection saved for "+email);}
    static OfficeReferral create(String email,String action) throws Exception {var json=http("POST","/officeReferral/v1/startPunish/adminReferral",List.of(Map.of("studentEmail",STUDENT,"teacherEmail","forged@example.test","classPeriod","1","referralDescription",List.of("Synthetic referral only"),"referralCode",Map.of("codeKey",1,"codeName","Synthetic referral"))),email,email.startsWith("google@")?"GOOGLE":"PASSWORD",action);return mongo.findById(json.get(0).path("officeReferralId").asText(),OfficeReferral.class);}
    static OfficeReferral reload(OfficeReferral r){return mongo.findById(r.getOfficeReferralId(),OfficeReferral.class);}
    static String state(OfficeReferral r){return reload(r).getRequiredNotifications().get(0).getState();}
    static void dispatch(OfficeReferral r){notifications.dispatchOne("office",r.getOfficeReferralId(),r.getRequiredNotifications().get(0).getNotificationId());}
    static void asStaff(String email){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,"n/a",List.of()));}
    static void verifyPhaseOne() throws Exception {
        if(!mongo.getDb().getName().equals("reps_email_isolated_20261021"))throw new SecurityException("Disposable database required");
        mongo.getDb().drop();seed();String action=UUID.randomUUID().toString();OfficeReferral first=create(TEACHER,action);
        check(first.getRequiredNotifications().size()==1&&first.getTeacherEmail().equals(TEACHER),"HTTP create atomically stores actor-bound intent");
        check(create(TEACHER,action).getOfficeReferralId().equals(first.getOfficeReferralId()),"duplicate HTTP action returns same case");
        dispatch(first);check(state(first).equals("ACCEPTED")&&SMTP.size()==1&&GMAIL.isEmpty(),"password teacher never connected uses mocked SMTP");
        check(SMTP.get(0).getReplyTo()[0].toString().equals(TEACHER),"SMTP Reply-To is verified teacher");
        connect(TEACHER);var authorization=mongo.getCollection("oauth2_authorized_clients").find(new Document("_id",TEACHER)).first();String stored=authorization.toJson();check(!stored.contains("synthetic-access")&&!stored.contains("synthetic-refresh")&&stored.contains("cipher"),"actual Mongo OAuth payload encrypted");
        var gmailCase=create(TEACHER,UUID.randomUUID().toString());dispatch(gmailCase);check(state(gmailCase).equals("ACCEPTED")&&GMAIL.size()==1,"password login connected Gmail uses Gmail");
        connect("google@example.test");var googleCase=create("google@example.test",UUID.randomUUID().toString());http("GET","/auth/capabilities",null,"google@example.test","GOOGLE",null);dispatch(googleCase);check(state(googleCase).equals("ACCEPTED")&&GMAIL.size()==2,"synthetic Google-login identity uses authorized Gmail");
        http("POST","/officeReferral/v1/closeId",Map.of("id",gmailCase.getOfficeReferralId(),"comment","Synthetic close"),ADMIN,"PASSWORD",UUID.randomUUID().toString());var closed=reload(gmailCase);var closure=closed.getRequiredNotifications().get(1);notifications.dispatchOne("office",closed.getOfficeReferralId(),closure.getNotificationId());closed=reload(closed);
        check(closed.getStatus().equals("CLOSED")&&closed.getOriginalAuthorEmail().equals(TEACHER)&&closed.getRequiredNotifications().get(1).getActor().equals(ADMIN),"administrator closure preserves original teacher and records administrator actor");
        MimeMessage admin=SMTP.get(SMTP.size()-1);check(admin.getReplyTo()[0].toString().equals(ADMIN)&&Arrays.stream(admin.getRecipients(Message.RecipientType.CC)).anyMatch(a->a.toString().equals(TEACHER)),"admin SMTP Reply-To and eligible teacher CC correct");
        check(admin.getHeader("In-Reply-To")!=null&&admin.getSubject().startsWith("REPS case "),"case threading and privacy subject persisted");
        SMTP_MODE.set("AUTH");var failed=create(ADMIN,UUID.randomUUID().toString());dispatch(failed);check(state(failed).equals("FAILED"),"known transport rejection preserves saved case and FAILED intent");SMTP_MODE.set("OK");int before=SMTP.size();asStaff(ADMIN);notifications.retry("office",failed.getOfficeReferralId(),failed.getRequiredNotifications().get(0).getNotificationId());dispatch(failed);dispatch(failed);check(state(failed).equals("ACCEPTED")&&SMTP.size()==before+1,"safe retry sends once; accepted intent cannot be claimed twice");
        SMTP_MODE.set("TIMEOUT");var uncertain=create(ADMIN,UUID.randomUUID().toString());dispatch(uncertain);check(state(uncertain).equals("UNCERTAIN"),"uncertain transport outcome durably blocks blind retry");boolean blocked=false;try{notifications.retry("office",uncertain.getOfficeReferralId(),uncertain.getRequiredNotifications().get(0).getNotificationId());}catch(IllegalStateException e){blocked=true;}check(blocked,"UNCERTAIN cannot be retried");SMTP_MODE.set("OK");
        GMAIL_MODE.set("REVOKED");var revoked=create(TEACHER,UUID.randomUUID().toString());dispatch(revoked);check(state(revoked).equals("RECONNECT_REQUIRED"),"Gmail rejection durably requires reconnect without SMTP fallback");GMAIL_MODE.set("OK");connect(TEACHER);
        var concurrent=create(ADMIN,UUID.randomUUID().toString());int start=SMTP.size();var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->dispatch(concurrent));var b=pool.submit(()->dispatch(concurrent));a.get();b.get();}finally{pool.shutdown();}check(SMTP.size()==start+1&&state(concurrent).equals("ACCEPTED"),"two actual Mongo dispatcher claims accept only one sender");
        var left=reload(first);var right=reload(first);left.setMapIndex(1);context.getBean(OfficeReferralRepository.class).save(left);boolean conflict=false;try{right.setMapIndex(2);context.getBean(OfficeReferralRepository.class).save(right);}catch(org.springframework.dao.OptimisticLockingFailureException e){conflict=true;}check(conflict,"actual Mongo optimistic locking rejects stale case save");
        asStaff(ADMIN);boolean deletion=false;try{notifications.deleteLegacyCase("office",first.getOfficeReferralId());}catch(IllegalStateException e){deletion=true;}check(deletion&&reload(first)!=null,"retained notification prevents destructive case deletion");
        var store=context.getBean(GoogleOAuthTokenStore.class);
        org.springframework.security.oauth2.client.OAuth2AuthorizedClient grant=store.loadAuthorizedClient("reps-gmail",TEACHER);
        var expiring=new org.springframework.security.oauth2.core.OAuth2AccessToken(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,"synthetic-expiring",Instant.now().minusSeconds(60),Instant.now().plusSeconds(5),grant.getAccessToken().getScopes());
        store.saveAuthorizedClient(new org.springframework.security.oauth2.client.OAuth2AuthorizedClient(grant.getClientRegistration(),TEACHER,expiring,grant.getRefreshToken()),new UsernamePasswordAuthenticationToken(TEACHER,"n/a",List.of()));
        check(store.getToken(TEACHER).orElseThrow().getAccessTokenExpiresAt().isAfter(Instant.now().plusSeconds(3000)),"expiring persisted Gmail token refreshed through mocked HTTP");
        mongo.getCollection("Punishments").insertOne(new Document("_id","synthetic-legacy-case").append("studentEmail",STUDENT).append("teacherEmail",TEACHER).append("school",SCHOOL).append("infractionName","Synthetic concern").append("infractionLevel","1").append("status","OPEN").append("timeCreated",Date.from(Instant.now())).append("infractionDescription",List.of("Synthetic only")));
        var legacy=mongo.findById("synthetic-legacy-case",Punishment.class);legacy.setMapIndex(1);context.getBean(PunishRepository.class).save(legacy);
        check(mongo.getCollection("Punishments").countDocuments(new Document("_id","synthetic-legacy-case"))==1,"legacy unversioned case updated without duplicate insert");
        http("PUT","/punish/v1/archived/forged@example.test/synthetic-legacy-case","Synthetic archive",ADMIN,"PASSWORD",UUID.randomUUID().toString());
        var archived=mongo.findById("synthetic-legacy-case",Punishment.class);
        check(archived.isArchived()&&ADMIN.equals(archived.getArchivedBy())&&archived.getRequiredNotifications().size()==1,"archive preserves case and durable intent with verified actor");
        overrides.put("reps.email.notifications.enabled","false");
        check(!http("GET","/email/v1/notifications",null,TEACHER,"PASSWORD",null).path("available").asBoolean(),"isolated disabled gate hides managed controls");
        overrides.put("reps.email.notifications.enabled","true");
        var pending=create(ADMIN,UUID.randomUUID().toString());var interrupted=create(ADMIN,UUID.randomUUID().toString());mongo.updateFirst(Query.query(Criteria.where("_id").is(interrupted.getOfficeReferralId())),new Update().set("requiredNotifications.0.state","DISPATCHING").set("requiredNotifications.0.claimedAt",Date.from(Instant.now().minusSeconds(600))).set("requiredNotifications.0.attemptId","synthetic-crashed-attempt"),OfficeReferral.class);
        Files.writeString(Path.of("target/isolated-restart-cases.json"),JSON.writeValueAsString(Map.of("pending",pending.getOfficeReferralId(),"interrupted",interrupted.getOfficeReferralId(),"uncertain",uncertain.getOfficeReferralId(),"accepted",first.getOfficeReferralId())));
        check(true,"pending and interrupted claims prepared for full process restart");
    }
    static void verifyPhaseTwo() throws Exception {
        var cases=JSON.readTree(Files.readString(Path.of("target/isolated-restart-cases.json")));var store=context.getBean(GoogleOAuthTokenStore.class);
        check(store.getToken(TEACHER).isPresent(),"encrypted Gmail grant survives full JVM/application restart");
        notifications.dispatchPending();var pending=mongo.findById(cases.path("pending").asText(),OfficeReferral.class);var interrupted=mongo.findById(cases.path("interrupted").asText(),OfficeReferral.class);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(Set.of("PENDING","DISPATCHING").contains(state(pending)) && System.nanoTime()<deadline)Thread.sleep(50);
        check(state(pending).equals("ACCEPTED"),"pending intent recovered after full process restart");check(state(interrupted).equals("UNCERTAIN"),"interrupted claim recovers as UNCERTAIN without resend");
        check(state(mongo.findById(cases.path("uncertain").asText(),OfficeReferral.class)).equals("UNCERTAIN"),"uncertain state survives full process restart");
        http("POST","/auth/gmail/disconnect",Map.of(),TEACHER,"PASSWORD",null);check(store.getConnectionState(TEACHER)==GoogleOAuthTokenStore.ConnectionState.DISCONNECTED&&!mongo.getCollection("oauth2_authorized_clients").find(new Document("_id",TEACHER)).first().toJson().contains("cipher"),"disconnect durably removes encrypted grant");connect(TEACHER);
        String teacherToken=token(TEACHER,"PASSWORD");Files.writeString(Path.of("target/isolated-ui-session.json"),JSON.writeValueAsString(Map.of("Authorization",teacherToken,"email",TEACHER,"role","TEACHER","baseUrl","http://127.0.0.1:18089")));
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1||!Set.of("phase1","phase2").contains(args[0]))throw new IllegalArgumentException("Explicit phase1 or phase2 required");
        String machine=Objects.toString(System.getenv("COMPUTERNAME"),"synthetic-host");if(!machine.matches("[A-Za-z0-9-]+"))throw new SecurityException("Invalid local hostname");
        Path hosts=Path.of("target/isolated-hosts").toAbsolutePath();Files.writeString(hosts,"127.0.0.1 localhost "+machine+" kubernetes.docker.internal\n::1 localhost\n");System.setProperty("jdk.net.hosts.file",hosts.toString());
        System.setSecurityManager(new LoopbackOnly());
        if(!InetAddress.getByName(machine).isLoopbackAddress() || !InetAddress.getByName("kubernetes.docker.internal").isLoopbackAddress())throw new SecurityException("Local hostname fixture must resolve only to loopback");
        try{new Socket("example.com",443);throw new AssertionError("Network guard failed");}catch(SecurityException expected){System.out.println("ISOLATED_PASS non-loopback egress denied before Spring startup");}
        try{start();if(args[0].equals("phase1"))verifyPhaseOne();else verifyPhaseTwo();check(BLOCKED_CONNECTIONS.get()==1&&BLOCKED_CREDENTIAL_READS.get()==0,"no unexpected external connections or credential discovery during application verification (blocked="+BLOCKED_CONNECTIONS.get()+", credentials="+BLOCKED_CREDENTIAL_READS.get()+")");System.out.println("ISOLATED_RESULT "+args[0]+" checks="+PASSED.get()+" passed");
            if(args[0].equals("phase2")){Files.writeString(Path.of("target/isolated-ready"),"loopback mock-only backend ready");while(!Files.exists(Path.of("target/isolated-stop")))Thread.sleep(250);}
        }finally{if(context!=null)context.close();SecurityContextHolder.clearContext();}
    }
}
