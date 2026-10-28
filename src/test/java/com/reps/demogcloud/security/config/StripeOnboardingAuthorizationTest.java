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
        when(users.confirmExistingBilling(any(),any(),any(),any())).thenReturn(1L);
        intent.setStripeCheckoutSessionId("synthetic-checkout");
        var school=new School();school.setSchoolIdNumber("school-id");school.setSchoolName("Testing");
        when(schools.findById("school-id")).thenReturn(Optional.of(school));
        when(encoder.encode(any())).thenReturn("synthetic-encoded");
        session=new Session();session.setId("synthetic-checkout");session.setCustomer("synthetic-customer");session.setSubscription("synthetic-subscription");
        session.setMetadata(Map.of("registrationIntentId","synthetic-intent","userType","ADMIN"));
    }
    UserModel user(String role,String school,boolean enabled) {
        var user=new UserModel();user.setId("synthetic-user");user.setUsername("new@example.test");user.setSchool(school);
        user.setRoles(Set.of(new RoleModel(null,role)));user.setEnabled(enabled);user.setStripeCustomerId("synthetic-customer");user.setStripeSubscriptionId("synthetic-subscription");user.setStripePriceId("synthetic-price");return user;
    }
    @Test void paymentCannotCreateAnAccountOrProfile() {
        assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
        verify(users,never()).save(any());verify(employees,never()).save(any());
        verify(locks).releaseProcessing("synthetic-intent");
    }
    @Test void existingTeacherBillingNeverChangesMembershipRolesOrActivation() {
        when(users.findByUsername("new@example.test")).thenReturn(user("teacher","Testing",true));
        var employee=new Employee();employee.setEmail("new@example.test");employee.setSchool("Testing");
        when(employees.findByEmailIgnoreCase("new@example.test")).thenReturn(employee);
        service.provisionTeacherFromCheckoutSession(session);
        verify(users).confirmExistingBilling(eq("synthetic-user"),eq("synthetic-customer"),eq("synthetic-subscription"),any());
        verify(users,never()).save(any());verify(employees,never()).save(any());
    }
    @Test void existingDisabledStudentAndForeignAccountsAreNeverPromotedOrReactivated() {
        for(var account:List.of(user("STUDENT","Testing",true),user("ADMIN","Other school",true))) {
            when(users.findByUsername("new@example.test")).thenReturn(account);
            assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
        }
        verify(users,never()).save(any());verify(employees,never()).save(any());
        verify(users,never()).confirmExistingBilling(any(),any(),any(),any());
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
        verify(users,never()).confirmExistingBilling(any(),any(),any(),any());
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
    @Test void mismatchedCanonicalOwnersAndNewBillingLinksAreRejected() {
        var account=user("TEACHER","Testing",true);account.setTenantSchoolId("other-school");when(users.findByUsername("new@example.test")).thenReturn(account);
        var employee=new Employee();employee.setEmail("new@example.test");employee.setSchool("Testing");employee.setTenantSchoolId("school-id");when(employees.findByEmailIgnoreCase("new@example.test")).thenReturn(employee);
        assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
        account.setTenantSchoolId("school-id");account.setStripeSubscriptionId("unrelated-subscription");
        assertThrows(IllegalStateException.class,()->service.provisionTeacherFromCheckoutSession(session));
        verify(users,never()).confirmExistingBilling(any(),any(),any(),any());
    }
    @Test void directCheckoutServiceCannotStartStripeOrSaveAnIntent() {
        var billing=new StripeBillingService(new org.springframework.mock.env.MockEnvironment(),intents);
        var result=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->billing.createCheckoutSessionUrl(null));
        assertEquals(503,result.getStatusCode().value());verify(intents,never()).save(any());
    }

}
