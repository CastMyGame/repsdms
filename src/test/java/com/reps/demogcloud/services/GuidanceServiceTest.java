package com.reps.demogcloud.services;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.guidance.*;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
class GuidanceServiceTest {
    StudentRepository students;GuidanceRepository records;WorkflowAccessService access;UserContextService users;EmailService email;RequiredNotificationService notifications;GuidanceService service;Student child;GuidanceReferral record;
    @BeforeEach void setup(){
        students=mock(StudentRepository.class);records=mock(GuidanceRepository.class);access=mock(WorkflowAccessService.class);users=mock(UserContextService.class);email=mock(EmailService.class);notifications=mock(RequiredNotificationService.class);
        service=new GuidanceService(notifications,students,mock(SchoolRepository.class),email,records,mock(MongoTemplate.class),access,users);
        child=new Student();child.setStudentEmail("child@example.test");child.setSchool("Testing");child.setGuidanceEmail("counselor@example.test");child.setFirstName("<Synthetic>");child.setLastName("Child");
        record=new GuidanceReferral();record.setGuidanceId("case");record.setStudentEmail(child.getStudentEmail());record.setSchool("Testing");record.setTeacherEmail("teacher@example.test");record.setStatus("OPEN");record.setNotesArray(new ArrayList<>());
        when(students.findByStudentEmailIgnoreCase(child.getStudentEmail())).thenReturn(child);when(records.findById("case")).thenReturn(Optional.of(record));when(records.save(any())).thenAnswer(i->i.getArgument(0));
        when(users.getCurrentUsername()).thenReturn("teacher@example.test");when(users.getCurrentStaffEmail()).thenReturn("teacher@example.test");when(access.sameSchool("Testing")).thenReturn(true);
    }
    GuidanceRequest request(){var input=new GuidanceReferral();input.setStudentEmail(child.getStudentEmail());input.setReferralDescription(List.of("Synthetic request"));return new GuidanceRequest(input);}
    ResourceUpdateRequest resources(String url){var request=new ResourceUpdateRequest();var item=new ResourceOption();item.setUrl(url);item.setLabel("<Resource>");request.setResourceOptionList(List.of(item));return request;}
    @Test void linkedCreationBindsAuthorizedSavedCaseAndTrustedAuthor(){
        var punishment=new Punishment();punishment.setPunishmentId("punishment");punishment.setStudentEmail(child.getStudentEmail());punishment.setSchool("Testing");punishment.setInfractionName("Synthetic");when(access.requireCase("punishment")).thenReturn(punishment);
        var form=new PunishmentFormRequest();form.setStudentEmail(child.getStudentEmail());form.setGuidanceDescription("Help requested");form.setTeacherEmail("forged@example.test");
        service.LinkAssignmentToGuidance(child,form,punishment);
        var saved=org.mockito.ArgumentCaptor.forClass(GuidanceReferral.class);verify(records).save(saved.capture());assertEquals("teacher@example.test",saved.getValue().getTeacherEmail());assertEquals("punishment",saved.getValue().getLinkToPunishment());assertEquals(List.of("Help requested"),saved.getValue().getReferralDescription());
    }
    @Test void creationRejectsUnboundOrCrossStudentCaseWithoutSave(){
        var input=request();input.getGuidance().setLinkToPunishment("other");var other=new Punishment();other.setStudentEmail("other@example.test");other.setSchool("Testing");when(access.requireCase("other")).thenReturn(other);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.createNewGuidanceFormSimple(input));verify(records,never()).save(any());
    }
    @Test void malformedCreationAndEmptyDescriptionAreRejected(){assertThrows(IllegalArgumentException.class,()->service.createNewGuidanceFormSimple(null));var input=request();input.getGuidance().setReferralDescription(List.of(" "));assertThrows(IllegalArgumentException.class,()->service.createNewGuidanceFormSimple(input));verify(records,never()).save(any());}
    @Test void spanishResourcesEscapeContentAndSaveBeforeLegacyTransport() throws Exception {
        child.setPreferredLanguage("es");service.sendResourcesAndMakeNotes("case",resources("https://example.test/resource?a=1&b=2"));
        var message=org.mockito.ArgumentCaptor.forClass(String.class);var order=inOrder(records,email);order.verify(records).save(record);order.verify(email).sendEmailGeneric(anyList(),eq(child.getStudentEmail()),anyString(),message.capture(),eq("es"));
        assertTrue(message.getValue().contains("Hola"));assertTrue(message.getValue().contains("&lt;Synthetic&gt;"));assertTrue(message.getValue().contains("&lt;Resource&gt;"));assertFalse(message.getValue().contains("<Resource>"));
    }
    @Test void invalidResourceSchemeCannotSaveOrSend(){assertThrows(IllegalArgumentException.class,()->service.sendResourcesAndMakeNotes("case",resources("javascript:alert(1)")));verify(records,never()).save(any());verifyNoInteractions(email);}
    @Test void concurrentSaveConflictNeverSendsLegacyEmail(){when(records.save(any())).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("synthetic conflict"));assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->service.sendResourcesAndMakeNotes("case",resources("https://example.test")));verifyNoInteractions(email);}
    @Test void managedResourcesKeepDurableIntentAndNoLegacySend() throws Exception {when(notifications.isEnabled()).thenReturn(true);when(notifications.actionId()).thenReturn("synthetic-action");service.sendResourcesAndMakeNotes("case",resources("https://example.test"));verify(notifications).prepare(eq(record),eq("RESOURCES"),eq("synthetic-action"),anyString());verify(records).save(record);verifyNoInteractions(email);}
    @Test void authorizationAlwaysPrecedesDuplicateResourceReceiptCheck(){when(notifications.isEnabled()).thenReturn(true);doThrow(new org.springframework.security.access.AccessDeniedException("revoked")).when(users).requireGuidanceAccess(record,true);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.sendResourcesAndMakeNotes("case",resources("https://example.test")));verify(notifications,never()).hasAction(any(),anyString());verifyNoInteractions(email);}
    @Test void notesPreservePriorHistoryAndIgnoreSuppliedAuthor(){var prior=new ThreadEvent();prior.setContent("Prior");record.getNotesArray().add(prior);var event=new ThreadEvent();event.setEvent("Notes");event.setContent("New");event.setCreatedBy("forged");service.updateGuidance("case",event);assertSame(prior,record.getNotesArray().get(0));assertEquals("teacher@example.test",record.getNotesArray().get(1).getCreatedBy());}
    @Test void archiveRetainsDescriptionsAndNotes(){record.setReferralDescription(List.of("Original"));service.deleteGuidanceReferral("case");assertTrue(record.isArchived());assertEquals(List.of("Original"),record.getReferralDescription());verify(records).save(record);verify(records,never()).delete(any());}
    @Test void relativeResourceUrlsAreRejectedWithoutTransportOrPersistence(){assertThrows(IllegalArgumentException.class,()->service.sendResourcesAndMakeNotes("case",resources("/relative")));verify(records,never()).save(any());verifyNoInteractions(email);}

}
