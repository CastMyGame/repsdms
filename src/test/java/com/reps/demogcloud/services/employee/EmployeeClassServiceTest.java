package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.employee.*;
import com.reps.demogcloud.utils.DtoUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeClassServiceTest {
    @Mock EmployeeRepository employees;
    @Mock MongoTemplate mongo;
    @InjectMocks EmployeeClassService service;
    Employee teacher;
    Employee.ClassRoster roster(String period, String... students) {
        var c = new Employee.ClassRoster(); c.setClassName("Math"); c.setClassPeriod(period); c.setClassRoster(List.of(students)); return c;
    }
    ClassRequest request(String period, boolean create) {
        var r = new ClassRequest(roster(period, "new@example.test")); r.setExpectedRosterRevision(0L); r.setCreateOnly(create); return r;
    }
    @BeforeEach void setup() {
        teacher = new Employee(); teacher.setEmployeeId("teacher"); teacher.setEmail("teacher@example.test"); teacher.setSchool("Testing");
        teacher.setClasses(new ArrayList<>(List.of(roster("period1", "old@example.test"))));
        lenient().when(employees.findByEmailIgnoreCase(teacher.getEmail())).thenReturn(teacher);
    }
    void successfulWrite() { when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Employee.class))).thenReturn(teacher); }
    @SuppressWarnings("unchecked") List<Employee.ClassRoster> writtenClasses() {
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Employee.class));
        assertEquals(Set.of("classes"), ((org.bson.Document) update.getValue().getUpdateObject().get("$set")).keySet());
        return (List<Employee.ClassRoster>) ((org.bson.Document) update.getValue().getUpdateObject().get("$set")).get("classes");
    }
    @Test void sameNameDifferentPeriodCreatesSeparateClass() {
        successfulWrite(); service.addOrUpdateClassToEmployee(teacher.getEmail(), request("period2", true));
        assertEquals(2, writtenClasses().size()); verify(employees, never()).save(any());
    }
    @Test void editOnlyReplacesRequestedPeriodAndNormalizesDuplicates() {
        teacher.getClasses().add(roster("period2", "second@example.test"));
        var r = request("period1", false); r.getClassToUpdate().setClassRoster(List.of(" NEW@example.test ", "new@example.test"));
        successfulWrite(); service.addOrUpdateClassToEmployee(teacher.getEmail(), r);
        var saved = writtenClasses(); assertEquals(List.of("new@example.test"), saved.get(0).getClassRoster());
        assertEquals(List.of("second@example.test"), saved.get(1).getClassRoster());
    }
    @Test void removalKeepsOtherPeriodAndStudentAccounts() {
        teacher.getClasses().add(roster("period2")); successfulWrite(); service.removeClassFromEmployee(teacher.getEmail(), request("period1", false));
        assertEquals("period2", writtenClasses().get(0).getClassPeriod()); verifyNoMoreInteractions(mongo);
    }
    @Test void emptyClassIsSupported() {
        var r = request("period1", false); r.getClassToUpdate().setClassRoster(List.of()); successfulWrite();
        service.addOrUpdateClassToEmployee(teacher.getEmail(), r); assertTrue(writtenClasses().get(0).getClassRoster().isEmpty());
    }
    @Test void staleOrMissingRevisionReturns409WithoutWriting() {
        var r = request("period1", false); r.setExpectedRosterRevision(null);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.addOrUpdateClassToEmployee(teacher.getEmail(), r)).getStatusCode().value());
        r.setExpectedRosterRevision(2L); assertThrows(ResponseStatusException.class, () -> service.addOrUpdateClassToEmployee(teacher.getEmail(), r)); verifyNoInteractions(mongo);
    }
    @Test void duplicateLegacyIdentityIsRejectedRatherThanMerged() {
        teacher.getClasses().add(roster(" PERIOD1 "));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.addOrUpdateClassToEmployee(teacher.getEmail(), request("period1", false))).getStatusCode().value());
        verifyNoInteractions(mongo);
    }
    @Test void duplicateCreateDoesNotOverwriteRoster() {
        assertThrows(ResponseStatusException.class, () -> service.addOrUpdateClassToEmployee(teacher.getEmail(), request("period1", true))); verifyNoInteractions(mongo);
    }
    @Test void legacyMissingRevisionUsesGuardedZeroAndOnlyRosterFields() {
        successfulWrite(); service.addOrUpdateClassToEmployee(teacher.getEmail(), request("period1", false));
        var query = ArgumentCaptor.forClass(Query.class); verify(mongo).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(Employee.class));
        String guard = query.getValue().getQueryObject().toJson(); assertTrue(guard.contains("$exists")); assertTrue(guard.contains("rosterRevision")); assertTrue(guard.contains("Testing"));
    }
    @Test void simultaneousStaleSnapshotsHaveExactlyOneWinner() throws Exception {
        var barrier = new CyclicBarrier(2); var writes = new AtomicInteger();
        when(employees.findByEmailIgnoreCase(teacher.getEmail())).thenAnswer(i -> { barrier.await(5, TimeUnit.SECONDS); return teacher; });
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Employee.class)))
            .thenAnswer(i -> writes.getAndIncrement() == 0 ? teacher : null);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Integer> edit = () -> { try { service.addOrUpdateClassToEmployee(teacher.getEmail(), request("period1", false)); return 200; } catch(ResponseStatusException e) { return e.getStatusCode().value(); } };
        try { var a = pool.submit(edit); var b = pool.submit(edit); assertEquals(Set.of(200,409), Set.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))); }
        finally { pool.shutdownNow(); }
        verify(employees, never()).save(any());
    }
    @Test void staleDashboardReadCannotOverwriteConcurrentRosterWrite() {
        successfulWrite(); service.addOrUpdateClassToEmployee(teacher.getEmail(), request("period1", false));
        new DtoUtils(employees).updateWeeklyPunishmentsForTeacherClasses(teacher, List.of());
        verify(employees, never()).save(any()); verify(mongo,times(1)).findAndModify(any(),any(),any(FindAndModifyOptions.class),eq(Employee.class));
    }
}
