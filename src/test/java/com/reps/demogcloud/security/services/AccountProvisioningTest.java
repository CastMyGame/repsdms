package com.reps.demogcloud.security.services;
import com.reps.demogcloud.security.models.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class AccountProvisioningTest {
 @Test void initialCredentialsCannotBeChosenByCallerOrDerivedFromIdentity(){var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);when(encoder.encode(anyString())).thenReturn("encoded");when(users.save(any())).thenAnswer(i->i.getArgument(0));var request=new AuthenticationRequest();request.setUsername("student@example.test");request.setPassword("student@example.test");request.setSchool("Testing");request.setRoles(Set.of(new RoleModel(null,"STUDENT")));var service=new AuthService(users,encoder);var first=service.createEmployeeUser(request);service.createEmployeeUser(request);var capture=org.mockito.ArgumentCaptor.forClass(String.class);verify(encoder,times(2)).encode(capture.capture());assertNotEquals(capture.getAllValues().get(0),capture.getAllValues().get(1));assertTrue(capture.getAllValues().get(0).length()>=32);assertFalse(capture.getAllValues().contains(request.getUsername()));assertTrue(first.isPasswordSetupRequired());assertEquals(request.getRoles(),first.getRoles());assertEquals("Testing",first.getSchool());}
}
