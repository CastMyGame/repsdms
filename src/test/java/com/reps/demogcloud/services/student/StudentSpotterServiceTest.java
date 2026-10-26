package com.reps.demogcloud.services.student;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.*;
import com.reps.demogcloud.services.UserContextService;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class StudentSpotterServiceTest {
    StudentRepository records; UserContextService users; MongoTemplate mongo; StudentSpotterService service; Student child;
    @BeforeEach void setup() {
        records=mock(StudentRepository.class);users=mock(UserContextService.class);mongo=mock(MongoTemplate.class);
        service=new StudentSpotterService(records,users,mongo);child=new Student();child.setStudentIdNumber("child");child.setStudentEmail("child@example.test");child.setSchool("Testing");
        when(users.getCurrentStaffEmail()).thenReturn("teacher@example.test");when(users.hasIndependentStudentAccess(anyString(),eq(child))).thenReturn(true);
        when(records.findByStudentEmailIgnoreCase(child.getStudentEmail())).thenReturn(child);
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Student.class))).thenReturn(child);
    }
    UpdateSpottersRequest request(String staff) {var request=new UpdateSpottersRequest();request.setSpotters(List.of(staff));request.setStudentEmail(List.of(child.getStudentEmail()));return request;}
    @Test void retriesUseSetAdditionAndNeverWholeDocumentSave() {
        for(int i=0;i<2;i++)service.addAsSpotter(request("teacher@example.test"));
        var update=org.mockito.ArgumentCaptor.forClass(Update.class);verify(mongo,times(2)).findAndModify(any(Query.class),update.capture(),any(FindAndModifyOptions.class),eq(Student.class));
        update.getAllValues().forEach(u -> {assertTrue(u.getUpdateObject().containsKey("$addToSet"));assertFalse(u.getUpdateObject().containsKey("$set"));});verify(records,never()).save(any());
    }
    @Test void adminMayManageAnotherEligibleStaffMemberAndRemoveStaleGrant() {
        when(users.isCurrentStaffAdministrator()).thenReturn(true);service.addAsSpotter(request("other@example.test"));
        when(users.hasIndependentStudentAccess(anyString(),eq(child))).thenReturn(false);service.deleteSpotters(request("other@example.test"));
        verify(mongo,times(2)).findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Student.class));
    }
    @Test void forgedStaffAndRevokedUnderlyingRelationshipCannotWrite() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.addAsSpotter(request("other@example.test")));
        when(users.hasIndependentStudentAccess(anyString(),eq(child))).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.addAsSpotter(request("teacher@example.test")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.deleteSpotters(request("teacher@example.test")));
        verifyNoInteractions(mongo);
    }
    @Test void concurrentArchiveIsReportedAsConflictAndRetryUsesNoStaleArray() {
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Student.class))).thenReturn(null);
        assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.addAsSpotter(request("teacher@example.test"))).getStatusCode().value());
        verify(records,never()).save(any());
    }
}
