package com.reps.demogcloud.services.currency;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.*;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.enums.InfractionType;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.email.*;
import com.reps.demogcloud.services.punishment.PunishmentCreationService;
import com.reps.demogcloud.utils.PunishmentUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real repositories, sessions and transaction manager. No Boot startup, cloud imports or provider clients.
 * Explicit opt-in URI is restricted to loopback; each run owns a new disposable database. */
@EnabledIfEnvironmentVariable(named="REPS_CURRENCY_TEST_MONGODB_URI",matches=".+")
class CurrencyMongoIntegrationTest {
    static AnnotationConfigApplicationContext context;
    static MongoClient client;
    static MongoTemplate mongo;
    static String database;
    static final String STAFF="teacher@example.test", CHILD="child@example.test", CHILD2="second@example.test", SCHOOL="Synthetic";
    StudentRepository students;EmployeeRepository employees;PunishRepository cases;UserRepository accounts;
    UserContextService users;WorkflowAccessService access;RequiredNotificationService notifications;
    CurrencyTransactions transactions;CurrencyService currency;PunishmentCreationService creation;EmailService legacy;
    @Configuration @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    static class Config {
        @Bean MongoTemplate mongoTemplate(){return mongo;}
    }
    @BeforeAll static void connect() {
        var uri=new ConnectionString(System.getenv("REPS_CURRENCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")),"Test URI must be loopback only");
        assertNull(uri.getCredential(),"Test database must not use deployed credentials");
        database="reps_currency_test_"+UUID.randomUUID().toString().replace("-","");
        client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
        var hello=mongo.executeCommand(new org.bson.Document("hello",1));assertTrue(hello.containsKey("setName"));
        context=new AnnotationConfigApplicationContext(Config.class);
    }
    @AfterAll static void close() {if(context!=null)context.close();if(mongo!=null)mongo.getDb().drop();if(client!=null)client.close();}
    @BeforeEach void setup() {
        for(String name:mongo.getCollectionNames()) mongo.getCollection(name).deleteMany(new org.bson.Document());
        students=context.getBean(StudentRepository.class);employees=context.getBean(EmployeeRepository.class);
        cases=context.getBean(PunishRepository.class);accounts=context.getBean(UserRepository.class);
        // Create namespaces outside transactions (also supports older replica-set versions).
        for(Class<?> type:List.of(CurrencyOperation.class,Punishment.class)) if(!mongo.collectionExists(type))mongo.createCollection(type);
        var teacher=new Employee();teacher.setEmployeeId("teacher");teacher.setEmail(STAFF);teacher.setSchool(SCHOOL);teacher.setCurrency(20);
        teacher.setRoles(Set.of(new RoleModel(null,"TEACHER")));teacher.setClasses(List.of());mongo.insert(teacher);
        var account=new UserModel();account.setUsername(STAFF);account.setSchool(SCHOOL);account.setRoles(teacher.getRoles());mongo.insert(account);
        student("child",CHILD);student("second",CHILD2);
        var school=new School();school.setSchoolIdNumber("school");school.setSchoolName(SCHOOL);mongo.insert(school);
        mongo.insert(new Infraction("positive",InfractionType.POSITIVE.getLabel(),"1"));
        users=new UserContextService(accounts,students,employees);
        access=new WorkflowAccessService(accounts,employees,students,context.getBean(SchoolRepository.class),cases,context.getBean(AssignmentTemplateRepository.class),users);
        var router=mock(EmailRoutingService.class);
        notifications=spy(new RequiredNotificationService(mongo,new MockEnvironment(),students,users,router,mock(EmailTemplateBuilderService.class)));
        transactions=new CurrencyTransactions(mongo,users,notifications);
        currency=new CurrencyService(transactions,mongo,employees,students,cases,access,users,notifications);
        legacy=mock(EmailService.class);
        var utils=new PunishmentUtils(students,context.getBean(InfractionRepository.class),cases,context.getBean(SchoolRepository.class),legacy,
                mock(EmployeeService.class),mock(GuidanceService.class),mock(OfficeReferralService.class),notifications);
        creation=new PunishmentCreationService(utils,mock(AssignmentService.class),users,mock(EmailSenderService.class),notifications,currency);
        action(UUID.randomUUID().toString());
    }
    void student(String id,String email) {var s=new Student();s.setStudentIdNumber(id);s.setStudentEmail(email);s.setSchool(SCHOOL);s.setCurrency(0);s.setSpotters(List.of(STAFF));mongo.insert(s);}
    void action(String id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(STAFF,"",List.of()));
        var request=new MockHttpServletRequest();request.addHeader("X-REPS-Action-Id",id);RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();}
    PunishmentFormRequest form(String email,int amount) {return PunishmentFormRequest.builder().studentEmail(email).teacherEmail("spoof@example.test")
            .infractionName(InfractionType.POSITIVE.getLabel()).infractionDescription("Synthetic recognition").currency(amount).build();}
    List<PunishmentResponse> award(int amount) throws Exception{return creation.createNewPunishFormBulk(List.of(form(CHILD,amount),form(CHILD2,amount)));}
    int teacherBalance(){return employees.findById("teacher").orElseThrow().getCurrency();}
    int childBalance(String id){return students.findById(id).orElseThrow().getCurrency();}
    void empty(){assertEquals(20,teacherBalance());assertEquals(0,childBalance("child"));assertEquals(0,childBalance("second"));assertEquals(0,cases.count());assertEquals(0,mongo.count(new Query(),CurrencyOperation.class));}
    @Test void singleAwardCommitsReceiptCreditRecognitionAndIntentWithoutTransport() throws Exception {
        var response=creation.createNewPunishForm(form(CHILD,3));
        assertEquals("COMMITTED",response.getCurrencyState());assertEquals(17,teacherBalance());assertEquals(3,childBalance("child"));
        assertEquals(3,response.getPunishment().getCurrencyAwarded());assertEquals(STAFF,response.getPunishment().getTeacherEmail());
        assertEquals(1,response.getPunishment().getRequiredNotifications().size());assertEquals(1,mongo.count(new Query(),CurrencyOperation.class));
        verifyNoInteractions(legacy);
    }
    @Test void multiAwardUsesFullTotal() throws Exception {var result=award(4);assertEquals(2,result.size());assertEquals(12,teacherBalance());assertEquals(4,childBalance("child"));assertEquals(4,childBalance("second"));}
    @Test void insufficientTotalRejectsEntireBatch(){assertThrows(ResponseStatusException.class,()->award(11));empty();}
    @Test void failureOnSecondRecognitionRollsBackEverythingThenIdenticalRetrySucceeds() throws Exception {
        AtomicInteger calls=new AtomicInteger();doAnswer(i->{if(calls.incrementAndGet()==2)throw new IllegalStateException("Synthetic failure");return i.callRealMethod();}).when(notifications).saveCurrencyCreated(any());
        assertThrows(IllegalStateException.class,()->award(4));empty();
        doCallRealMethod().when(notifications).saveCurrencyCreated(any());award(4);assertEquals(12,teacherBalance());assertEquals(2,cases.count());
    }
    @Test void duplicateActionDoesNotChargeAgain() throws Exception {var first=award(3);var second=award(3);assertEquals(first.get(0).getCurrencyOperationId(),second.get(0).getCurrencyOperationId());assertEquals(14,teacherBalance());assertEquals(2,cases.count());}
    @Test void actionCannotChangePayload() throws Exception {award(2);assertEquals(409,assertThrows(ResponseStatusException.class,()->award(3)).getStatusCode().value());assertEquals(16,teacherBalance());}
    @Test void simultaneousSameActionCommitsOnce() throws Exception {
        concurrent(true,3);assertEquals(14,teacherBalance());assertEquals(3,childBalance("child"));assertEquals(2,cases.count());assertEquals(1,mongo.count(new Query(),CurrencyOperation.class));
    }
    @Test void concurrentDifferentActionsNeverOverdraw() throws Exception {
        var failures=concurrent(false,6);assertEquals(1,failures);assertEquals(8,teacherBalance());assertEquals(6,childBalance("child"));assertEquals(2,cases.count());
    }
    int concurrent(boolean same,int amount) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var shared=UUID.randomUUID().toString();
        try {var tasks=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{action(same?shared:UUID.randomUUID().toString());start.await();try{award(amount);return true;}catch(ResponseStatusException rejected){assertEquals(409,rejected.getStatusCode().value());return false;}finally{clear();}}));
            start.countDown();int failed=0;for(var task:tasks)if(!task.get(30,TimeUnit.SECONDS))failed++;return failed;
        }finally{pool.shutdownNow();}
    }
    @Test void invalidSecondStudentIsRejectedBeforeCurrencyWrites(){mongo.updateFirst(Query.query(Criteria.where("_id").is("second")),new Update().set("school","Other"),Student.class);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->award(4));empty();}
    @Test void archivedStudentCannotReceive(){mongo.updateFirst(Query.query(Criteria.where("_id").is("child")),new Update().set("archived",true),Student.class);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->creation.createNewPunishForm(form(CHILD,3)));empty();}
    @Test void unrelatedTeacherCannotAward(){mongo.updateFirst(Query.query(Criteria.where("_id").is("child")),new Update().set("spotters",List.of()),Student.class);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->creation.createNewPunishForm(form(CHILD,3)));empty();}
    @Test void negativeAwardAndDuplicateStudentsRejected(){assertThrows(IllegalArgumentException.class,()->award(-1));assertThrows(IllegalArgumentException.class,()->creation.createNewPunishFormBulk(List.of(form(CHILD,2),form(CHILD,2))));empty();}
    @Test void zeroCurrencyRecognitionDoesNotTransfer(){assertDoesNotThrow(()->award(0));assertEquals(20,teacherBalance());assertEquals(2,cases.count());}
    @Test void staleStudentAndEmployeeSavesCannotOverwriteCommittedBalances() throws Exception {
        var staleStudent=students.findById("child").orElseThrow();var staleTeacher=employees.findById("teacher").orElseThrow();award(4);
        staleStudent.setFirstName("Updated");staleTeacher.setFirstName("Updated");students.save(staleStudent);employees.save(staleTeacher);
        assertEquals(4,childBalance("child"));assertEquals(12,teacherBalance());
        staleStudent.setCurrency(9999);students.saveAll(List.of(staleStudent));assertEquals(4,childBalance("child"));
    }
    CurrencySpendRequest spend(String email,int amount){var r=new CurrencySpendRequest();r.setStudentEmail(email);r.setCurrencyTransferred(amount);return r;}
    @Test void redemptionIsPositiveBoundedAtomicAndIdempotent() throws Exception {
        award(5);action(UUID.randomUUID().toString());var request=List.of(spend(CHILD,3),spend(CHILD2,2));currency.spend(request);currency.spend(request);
        assertEquals(2,childBalance("child"));assertEquals(3,childBalance("second"));
        action(UUID.randomUUID().toString());assertThrows(ResponseStatusException.class,()->currency.spend(List.of(spend(CHILD,1),spend(CHILD2,9))));assertEquals(2,childBalance("child"));
        for(int amount:List.of(-1,0))assertThrows(IllegalArgumentException.class,()->currency.spend(List.of(spend(CHILD,amount))));
    }
    @Test void legacyNullWalletIsNotAutomaticallyReinitialized(){mongo.updateFirst(Query.query(Criteria.where("_id").is("child")),new Update().unset("currency"),Student.class);assertThrows(ResponseStatusException.class,()->award(2));assertNull(students.findById("child").orElseThrow().getCurrency());assertEquals(20,teacherBalance());}
    @Test void administratorResetRequiresSameSchoolAndIsIdempotent() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->currency.resetSchool(SCHOOL));
        var account=accounts.findByUsername(STAFF);account.setRoles(Set.of(new RoleModel(null,"ADMIN")));mongo.save(account);
        currency.resetSchool(SCHOOL);assertEquals(5,teacherBalance());
        mongo.updateFirst(Query.query(Criteria.where("_id").is("teacher")),new Update().set("currency",3),Employee.class);
        currency.resetSchool(SCHOOL);assertEquals(3,teacherBalance());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->currency.resetSchool("Other"));
    }
    @Test void restartedServiceReadsExistingActionReceipt() throws Exception {award(2);transactions=new CurrencyTransactions(mongo,users,notifications);currency=new CurrencyService(transactions,mongo,employees,students,cases,access,users,notifications);creation=new PunishmentCreationService(new PunishmentUtils(students,context.getBean(InfractionRepository.class),cases,context.getBean(SchoolRepository.class),legacy,mock(EmployeeService.class),mock(GuidanceService.class),mock(OfficeReferralService.class),notifications),mock(AssignmentService.class),users,mock(EmailSenderService.class),notifications,currency);award(2);assertEquals(16,teacherBalance());assertEquals(2,cases.count());}

    @Test void notificationIntentIsInvisibleToDispatcherUntilCommit() throws Exception {
        doAnswer(i->{Object saved=i.callRealMethod();
            assertEquals(0,client.getDatabase(database).getCollection("Punishments").countDocuments());
            return saved;
        }).when(notifications).saveCurrencyCreated(any());
        award(3);assertEquals(2,client.getDatabase(database).getCollection("Punishments").countDocuments());
        assertTrue(cases.findAll().stream().allMatch(p->p.getRequiredNotifications().size()==1));
        verifyNoInteractions(legacy);
    }
    @Test void transientFailureRollsBackBeforeBoundedRetry() throws Exception {
        AtomicInteger count=new AtomicInteger();
        doAnswer(i->{Object saved=i.callRealMethod();if(count.incrementAndGet()==1){
            var failure=new com.mongodb.MongoException(112,"Synthetic transient conflict");failure.addLabel("TransientTransactionError");throw failure;
        }return saved;}).when(notifications).saveCurrencyCreated(any());
        award(4);assertEquals(12,teacherBalance());assertEquals(4,childBalance("child"));assertEquals(2,cases.count());assertEquals(1,mongo.count(new Query(),CurrencyOperation.class));
    }
    @Test void overflowDoesNotPartiallyCreditOrDebit() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is("second")),new Update().set("currency",Integer.MAX_VALUE),Student.class);
        assertThrows(ResponseStatusException.class,()->award(1));assertEquals(20,teacherBalance());assertEquals(0,childBalance("child"));assertEquals(Integer.MAX_VALUE,childBalance("second"));assertEquals(0,cases.count());
    }
    @Test void missingDescriptionOnSecondItemRollsBackBeforeDebit() {
        var invalid=form(CHILD2,3);invalid.setInfractionDescription(" ");
        assertThrows(IllegalArgumentException.class,()->creation.createNewPunishFormBulk(List.of(form(CHILD,3),invalid)));empty();
    }
    @Test void legacyRecognitionAndUnknownProfileFieldsArePreserved() throws Exception {
        var historic=new Punishment();historic.setPunishmentId("historic");historic.setStudentEmail(CHILD);historic.setSchool(SCHOOL);historic.setStatus("SO");mongo.insert(historic);
        mongo.getCollection("students").updateOne(new org.bson.Document("_id","child"),new org.bson.Document("$set",new org.bson.Document("unknownLegacyField","retained")));
        var stale=students.findById("child").orElseThrow();award(2);students.save(stale);
        assertEquals("retained",mongo.getCollection("students").find(new org.bson.Document("_id","child")).first().getString("unknownLegacyField"));
        assertNull(cases.findById("historic").orElseThrow().getCurrencyOperationId());assertEquals(3,cases.count());assertEquals(2,childBalance("child"));
    }
}
