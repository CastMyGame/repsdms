package com.reps.demogcloud.services.tenancy;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CanonicalAccountContextTest {
    UserRepository users=mock(UserRepository.class);
    EmployeeRepository employees=mock(EmployeeRepository.class);
    StudentRepository students=mock(StudentRepository.class);
    SchoolRepository schools=mock(SchoolRepository.class);
    CanonicalAccountContext context=new CanonicalAccountContext(users,employees,students,schools);
    UserModel account;Employee employee;

    @BeforeEach void seed() {
        account=new UserModel();account.setId("account-A");account.setUsername("teacher@example.test");account.setPassword("synthetic");
        account.setRoles(Set.of(new RoleModel(null,"TEACHER")));account.setTenantSchoolId("school-A");account.setSchool("Same School");
        employee=new Employee();employee.setEmployeeId("profile-A");employee.setEmail(account.getUsername());
        employee.setRoles(account.getRoles());employee.setTenantSchoolId("school-A");employee.setSchool("Same School");
        when(users.findByUsername(account.getUsername())).thenReturn(account);
        when(employees.findByEmailIgnoreCase(account.getUsername())).thenReturn(employee);
        for(String id:List.of("school-A","school-B")) {var s=new School();s.setSchoolIdNumber(id);s.setSchoolName("Same School");when(schools.findById(id)).thenReturn(Optional.of(s));}
        login();
    }
    void login() {
        var principal=new AccountPrincipal(account,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    void denied() {assertThrows(AccessDeniedException.class,context::requireCurrent);}

    @Test void exactMembershipUsesPersistedRoleRatherThanPrincipalClaims() {
        var actor=context.requireCurrent();assertEquals("school-A",actor.tenantSchoolId());assertEquals("account-A",actor.accountId());
        assertEquals(Set.of("TEACHER"),actor.roles());assertThrows(UnsupportedOperationException.class,()->actor.roles().add("ADMIN"));
    }
    @Test void schoolNamesDoNotAuthorizeOrInvalidateCanonicalMembership() {
        account.setSchool("Historical Label");employee.setSchool("Other Display Label");assertEquals("school-A",context.requireCurrent().tenantSchoolId());
        employee.setTenantSchoolId("school-B");employee.setSchool(account.getSchool());denied();
    }
    @Test void secondSameNameSchoolHasIndependentCanonicalIdentity() {
        account.setTenantSchoolId("school-B");employee.setTenantSchoolId("school-B");assertEquals("school-B",context.requireCurrent().tenantSchoolId());
    }
    @Test void missingOrMalformedAccountOwnerNeverFallsBackToNames() {
        for(String id:new String[]{null,"","school-A "," school-A","SCHOOL-A"}) {account.setTenantSchoolId(id);denied();}
    }
    @Test void missingOrMismatchedProfileOwnerIsNotRepaired() {
        for(String id:new String[]{null,"","school-B","school-A "}) {employee.setTenantSchoolId(id);denied();assertEquals(id,employee.getTenantSchoolId());}
    }
    @Test void nonexistentSchoolIsDenied() {when(schools.findById("school-A")).thenReturn(Optional.empty());denied();}
    @Test void disabledAndPendingAccountsAreDenied() {account.setEnabled(false);denied();account.setEnabled(true);account.setPasswordSetupRequired(true);denied();}
    @Test void archivedMissingAndWrongEmailStaffProfilesAreDenied() {
        employee.setArchived(true);denied();employee.setArchived(false);employee.setEmail("different@example.test");denied();
        when(employees.findByEmailIgnoreCase(account.getUsername())).thenReturn(null);denied();
    }
    @Test void ambiguousProfilesAndPersistedRolesAreDenied() {
        when(students.findByStudentEmailIgnoreCase(account.getUsername())).thenReturn(new Student());denied();
        when(students.findByStudentEmailIgnoreCase(account.getUsername())).thenReturn(null);
        employee.setRoles(Set.of(new RoleModel(null,"ADMIN")));denied();
        account.setRoles(Set.of(new RoleModel(null,"STUDENT"),new RoleModel(null,"TEACHER")));denied();
    }
    @Test void credentialVersionChangeRevokesAnExistingPrincipal() {account.setCredentialVersion(1);denied();}
    @Test void anonymousOrUnversionedPrincipalsCannotBecomeCanonicalActors() {
        SecurityContextHolder.clearContext();denied();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(account.getUsername(),null,List.of()));denied();
    }
    @Test void validStudentAndArchivedStudentUseThePersistedStudentProfile() {
        account.setRoles(Set.of(new RoleModel(null,"STUDENT")));login();
        when(employees.findByEmailIgnoreCase(account.getUsername())).thenReturn(null);
        var s=new Student();s.setStudentIdNumber("child");s.setStudentEmail(account.getUsername());s.setTenantSchoolId("school-A");
        when(students.findByStudentEmailIgnoreCase(account.getUsername())).thenReturn(s);
        assertEquals(Set.of("STUDENT"),context.requireCurrent().roles());assertEquals("child",context.requireCurrent().profileId());
        s.setArchived(true);denied();
    }
    @Test void ambiguousRepositoryResultIsSanitized() {
        when(employees.findByEmailIgnoreCase(account.getUsername())).thenThrow(new org.springframework.dao.IncorrectResultSizeDataAccessException(1,2));
        assertEquals("Canonical account membership is unavailable",assertThrows(AccessDeniedException.class,context::requireCurrent).getMessage());
    }
}
