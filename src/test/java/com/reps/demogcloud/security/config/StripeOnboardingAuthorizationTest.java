package com.reps.demogcloud.security.config;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.models.stripe.*;
import com.reps.demogcloud.security.services.stripe.*;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No Stripe SDK calls: the already verified webhook session and persistence are synthetic mocks. */
class StripeOnboardingAuthorizationTest {
    UserRepository users; EmployeeRepository employees; SchoolRepository schools;
    RegistrationIntentRepository intents; RegistrationIntentLockService locks; PasswordEncoder encoder;
    StripeProvisioningService service; Session session; RegistrationIntent intent;
    @BeforeEach void setup() {
        users=mock(UserRepository.class);employees=mock(EmployeeRepository.class);schools=mock(SchoolRepository.class);
        intents=mock(RegistrationIntentRepository.class);locks=mock(RegistrationIntentLockService.class);encoder=mock(PasswordEncoder.class);
        service=new StripeProvisioningService(users,employees,schools,encoder,intents,locks);
        intent=RegistrationIntent.builder().id("synthetic-intent").email("new@example.test").schoolName("Testing")
                .schoolIdNumber("school-id").firstName("Synthetic").lastName("Teacher").priceId("synthetic-price").build();
        when(intents.findById("synthetic-intent")).thenReturn(Optional.of(intent));
        when(locks.claimForProcessing("synthetic-intent")).thenReturn(true);
        when(locks.markCompleted(any(),any(),any(),any())).thenReturn(true);
        var school=new School();school.setSchoolIdNumber("school-id");school.setSchoolName("Testing");
        when(schools.findById("school-id")).thenReturn(Optional.of(school));
        when(encoder.encode(any())).thenReturn("synthetic-encoded");
        session=new Session();session.setId("synthetic-checkout");session.setCustomer("synthetic-customer");session.setSubscription("synthetic-subscription");
        session.setMetadata(Map.of("registrationIntentId","synthetic-intent","userType","ADMIN"));
    }
    UserModel user(String role,String school,boolean enabled) {
        var user=new UserModel();user.setId("synthetic-user");user.setUsername("new@example.test");user.setSchool(school);
        user.setRoles(Set.of(new RoleModel(null,role)));user.setEnabled(enabled);return user;
    }
    @Test void publicCheckoutCreatesPendingTeacherOnly() {
        service.provisionTeacherFromCheckoutSession(session);
        var account=org.mockito.ArgumentCaptor.forClass(UserModel.class);verify(users).save(account.capture());
        assertFalse(account.getValue().isEnabled());
        assertEquals(Set.of(new RoleModel(null,"TEACHER")),account.getValue().getRoles());
        var employee=org.mockito.ArgumentCaptor.forClass(Employee.class);verify(employees).save(employee.capture());
        assertEquals("Testing",employee.getValue().getSchool());
    }
    @Test void existingTeacherBillingNeverChangesMembershipRolesOrActivation() {
        when(users.findByUsername("new@example.test")).thenReturn(user("teacher","Testing",true));
        var employee=new Employee();employee.setEmail("new@example.test");employee.setSchool("Testing");
        when(employees.findByEmailIgnoreCase("new@example.test")).thenReturn(employee);
        service.provisionTeacherFromCheckoutSession(session);
        verify(users).updateBillingIfEnabled(eq("synthetic-user"),eq("Testing"),any(),any(),any(),any());
        verify(users,never()).save(any());verify(employees,never()).save(any());
    }
    @Test void existingDisabledStudentAndForeignAccountsAreNeverPromotedOrReactivated() {
        for(var account:List.of(user("TEACHER","Testing",false),user("STUDENT","Testing",true),user("ADMIN","Other school",true))) {
            when(users.findByUsername("new@example.test")).thenReturn(account);
            assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
        }
        verify(users,never()).save(any());verify(employees,never()).save(any());
        verify(users,never()).updateBillingIfEnabled(any(),any(),any(),any(),any(),any());
    }
    @Test void mismatchedSchoolIdNameAndArchivedEmployeeFailBeforeProvisioning() {
        intent.setSchoolName("Other school");
        assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
        intent.setSchoolName("Testing");
        var employee=new Employee();employee.setSchool("Testing");employee.setArchived(true);
        when(employees.findByEmailIgnoreCase("new@example.test")).thenReturn(employee);
        assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
        verify(users,never()).save(any());verify(employees,never()).save(any());
    }

    @Test void checkoutCannotRepairMissingProfileForAnExistingPrivilegedAccount() {
        when(users.findByUsername("new@example.test")).thenReturn(user("ADMIN","Testing",true));
        assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
        verify(users,never()).save(any());verify(employees,never()).save(any());
        verify(users,never()).updateBillingIfEnabled(any(),any(),any(),any(),any(),any());
    }

    @Test void samePendingCheckoutCanRecoverWithoutGrantingAccess() {
        var pending=user("TEACHER","Testing",false);pending.setPaid(true);
        pending.setStripeCustomerId("synthetic-customer");pending.setStripeSubscriptionId("synthetic-subscription");
        when(users.findByUsername("new@example.test")).thenReturn(pending);
        var employee=new Employee();employee.setEmail("new@example.test");employee.setSchool("Testing");
        when(employees.findByEmailIgnoreCase("new@example.test")).thenReturn(employee);
        service.provisionTeacherFromCheckoutSession(session);
        assertFalse(pending.isEnabled());
        verify(users,never()).updateEnabledInSchool(any(),any(),anyBoolean());
        verify(users,never()).save(any());verify(employees,never()).save(any());
        session.setSubscription("different-subscription");
        assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
    }
}
