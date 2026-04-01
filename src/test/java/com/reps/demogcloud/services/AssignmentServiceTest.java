package com.reps.demogcloud.services;

import com.reps.demogcloud.data.AssignmentRepository;
import com.reps.demogcloud.data.AssignmentTemplateBindingRepository;
import com.reps.demogcloud.data.AssignmentTemplateRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.assignments.AssignmentTemplateBinding;
import com.reps.demogcloud.models.dto.AssignmentTemplateSummaryDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentTemplateRepository assignmentTemplateRepository;

    @Mock
    private AssignmentTemplateBindingRepository bindingRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PunishRepository punishRepository;

    @InjectMocks
    private AssignmentService service;

    @Test
    void getAllAssignments_shouldReturnAll() {
        List<Assignment> expected = List.of(new Assignment(), new Assignment());
        when(assignmentRepository.findAll()).thenReturn(expected);

        List<Assignment> result = service.getAllAssignments();

        assertSame(expected, result);
        verify(assignmentRepository).findAll();
    }

    @Test
    void createNewAssignment_shouldSave() {
        Assignment assignment = new Assignment();
        when(assignmentRepository.save(assignment)).thenReturn(assignment);

        Assignment result = service.createNewAssignment(assignment);

        assertSame(assignment, result);
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void deleteAssignment_shouldDeleteByInfractionName() {
        Assignment deleted = new Assignment();
        when(assignmentRepository.deleteByInfractionName("Tardy")).thenReturn(deleted);

        Assignment result = service.deleteAssignment("Tardy");

        assertSame(deleted, result);
        verify(assignmentRepository).deleteByInfractionName("Tardy");
    }

    @Test
    void updateNewAssignment_shouldUpdateFieldsAndSave() throws Exception {
        Assignment existing = new Assignment();
        existing.setInfractionName("Old");
        existing.setLevel(1);

        Assignment updated = new Assignment();
        updated.setInfractionName("New");
        updated.setLevel(3);
        updated.setQuestions(List.of());

        when(assignmentRepository.findById("1")).thenReturn(Optional.of(existing));
        when(assignmentRepository.save(existing)).thenReturn(existing);

        Assignment result = service.updateNewAssignment(updated, "1");

        assertEquals("New", result.getInfractionName());
        assertEquals(3, result.getLevel());
        assertEquals(List.of(), result.getQuestions());
        verify(assignmentRepository).findById("1");
        verify(assignmentRepository).save(existing);
    }

    @Test
    void updateNewAssignment_shouldThrowWhenNotFound() {
        Assignment updated = new Assignment();
        when(assignmentRepository.findById("1")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () -> service.updateNewAssignment(updated, "1"));

        assertEquals("Assignment with ID 1 not found", ex.getMessage());
        verify(assignmentRepository).findById("1");
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void migrateLegacyAssignmentsToTemplates_shouldMigrateAll() {
        Assignment a1 = new Assignment();
        a1.setAssignmentId("a1");
        a1.setInfractionName("Tardy");
        a1.setLevel(1);
        a1.setQuestions(List.of());

        Assignment a2 = new Assignment();
        a2.setAssignmentId("a2");
        a2.setInfractionName("Disruption");
        a2.setLevel(2);
        a2.setQuestions(List.of());

        when(assignmentRepository.findAll()).thenReturn(List.of(a1, a2));
        when(assignmentTemplateRepository.save(any(AssignmentTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int result = service.migrateLegacyAssignmentsToTemplates();

        assertEquals(2, result);
        verify(assignmentRepository).findAll();
        verify(assignmentTemplateRepository, times(2)).save(any(AssignmentTemplate.class));
    }

    @Test
    void getAllTemplates_shouldReturnAll() {
        List<AssignmentTemplate> expected = List.of(new AssignmentTemplate(), new AssignmentTemplate());
        when(assignmentTemplateRepository.findAll()).thenReturn(expected);

        List<AssignmentTemplate> result = service.getAllTemplates();

        assertSame(expected, result);
        verify(assignmentTemplateRepository).findAll();
    }

    @Test
    void getTemplateById_shouldReturnTemplate() throws Exception {
        AssignmentTemplate template = new AssignmentTemplate();
        when(assignmentTemplateRepository.findById("t1")).thenReturn(Optional.of(template));

        AssignmentTemplate result = service.getTemplateById("t1");

        assertSame(template, result);
        verify(assignmentTemplateRepository).findById("t1");
    }

    @Test
    void getTemplateById_shouldThrowWhenMissing() {
        when(assignmentTemplateRepository.findById("t1")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () -> service.getTemplateById("t1"));

        assertEquals("AssignmentTemplate with ID t1 not found", ex.getMessage());
    }

    @Test
    void getTemplatesByInfractionAndLevel_shouldReturnMatches() {
        List<AssignmentTemplate> expected = List.of(new AssignmentTemplate());
        when(assignmentTemplateRepository.findByInfractionNameAndLevel("Tardy", 1)).thenReturn(expected);

        List<AssignmentTemplate> result = service.getTemplatesByInfractionAndLevel("Tardy", 1);

        assertSame(expected, result);
        verify(assignmentTemplateRepository).findByInfractionNameAndLevel("Tardy", 1);
    }

    @Test
    void createAssignmentTemplate_shouldSetSystemDefaults() {
        AssignmentTemplate template = new AssignmentTemplate();

        when(assignmentTemplateRepository.save(any(AssignmentTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentTemplate result = service.createAssignmentTemplate(template);

        assertNull(result.getId());
        assertTrue(result.isCreatedBySystem());
        assertEquals(AssignmentTemplate.Scope.SYSTEM_DEFAULT, result.getScope());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void createAssignmentTemplate_shouldSetTeacherDefaults() {
        AssignmentTemplate template = new AssignmentTemplate();
        template.setId("old-id");
        template.setCreatedByUserId("teacher@test.com");

        when(assignmentTemplateRepository.save(any(AssignmentTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentTemplate result = service.createAssignmentTemplate(template);

        assertNull(result.getId());
        assertFalse(result.isCreatedBySystem());
        assertEquals(AssignmentTemplate.Scope.SYSTEM_DEFAULT, result.getScope());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void createAssignmentTemplate_shouldPreserveProvidedScope() {
        AssignmentTemplate template = new AssignmentTemplate();
        template.setCreatedByUserId("teacher@test.com");
        template.setScope(AssignmentTemplate.Scope.SCHOOL_DEFAULT);

        when(assignmentTemplateRepository.save(any(AssignmentTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentTemplate result = service.createAssignmentTemplate(template);

        assertEquals(AssignmentTemplate.Scope.SCHOOL_DEFAULT, result.getScope());
        assertFalse(result.isCreatedBySystem());
    }

    @Test
    void updateAssignmentTemplate_shouldUpdateAllowedFields() throws Exception {
        AssignmentTemplate existing = new AssignmentTemplate();
        existing.setInfractionName("Old");
        existing.setLevel(1);

        AssignmentTemplate updated = new AssignmentTemplate();
        updated.setInfractionName("New");
        updated.setLevel(2);
        updated.setQuestions(List.of());

        when(assignmentTemplateRepository.findById("t1")).thenReturn(Optional.of(existing));
        when(assignmentTemplateRepository.save(existing)).thenReturn(existing);

        AssignmentTemplate result = service.updateAssignmentTemplate("t1", updated);

        assertEquals("New", result.getInfractionName());
        assertEquals(2, result.getLevel());
        assertEquals(List.of(), result.getQuestions());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void updateAssignmentTemplate_shouldThrowWhenMissing() {
        AssignmentTemplate updated = new AssignmentTemplate();
        when(assignmentTemplateRepository.findById("t1")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () -> service.updateAssignmentTemplate("t1", updated));

        assertEquals("AssignmentTemplate with ID t1 not found", ex.getMessage());
    }

    @Test
    void deleteAssignmentTemplate_shouldDeleteWhenFound() throws Exception {
        AssignmentTemplate existing = new AssignmentTemplate();
        when(assignmentTemplateRepository.findById("t1")).thenReturn(Optional.of(existing));

        service.deleteAssignmentTemplate("t1");

        verify(assignmentTemplateRepository).findById("t1");
        verify(assignmentTemplateRepository).delete(existing);
    }

    @Test
    void deleteAssignmentTemplate_shouldThrowWhenMissing() {
        when(assignmentTemplateRepository.findById("t1")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () -> service.deleteAssignmentTemplate("t1"));

        assertEquals("AssignmentTemplate with ID t1 not found", ex.getMessage());
        verify(assignmentTemplateRepository, never()).delete(any());
    }

    @Test
    void resolveTemplateForInfraction_shouldUseTeacherBindingFirst() throws Exception {
        AssignmentTemplateBinding binding = new AssignmentTemplateBinding();
        binding.setAssignmentTemplateId("template-1");

        AssignmentTemplate template = new AssignmentTemplate();

        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.of(binding));

        when(assignmentTemplateRepository.findById("template-1")).thenReturn(Optional.of(template));

        AssignmentTemplate result = service.resolveTemplateForInfraction(
                "teacher@test.com", "school1", "Tardy", 1
        );

        assertSame(template, result);
        verify(bindingRepository).findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        );
        verify(assignmentTemplateRepository).findById("template-1");
    }

    @Test
    void resolveTemplateForInfraction_shouldThrowWhenTeacherBindingTemplateMissing() {
        AssignmentTemplateBinding binding = new AssignmentTemplateBinding();
        binding.setAssignmentTemplateId("missing-template");

        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.of(binding));

        when(assignmentTemplateRepository.findById("missing-template")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () ->
                service.resolveTemplateForInfraction("teacher@test.com", "school1", "Tardy", 1));

        assertEquals("Binding exists but template missing-template not found", ex.getMessage());
    }

    @Test
    void resolveTemplateForInfraction_shouldUseSchoolBindingFallback() throws Exception {
        AssignmentTemplateBinding binding = new AssignmentTemplateBinding();
        binding.setAssignmentTemplateId("template-2");

        AssignmentTemplate template = new AssignmentTemplate();

        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());

        when(bindingRepository.findFirstByTeacherEmailIsNullAndSchoolIdAndInfractionNameAndLevelAndActiveTrue(
                "school1", "Tardy", 1
        )).thenReturn(Optional.of(binding));

        when(assignmentTemplateRepository.findById("template-2")).thenReturn(Optional.of(template));

        AssignmentTemplate result = service.resolveTemplateForInfraction(
                "teacher@test.com", "school1", "Tardy", 1
        );

        assertSame(template, result);
    }

    @Test
    void resolveTemplateForInfraction_shouldThrowWhenSchoolBindingTemplateMissing() {
        AssignmentTemplateBinding binding = new AssignmentTemplateBinding();
        binding.setAssignmentTemplateId("missing-template");

        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());

        when(bindingRepository.findFirstByTeacherEmailIsNullAndSchoolIdAndInfractionNameAndLevelAndActiveTrue(
                "school1", "Tardy", 1
        )).thenReturn(Optional.of(binding));

        when(assignmentTemplateRepository.findById("missing-template")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () ->
                service.resolveTemplateForInfraction("teacher@test.com", "school1", "Tardy", 1));

        assertEquals("School binding exists but template missing-template not found", ex.getMessage());
    }

    @Test
    void resolveTemplateForInfraction_shouldUseSystemFallback() throws Exception {
        AssignmentTemplate systemTemplate = new AssignmentTemplate();

        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());

        when(bindingRepository.findFirstByTeacherEmailIsNullAndSchoolIdAndInfractionNameAndLevelAndActiveTrue(
                "school1", "Tardy", 1
        )).thenReturn(Optional.empty());

        when(assignmentTemplateRepository.findByInfractionNameAndLevelAndScopeAndActiveTrue(
                "Tardy", 1, AssignmentTemplate.Scope.SYSTEM_DEFAULT
        )).thenReturn(List.of(systemTemplate));

        AssignmentTemplate result = service.resolveTemplateForInfraction(
                "teacher@test.com", "school1", "Tardy", 1
        );

        assertSame(systemTemplate, result);
    }

    @Test
    void resolveTemplateForInfraction_shouldThrowWhenNothingFound() {
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());

        when(bindingRepository.findFirstByTeacherEmailIsNullAndSchoolIdAndInfractionNameAndLevelAndActiveTrue(
                "school1", "Tardy", 1
        )).thenReturn(Optional.empty());

        when(assignmentTemplateRepository.findByInfractionNameAndLevelAndScopeAndActiveTrue(
                "Tardy", 1, AssignmentTemplate.Scope.SYSTEM_DEFAULT
        )).thenReturn(List.of());

        Exception ex = assertThrows(Exception.class, () ->
                service.resolveTemplateForInfraction("teacher@test.com", "school1", "Tardy", 1));

        assertEquals("No AssignmentTemplate found for infraction=Tardy, level=1", ex.getMessage());
    }

    @Test
    void resolveTemplateForPunishment_shouldUseTeacherBindingFirst() {
        Punishment punishment = new Punishment();
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionName("Tardy");
        punishment.setInfractionLevel("1");
        punishment.setStudentEmail("student@test.com");

        AssignmentTemplateBinding binding = new AssignmentTemplateBinding();
        binding.setAssignmentTemplateId("template-1");

        AssignmentTemplate template = new AssignmentTemplate();

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrueOrderByCreatedAtDesc(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.of(binding));
        when(assignmentTemplateRepository.findById("template-1")).thenReturn(Optional.of(template));

        AssignmentTemplate result = service.resolveTemplateForPunishment(punishment);

        assertSame(template, result);
    }

    @Test
    void resolveTemplateForPunishment_shouldThrowWhenTeacherBindingTemplateMissing() {
        Punishment punishment = new Punishment();
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionName("Tardy");
        punishment.setInfractionLevel("1");
        punishment.setStudentEmail("student@test.com");

        AssignmentTemplateBinding binding = new AssignmentTemplateBinding();
        binding.setAssignmentTemplateId("missing-template");

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrueOrderByCreatedAtDesc(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.of(binding));
        when(assignmentTemplateRepository.findById("missing-template")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolveTemplateForPunishment(punishment));

        assertEquals("Binding exists but template not found: missing-template", ex.getMessage());
    }

    @Test
    void resolveTemplateForPunishment_shouldUseSchoolDefaultFallback() {
        Punishment punishment = new Punishment();
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionName("Tardy");
        punishment.setInfractionLevel("1");
        punishment.setStudentEmail("student@test.com");

        Student student = new Student();
        student.setSchool("school1");

        AssignmentTemplate schoolTemplate = new AssignmentTemplate();

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrueOrderByCreatedAtDesc(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());
        when(assignmentTemplateRepository.findByInfractionNameAndLevelAndScopeAndSchoolIdAndActiveTrue(
                "Tardy", 1, AssignmentTemplate.Scope.SCHOOL_DEFAULT, "school1"
        )).thenReturn(List.of(schoolTemplate));

        AssignmentTemplate result = service.resolveTemplateForPunishment(punishment);

        assertSame(schoolTemplate, result);
    }

    @Test
    void resolveTemplateForPunishment_shouldUseSystemFallback() {
        Punishment punishment = new Punishment();
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionName("Tardy");
        punishment.setInfractionLevel("1");
        punishment.setStudentEmail("student@test.com");

        AssignmentTemplate systemTemplate = new AssignmentTemplate();

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrueOrderByCreatedAtDesc(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());
        when(assignmentTemplateRepository.findByInfractionNameAndLevelAndScopeAndActiveTrue(
                "Tardy", 1, AssignmentTemplate.Scope.SYSTEM_DEFAULT
        )).thenReturn(List.of(systemTemplate));

        AssignmentTemplate result = service.resolveTemplateForPunishment(punishment);

        assertSame(systemTemplate, result);
    }

    @Test
    void resolveTemplateForPunishment_shouldThrowWhenNothingFound() {
        Punishment punishment = new Punishment();
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionName("Tardy");
        punishment.setInfractionLevel("1");
        punishment.setStudentEmail("student@test.com");

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrueOrderByCreatedAtDesc(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());
        when(assignmentTemplateRepository.findByInfractionNameAndLevelAndScopeAndActiveTrue(
                "Tardy", 1, AssignmentTemplate.Scope.SYSTEM_DEFAULT
        )).thenReturn(List.of());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolveTemplateForPunishment(punishment));

        assertEquals("No assignment template found for infraction=Tardy, level=1", ex.getMessage());
    }

    @Test
    void setTeacherDefaultTemplate_shouldDeactivateExistingAndCreateNew() throws Exception {
        AssignmentTemplate template = new AssignmentTemplate();
        template.setId("template-1");

        AssignmentTemplateBinding existing = new AssignmentTemplateBinding();
        existing.setActive(true);

        AssignmentTemplateBinding savedBinding = new AssignmentTemplateBinding();
        savedBinding.setAssignmentTemplateId("template-1");

        when(assignmentTemplateRepository.findById("template-1")).thenReturn(Optional.of(template));
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.of(existing));
        when(bindingRepository.save(any(AssignmentTemplateBinding.class))).thenReturn(savedBinding);

        AssignmentTemplateBinding result = service.setTeacherDefaultTemplate(
                "teacher@test.com", "school1", "Tardy", 1, "template-1"
        );

        assertSame(savedBinding, result);
        assertFalse(existing.isActive());

        ArgumentCaptor<AssignmentTemplateBinding> captor =
                ArgumentCaptor.forClass(AssignmentTemplateBinding.class);
        verify(bindingRepository, times(2)).save(captor.capture());

        List<AssignmentTemplateBinding> savedBindings = captor.getAllValues();
        AssignmentTemplateBinding newBinding = savedBindings.get(1);

        assertEquals("teacher@test.com", newBinding.getTeacherEmail());
        assertEquals("school1", newBinding.getSchoolId());
        assertEquals("Tardy", newBinding.getInfractionName());
        assertEquals(1, newBinding.getLevel());
        assertEquals("template-1", newBinding.getAssignmentTemplateId());
        assertTrue(newBinding.isActive());
        assertNotNull(newBinding.getCreatedAt());
        assertNotNull(newBinding.getUpdatedAt());
    }

    @Test
    void setTeacherDefaultTemplate_shouldThrowWhenTemplateMissing() {
        when(assignmentTemplateRepository.findById("template-1")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () ->
                service.setTeacherDefaultTemplate("teacher@test.com", "school1", "Tardy", 1, "template-1"));

        assertEquals("AssignmentTemplate with ID template-1 not found", ex.getMessage());
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void clearTeacherDefaultTemplate_shouldDeactivateExistingBinding() {
        AssignmentTemplateBinding existing = new AssignmentTemplateBinding();
        existing.setActive(true);

        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.of(existing));

        service.clearTeacherDefaultTemplate("teacher@test.com", "Tardy", 1);

        assertFalse(existing.isActive());
        assertNotNull(existing.getUpdatedAt());
        verify(bindingRepository).save(existing);
    }

    @Test
    void clearTeacherDefaultTemplate_shouldDoNothingWhenNoBinding() {
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());

        service.clearTeacherDefaultTemplate("teacher@test.com", "Tardy", 1);

        verify(bindingRepository, never()).save(any());
    }

    @Test
    void getActiveBindingsForTeacher_shouldReturnBindings() {
        List<AssignmentTemplateBinding> expected = List.of(new AssignmentTemplateBinding());
        when(bindingRepository.findByTeacherEmailAndActiveTrue("teacher@test.com")).thenReturn(expected);

        List<AssignmentTemplateBinding> result = service.getActiveBindingsForTeacher("teacher@test.com");

        assertSame(expected, result);
    }

    @Test
    void getActiveBindingsForTemplate_shouldReturnBindings() {
        List<AssignmentTemplateBinding> expected = List.of(new AssignmentTemplateBinding());
        when(bindingRepository.findByAssignmentTemplateIdAndActiveTrue("template-1")).thenReturn(expected);

        List<AssignmentTemplateBinding> result = service.getActiveBindingsForTemplate("template-1");

        assertSame(expected, result);
    }

    @Test
    void searchTemplates_shouldReturnEmptyWhenNoTemplates() {
        when(assignmentTemplateRepository.findAll()).thenReturn(List.of());

        List<AssignmentTemplateSummaryDTO> result =
                service.searchTemplates(null, null, null, null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void searchTemplates_shouldFilterByInfractionLevelCreatorAndCreatedBySystem() {
        AssignmentTemplate match = new AssignmentTemplate();
        match.setId("1");
        match.setName("Template 1");
        match.setInfractionName("Tardy");
        match.setLevel(1);
        match.setCreatedByUserId("teacher@test.com");
        match.setCreatedBySystem(false);
        match.setQuestions(List.of());

        AssignmentTemplate noMatch = new AssignmentTemplate();
        noMatch.setId("2");
        noMatch.setName("Template 2");
        noMatch.setInfractionName("Disruption");
        noMatch.setLevel(2);
        noMatch.setCreatedByUserId("other@test.com");
        noMatch.setCreatedBySystem(true);
        noMatch.setQuestions(List.of());

        when(assignmentTemplateRepository.findAll()).thenReturn(List.of(match, noMatch));

        List<AssignmentTemplateSummaryDTO> result = service.searchTemplates(
                "Tardy",
                1,
                "teacher@test.com",
                false,
                null
        );

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getId());
    }

    @Test
    void searchTemplates_shouldMatchTextAgainstName() {
        AssignmentTemplate.TemplateQuestion question = new AssignmentTemplate.TemplateQuestion();
        question.setTitle("Something else");

        AssignmentTemplate template = new AssignmentTemplate();
        template.setId("1");
        template.setName("Tardy Reflection");
        template.setQuestions(List.of(question));

        when(assignmentTemplateRepository.findAll()).thenReturn(List.of(template));

        List<AssignmentTemplateSummaryDTO> result =
                service.searchTemplates(null, null, null, null, "reflection");

        assertEquals(1, result.size());
    }

    @Test
    void searchTemplates_shouldMatchTextAgainstQuestionFields() {
        AssignmentTemplate.TemplateQuestion question = new AssignmentTemplate.TemplateQuestion();
        question.setTitle("Why did this happen?");
        question.setPrompt("Explain the classroom behavior");
        question.setPassageBody("This is a reading about expectations.");

        AssignmentTemplate template = new AssignmentTemplate();
        template.setId("1");
        template.setName("Template");
        template.setQuestions(List.of(question));

        when(assignmentTemplateRepository.findAll()).thenReturn(List.of(template));

        assertEquals(1, service.searchTemplates(null, null, null, null, "happen").size());
        assertEquals(1, service.searchTemplates(null, null, null, null, "classroom").size());
        assertEquals(1, service.searchTemplates(null, null, null, null, "expectations").size());
    }

    @Test
    void searchTemplates_shouldNotMatchTextWhenNoQuestions() {
        AssignmentTemplate template = new AssignmentTemplate();
        template.setId("1");
        template.setName("Template");
        template.setQuestions(null);

        when(assignmentTemplateRepository.findAll()).thenReturn(List.of(template));

        List<AssignmentTemplateSummaryDTO> result =
                service.searchTemplates(null, null, null, null, "anything");

        assertTrue(result.isEmpty());
    }

    @Test
    void searchTemplates_shouldBuildPreviewFromPrompt() {
        AssignmentTemplate.TemplateQuestion question = new AssignmentTemplate.TemplateQuestion();
        question.setPrompt("Prompt preview");
        question.setTitle("Title");
        question.setPassageBody("Passage");

        AssignmentTemplate template = new AssignmentTemplate();
        template.setId("1");
        template.setName("Name");
        template.setInfractionName("Tardy");
        template.setLevel(1);
        template.setQuestions(List.of(question));
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());

        when(assignmentTemplateRepository.findAll()).thenReturn(List.of(template));

        List<AssignmentTemplateSummaryDTO> result =
                service.searchTemplates(null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Prompt preview", result.get(0).getFirstQuestionPreview());
    }

    @Test
    void searchTemplates_shouldBuildPreviewFromTitleWhenPromptBlank() {
        AssignmentTemplate.TemplateQuestion question = new AssignmentTemplate.TemplateQuestion();
        question.setPrompt(" ");
        question.setTitle("Title preview");
        question.setPassageBody("Passage");

        AssignmentTemplate template = new AssignmentTemplate();
        template.setId("1");
        template.setName("Name");
        template.setInfractionName("Tardy");
        template.setLevel(1);
        template.setQuestions(List.of(question));
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());

        when(assignmentTemplateRepository.findAll()).thenReturn(List.of(template));

        List<AssignmentTemplateSummaryDTO> result =
                service.searchTemplates(null, null, null, null, null);

        assertEquals("Title preview", result.get(0).getFirstQuestionPreview());
    }

    @Test
    void searchTemplates_shouldBuildPreviewFromPassageWhenPromptAndTitleBlank() {
        String longPassage = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz1234567890";
        AssignmentTemplate.TemplateQuestion question = new AssignmentTemplate.TemplateQuestion();
        question.setPrompt(" ");
        question.setTitle(" ");
        question.setPassageBody(longPassage);

        AssignmentTemplate template = new AssignmentTemplate();
        template.setId("1");
        template.setName("Name");
        template.setInfractionName("Tardy");
        template.setLevel(1);
        template.setQuestions(List.of(question));
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());

        when(assignmentTemplateRepository.findAll()).thenReturn(List.of(template));

        List<AssignmentTemplateSummaryDTO> result =
                service.searchTemplates(null, null, null, null, null);

        assertNotNull(result.get(0).getFirstQuestionPreview());
        assertTrue(result.get(0).getFirstQuestionPreview().endsWith("..."));
    }

    @Test
    void buildAssignmentForPunishment_shouldResolveTemplate() throws Exception {
        Punishment punishment = new Punishment();
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionName("Tardy");
        punishment.setInfractionLevel("1");
        punishment.setStudentEmail("student@test.com");

        AssignmentTemplate template = new AssignmentTemplate();

        when(punishRepository.findById("1")).thenReturn(Optional.of(punishment));
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);
        when(bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrueOrderByCreatedAtDesc(
                "teacher@test.com", "Tardy", 1
        )).thenReturn(Optional.empty());
        when(assignmentTemplateRepository.findByInfractionNameAndLevelAndScopeAndActiveTrue(
                "Tardy", 1, AssignmentTemplate.Scope.SYSTEM_DEFAULT
        )).thenReturn(List.of(template));

        AssignmentTemplate result = service.buildAssignmentForPunishment("1");

        assertSame(template, result);
    }

    @Test
    void buildAssignmentForPunishment_shouldThrowWhenPunishmentMissing() {
        when(punishRepository.findById("1")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () -> service.buildAssignmentForPunishment("1"));

        assertEquals("Punishment not found: 1", ex.getMessage());
    }
}