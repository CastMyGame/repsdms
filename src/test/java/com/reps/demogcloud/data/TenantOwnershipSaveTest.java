package com.reps.demogcloud.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantOwnershipSaveTest {
    MongoTemplate mongo;
    MappingMongoConverter converter;
    Student student;

    @BeforeEach void setup() throws Exception {
        var context=new MongoMappingContext();
        var conversions=MongoCustomConversions.create(adapter->{});
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());context.afterPropertiesSet();
        converter=new MappingMongoConverter(NoOpDbRefResolver.INSTANCE,context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        mongo=mock(MongoTemplate.class);
        when(mongo.getConverter()).thenReturn(converter);
        when(mongo.getCollectionName(Student.class)).thenReturn("students");
        when(mongo.exists(any(Query.class),eq(com.reps.demogcloud.models.school.School.class))).thenReturn(true);
        student=new Student();student.setStudentIdNumber("child");student.setSchool("Same School");student.setCurrency(999);
    }

    void stored(Document value) {
        when(mongo.findOne(any(Query.class),eq(Document.class),eq("students"))).thenReturn(value);
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Student.class))).thenReturn(student);
    }

    @Test void staleProfilePreservesBoundOwnerAndCurrencyUsingAtomicPredicate() {
        stored(new Document("_id","child").append("tenantSchoolId","school-A"));
        BalanceSafeSave.save(mongo,student,Student.class);
        verify(mongo).findAndModify(argThat(q->"school-A".equals(q.getQueryObject().get("tenantSchoolId"))),
                argThat(u->!u.modifies("tenantSchoolId") && !u.modifies("currency")),any(),eq(Student.class));
        verify(mongo,never()).save(any(Student.class));
    }

    @Test void matchingOwnerNeverBecomesASetOrUnset() {
        student.setTenantSchoolId("school-A");stored(new Document("tenantSchoolId","school-A"));
        BalanceSafeSave.save(mongo,student,Student.class);
        verify(mongo).findAndModify(any(),argThat(u->!u.modifies("tenantSchoolId")),any(),eq(Student.class));
    }

    @Test void legacyProfileStaysUnboundWithAbsentFieldPredicate() {
        stored(new Document("_id","child"));BalanceSafeSave.save(mongo,student,Student.class);
        verify(mongo).findAndModify(argThat(q->new Document("$exists",false).equals(q.getQueryObject().get("tenantSchoolId"))),
                argThat(u->!u.modifies("tenantSchoolId")),any(),eq(Student.class));
    }

    @Test void conflictingOwnerCannotBeChangedEvenForSameDisplayName() {
        student.setTenantSchoolId("school-B");stored(new Document("tenantSchoolId","school-A"));
        assertThrows(OptimisticLockingFailureException.class,()->BalanceSafeSave.save(mongo,student,Student.class));
        verify(mongo,never()).findAndModify(any(),any(Update.class),any(),eq(Student.class));
    }

    @Test void ordinarySaveCannotBindAnUnmappedRecord() {
        student.setTenantSchoolId("school-A");stored(new Document("_id","child"));
        assertThrows(OptimisticLockingFailureException.class,()->BalanceSafeSave.save(mongo,student,Student.class));
        verify(mongo,never()).findAndModify(any(),any(Update.class),any(),eq(Student.class));
    }

    @Test void nonexistentCanonicalSchoolFailsClosedWithoutNameResolution() {
        stored(new Document("tenantSchoolId","orphaned-school"));
        when(mongo.exists(any(Query.class),eq(com.reps.demogcloud.models.school.School.class))).thenReturn(false);
        assertThrows(OptimisticLockingFailureException.class,()->BalanceSafeSave.save(mongo,student,Student.class));
        verify(mongo).exists(argThat(q->new Document("_id","orphaned-school").equals(q.getQueryObject())),eq(com.reps.demogcloud.models.school.School.class));
        verify(mongo,never()).findAndModify(any(),any(Update.class),any(),eq(Student.class));
    }

    @Test void invalidPersistedOwnershipFailsClosedRatherThanClearingIt() {
        for(Object owner:new Object[]{"", " school-A", "school-A ",42, new Document("$oid","synthetic")}) {
            assertThrows(OptimisticLockingFailureException.class,()->TenantOwnershipWriteGuard.unchangedOwner(
                    new Document("tenantSchoolId",owner),new Document()));
        }
        assertThrows(OptimisticLockingFailureException.class,()->TenantOwnershipWriteGuard.unchangedOwner(
                new Document("tenantSchoolId",null),new Document()));
    }

    @Test void ownershipRaceOrDeletionIsConflictAndNeverReinserts() {
        stored(new Document("_id","child"));
        when(mongo.findAndModify(any(),any(Update.class),any(),eq(Student.class))).thenReturn(null);
        assertThrows(OptimisticLockingFailureException.class,()->BalanceSafeSave.save(mongo,student,Student.class));
        verify(mongo,never()).insert(any(Document.class),anyString());
    }

    @Test void newProfilesRemainLegacyUnboundAndStartWithZeroCurrency() {
        BalanceSafeSave.save(mongo,student,Student.class);
        verify(mongo).insert(argThat((Document d)->!d.containsKey("tenantSchoolId") && Integer.valueOf(0).equals(d.get("currency"))),eq("students"));
    }

    @Test void newProfileCannotCarryUnreviewedOwnership() {
        student.setTenantSchoolId("school-A");
        assertThrows(OptimisticLockingFailureException.class,()->BalanceSafeSave.save(mongo,student,Student.class));
        verify(mongo,never()).insert(any(Document.class),anyString());
    }

    @Test void mongoFieldsRoundTripButHttpPayloadsCannotReadOrWriteThem() throws Exception {
        var json=new ObjectMapper();
        var s=json.readValue("{\"studentIdNumber\":\"child\",\"school\":\"Same School\",\"tenantSchoolId\":\"school-B\"}",Student.class);
        var e=json.readValue("{\"employeeId\":\"staff\",\"tenantSchoolId\":\"school-B\"}",Employee.class);
        assertNull(s.getTenantSchoolId());assertNull(e.getTenantSchoolId());
        s.setTenantSchoolId("school-A");e.setTenantSchoolId("school-A");
        assertFalse(json.writeValueAsString(s).contains("tenantSchoolId"));
        assertFalse(json.writeValueAsString(e).contains("tenantSchoolId"));
        Document sd=new Document(),ed=new Document();converter.write(s,sd);converter.write(e,ed);
        assertEquals("school-A",converter.read(Student.class,sd).getTenantSchoolId());
        assertEquals("school-A",converter.read(Employee.class,ed).getTenantSchoolId());
    }

    @Test void employeeFragmentUsesSameOwnershipAndBalanceProtection() {
        when(mongo.getCollectionName(Employee.class)).thenReturn("employee");
        when(mongo.findOne(any(Query.class),eq(Document.class),eq("employee"))).thenReturn(new Document("tenantSchoolId","school-A"));
        var e=new Employee();e.setEmployeeId("staff");e.setCurrency(999);
        when(mongo.findAndModify(any(),any(Update.class),any(),eq(Employee.class))).thenReturn(e);
        new EmployeeBalanceSafeRepositoryImpl(mongo).save(e);
        verify(mongo).findAndModify(argThat(q->"school-A".equals(q.getQueryObject().get("tenantSchoolId"))),
                argThat(u->!u.modifies("currency") && !u.modifies("tenantSchoolId")),any(),eq(Employee.class));
    }
}
