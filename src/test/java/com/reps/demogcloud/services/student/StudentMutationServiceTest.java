package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.services.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentMutationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentQueryService studentQueryService;

    @InjectMocks
    private StudentMutationService studentMutationService;

    Student input() {
        var s = new Student(); s.setStudentEmail("Student@example.test"); s.setFirstName("Synthetic"); s.setLastName("Child"); s.setSchool("Testing"); return s;
    }
    @Test void creationUsesInsertAndCannotInjectIdentifiersOrBalances() {
        var s = input(); s.setStudentIdNumber("another-student"); s.setCurrency(1000); s.setArchived(true);
        when(studentRepository.insert(any(Student.class))).thenAnswer(i -> i.getArgument(0));
        var result = studentMutationService.createNewStudent(s).getStudent();
        assertEquals(AuthService.studentProvisioningId(s.getStudentEmail()), result.getStudentIdNumber());
        assertEquals("student@example.test",result.getStudentEmail()); assertFalse(result.isArchived()); assertEquals(0,result.getCurrency());
        verify(authService).provisionStudent(any()); verify(studentRepository,never()).save(any());
    }
    @Test void matchingExistingStudentIsIdempotentAndNotOverwritten() {
        var existing = input(); existing.setCurrency(45);
        when(studentRepository.findByStudentEmailIgnoreCase("student@example.test")).thenReturn(existing);
        assertSame(existing,studentMutationService.createNewStudent(input()).getStudent());
        assertEquals(45,existing.getCurrency()); verifyNoInteractions(authService); verify(studentRepository,never()).insert(any(Student.class));
    }
    @Test void foreignArchivedOrDifferentStudentRequiresExplicitResolution() {
        var existing=input(); existing.setSchool("Other school");
        when(studentRepository.findByStudentEmailIgnoreCase("student@example.test")).thenReturn(existing);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->studentMutationService.createNewStudent(input()));
        existing.setSchool("Testing");existing.setArchived(true);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->studentMutationService.createNewStudent(input()));
        existing.setArchived(false);existing.setFirstName("Different");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->studentMutationService.createNewStudent(input()));
        verifyNoInteractions(authService);
    }
    @Test void failedProfileInsertCanBeRetriedWithoutAccountOrStudentOverwrite() {
        when(studentRepository.insert(any(Student.class))).thenThrow(new IllegalStateException("synthetic persistence failure")).thenAnswer(i->i.getArgument(0));
        assertThrows(IllegalStateException.class,()->studentMutationService.createNewStudent(input()));
        assertNotNull(studentMutationService.createNewStudent(input()).getStudent());
        verify(authService,times(2)).provisionStudent(any()); verify(studentRepository,never()).save(any());
    }
    @Test void concurrentDuplicateInsertReturnsMatchingSavedProfile() {
        when(studentRepository.findByStudentEmailIgnoreCase("student@example.test")).thenReturn(null,input());
        when(studentRepository.insert(any(Student.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("synthetic race"));
        assertNotNull(studentMutationService.createNewStudent(input()).getStudent());
    }
    @Test void missingRequiredIdentityIsRejectedBeforeWrites() {
        var s=input();s.setFirstName("");assertThrows(IllegalArgumentException.class,()->studentMutationService.createNewStudent(s));verifyNoInteractions(authService,studentRepository);
    }

    @Test void destructiveDeletionIsRetiredBeforeAnyRepositoryAccess() {
        var failure=assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> studentMutationService.deleteStudent(new StudentRequest()));
        assertEquals(410,failure.getStatusCode().value()); verifyNoInteractions(studentRepository);
    }
}
