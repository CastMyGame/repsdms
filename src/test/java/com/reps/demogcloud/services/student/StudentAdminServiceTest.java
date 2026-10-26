package com.reps.demogcloud.services.student;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.utils.StudentUtils;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
class StudentAdminServiceTest {
    StudentRepository records;MongoTemplate mongo;WorkflowAccessService access;UserContextService users;UserAdministrationService administration;
    StudentAdminService service;Student child;
    @BeforeEach void setup(){
        records=mock(StudentRepository.class);mongo=mock(MongoTemplate.class);access=mock(WorkflowAccessService.class);users=mock(UserContextService.class);administration=mock(UserAdministrationService.class);
        service=new StudentAdminService(mock(SchoolRepository.class),records,mock(StudentUtils.class),access,users,mongo,administration);
        child=new Student();child.setStudentIdNumber("child");child.setStudentEmail("child@example.test");child.setSchool("Testing");child.setCurrency(50);child.setPoints(7);
        when(access.sameSchool("Testing")).thenReturn(true);
        when(records.findById("child")).thenReturn(Optional.of(child));when(records.findByStudentEmailIgnoreCase(child.getStudentEmail())).thenReturn(child);
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Student.class))).thenReturn(child);
    }
    Student input(){var s=new Student();s.setStudentIdNumber("child");s.setFirstName("Updated");return s;}
    @Test void legacyRevisionZeroUsesTargetedProfileUpdateWithoutTouchingProtectedFields(){
        service.updateStudents(List.of(input()));var query=org.mockito.ArgumentCaptor.forClass(Query.class);var update=org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(),update.capture(),any(FindAndModifyOptions.class),eq(Student.class));
        assertTrue(query.getValue().getQueryObject().toJson().contains("$exists"));
        assertEquals("Updated",((org.bson.Document)update.getValue().getUpdateObject().get("$set")).get("firstName"));
        assertFalse(update.getValue().getUpdateObject().toJson().contains("currency"));assertFalse(update.getValue().getUpdateObject().toJson().contains("spotters"));
        verify(records,never()).save(any());
    }
    @Test void staleRevisionAndConcurrentProfileChangesReturnConflict(){
        child.setProfileRevision(1);assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateStudents(List.of(input()))).getStatusCode().value());
        child.setProfileRevision(0);when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Student.class))).thenReturn(null);
        assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateStudents(List.of(input()))).getStatusCode().value());
    }
    @Test void protectedPointHistoryAndSubscriptionsCannotBeChangedThroughProfiles(){
        var s=input();s.setPoints(100);assertThrows(IllegalArgumentException.class,()->service.updateStudents(List.of(s)));
        s.setPoints(null);s.setSpotters(List.of("attacker@example.test"));assertThrows(IllegalArgumentException.class,()->service.updateStudents(List.of(s)));verifyNoInteractions(mongo);
    }
    @Test void guidanceAssignmentRequiresAdministratorAndVerifiedSchoolRole(){
        var s=input();s.setGuidanceEmail("guidance@example.test");service.updateStudents(List.of(s));
        verify(access).requireAdmin();verify(administration).studentStaffReference("guidance@example.test","Testing","GUIDANCE");
    }
    @Test void notesAppendAuthenticatedAuthorWithoutReplacingHistory(){
        when(users.getCurrentStaffEmail()).thenReturn("staff@example.test");var event=new ThreadEvent();event.setEvent("Note");event.setContent("Synthetic");event.setCreatedBy("forged");
        service.updateStudentNotes("child",event);var update=org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class),update.capture(),any(FindAndModifyOptions.class),eq(Student.class));
        var saved=(ThreadEvent)((org.bson.Document)update.getValue().getUpdateObject().get("$push")).get("notesArray");assertEquals("staff@example.test",saved.getCreatedBy());verify(records,never()).save(any());
    }
    @Test void detentionTimeUsesCompareAndSetAndRejectsInvalidAmounts(){
        child.setTimeBank(new Student.TimeBank(1,55));service.addTimeToStudent(child.getStudentEmail(),0,10);
        var query=org.mockito.ArgumentCaptor.forClass(Query.class);var update=org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(),update.capture(),any(FindAndModifyOptions.class),eq(Student.class));
        assertEquals(child.getTimeBank(),query.getValue().getQueryObject().get("timeBank"));
        var bank=(Student.TimeBank)((org.bson.Document)update.getValue().getUpdateObject().get("$set")).get("timeBank");assertEquals(2,bank.getHours());assertEquals(5,bank.getMinutes());
        assertThrows(IllegalArgumentException.class,()->service.addTimeToStudent(child.getStudentEmail(),0,0));assertThrows(IllegalArgumentException.class,()->service.addTimeToStudent(child.getStudentEmail(),-1,0));
    }
}
