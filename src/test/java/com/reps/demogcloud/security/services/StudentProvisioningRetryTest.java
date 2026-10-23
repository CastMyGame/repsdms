package com.reps.demogcloud.security.services;
import com.reps.demogcloud.security.models.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StudentProvisioningRetryTest {
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    AuthService service = new AuthService(users,encoder);
    AuthenticationRequest request() {
        var r=new AuthenticationRequest();r.setUsername("child@example.test");r.setSchool("Testing");r.setFirstName("Synthetic");r.setLastName("Child");r.setRoles(Set.of(new RoleModel(null,"STUDENT")));return r;
    }
    UserModel account() {
        var u=new UserModel();u.setId(AuthService.studentProvisioningId("child@example.test"));u.setUsername("child@example.test");u.setSchool("Testing");u.setFirstName("Synthetic");u.setLastName("Child");u.setRoles(Set.of(new RoleModel(null,"STUDENT")));return u;
    }
    @Test void insertsUseStableIdAndUnusableRandomPassword() {
        when(encoder.encode(anyString())).thenReturn("synthetic-encoded");when(users.insert(any(UserModel.class))).thenAnswer(i->i.getArgument(0));
        var result=service.provisionStudent(request());assertEquals(account().getId(),result.getId());assertTrue(result.isPasswordSetupRequired());verify(users,never()).save(any());
    }
    @Test void retryKeepsPasswordSetupAndVersionUntouched() {
        var existing=account();existing.setPassword("existing-encoded");existing.setCredentialVersion(7);when(users.findByUsernameIgnoreCase(anyString())).thenReturn(existing);
        assertSame(existing,service.provisionStudent(request()));assertEquals(7,existing.getCredentialVersion());assertEquals("existing-encoded",existing.getPassword());verify(users,never()).insert(any(UserModel.class));verifyNoInteractions(encoder);
    }
    @Test void racingInsertResolvesSameAccountInsteadOfDuplicating() {
        when(encoder.encode(anyString())).thenReturn("synthetic-encoded");when(users.insert(any(UserModel.class))).thenThrow(new DuplicateKeyException("synthetic"));
        var existing=account();when(users.findById(existing.getId())).thenReturn(Optional.of(existing));assertSame(existing,service.provisionStudent(request()));
    }
    @Test void legacyForeignDisabledOrStaffAccountsAreNeverAdoptedOrOverwritten() {
        var existing=account();when(users.findByUsernameIgnoreCase(anyString())).thenReturn(existing);
        existing.setId("legacy-id");assertThrows(ResponseStatusException.class,()->service.provisionStudent(request()));
        existing.setId(account().getId());existing.setSchool("Other school");assertThrows(ResponseStatusException.class,()->service.provisionStudent(request()));
        existing.setSchool("Testing");existing.setEnabled(false);assertThrows(ResponseStatusException.class,()->service.provisionStudent(request()));
        existing.setEnabled(true);existing.setRoles(Set.of(new RoleModel(null,"TEACHER")));assertThrows(ResponseStatusException.class,()->service.provisionStudent(request()));
        verify(users,never()).insert(any(UserModel.class));verify(users,never()).save(any());
    }
}
