package com.reps.demogcloud.security.services.stripe;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.models.stripe.RegistrationIntent;
import com.reps.demogcloud.security.models.stripe.RegistrationIntentRepository;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StripeProvisioningService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationIntentRepository registrationIntentRepository;
    private final RegistrationIntentLockService registrationIntentLockService;

    public void provisionTeacherFromCheckoutSession(Session session) {
        if (session == null) return;

        String intentId = (session.getMetadata() != null)
                ? session.getMetadata().get("registrationIntentId")
                : null;

        if (isBlank(intentId)) {
            throw new IllegalStateException("Missing registrationIntentId in session metadata");
        }

        // ✅ claim lock first (prevents double provisioning)
        boolean claimed = registrationIntentLockService.claimForProcessing(intentId);
        if (!claimed) {
            // someone else is processing or already completed
            return;
        }

        try {
            RegistrationIntent intent = registrationIntentRepository.findById(intentId)
                    .orElseThrow(() -> new IllegalStateException("RegistrationIntent not found: " + intentId));

            // Stripe identifiers
            String stripeCheckoutSessionId = session.getId();
            String stripeCustomerId = session.getCustomer();
            String stripeSubscriptionId = session.getSubscription();

            if (isBlank(stripeCheckoutSessionId)) {
                throw new IllegalStateException("Missing Stripe checkout session id");
            }
            if (isBlank(stripeCustomerId)) {
                throw new IllegalStateException("Missing Stripe customer id on session " + stripeCheckoutSessionId);
            }
            if (isBlank(stripeSubscriptionId)) {
                throw new IllegalStateException("Missing Stripe subscription id on session " + stripeCheckoutSessionId);
            }

            // intent source of truth
            String schoolName = safeTrim(intent.getSchoolName());
            String schoolIdNumber = safeTrim(intent.getSchoolIdNumber());
            String currencyName = safeTrim(intent.getCurrencyName());

            String email = safeLower(intent.getEmail());
            String firstName = safeTrim(intent.getFirstName());
            String lastName = safeTrim(intent.getLastName());
            String stripePriceId = safeTrim(intent.getPriceId());

            if (isBlank(schoolName) || isBlank(schoolIdNumber) || isBlank(email)) {
                throw new IllegalStateException("RegistrationIntent missing required fields: " + intentId);
            }

            // make sure school exists (you said you prefer fail fast if not)
            School school = requireSchoolExists(schoolIdNumber);
            if (!schoolName.equalsIgnoreCase(school.getSchoolName()))
                throw new IllegalStateException("Registration school does not match its identifier");
            // A public checkout may not transfer, reactivate or promote an existing account.
            UserModel existingUser = userRepository.findByUsername(email);
            Employee existingEmployee = employeeRepository.findByEmailIgnoreCase(email);
            // Signed payment proves payment only. Never provision, activate, transfer or relink an account.
            if (existingUser == null || existingEmployee == null || existingEmployee.isArchived()
                    || !email.equalsIgnoreCase(existingUser.getUsername()) || !email.equalsIgnoreCase(existingEmployee.getEmail())
                    || !schoolName.equalsIgnoreCase(existingUser.getSchool()) || !schoolName.equalsIgnoreCase(existingEmployee.getSchool())
                    || existingUser.getRoles() == null || existingUser.getRoles().isEmpty()
                    || existingUser.getRoles().stream().anyMatch(r -> r == null || r.getRole() == null
                    || !Set.of("TEACHER", "GUIDANCE", "ADMIN").contains(r.getRole().toUpperCase(java.util.Locale.ROOT)))
                    || !stripeCheckoutSessionId.equals(intent.getStripeCheckoutSessionId())
                    || !stripeCustomerId.equals(existingUser.getStripeCustomerId())
                    || !stripeSubscriptionId.equals(existingUser.getStripeSubscriptionId())
                    || !stripePriceId.equals(existingUser.getStripePriceId()))
                throw new IllegalStateException("Existing billing association requires controlled review");
            String accountOwner=existingUser.getTenantSchoolId(), profileOwner=existingEmployee.getTenantSchoolId();
            if ((accountOwner != null || profileOwner != null)
                    && (!schoolIdNumber.equals(accountOwner) || !schoolIdNumber.equals(profileOwner)))
                throw new IllegalStateException("Billing tenant ownership requires controlled review");
            // Legacy unbound billing is supported only for the exact already-recorded customer/subscription.
            // It grants no membership, role or activation and changes no billing identifiers.
            if (existingUser.isEnabled() && userRepository.confirmExistingBilling(existingUser.getId(),
                    stripeCustomerId, stripeSubscriptionId, Instant.now()) != 1)
                throw new IllegalStateException("Existing billing association changed concurrently");

            // ✅ mark completed atomically (also writes Stripe ids)
            boolean completed = registrationIntentLockService.markCompleted(
                    intentId,
                    stripeCheckoutSessionId,
                    stripeCustomerId,
                    stripeSubscriptionId
            );

            if (!completed) {
                // should be rare, but if status wasn't PROCESSING anymore
                throw new IllegalStateException("Could not mark intent COMPLETED (intentId=" + intentId + ")");
            }

        } catch (Exception e) {
            // ✅ rollback so Stripe retry can succeed
            registrationIntentLockService.releaseProcessing(intentId);
            throw e;
        }
    }

    private School requireSchoolExists(String schoolIdNumber) {
        return schoolRepository.findById(schoolIdNumber)
                .orElseThrow(() -> new IllegalStateException("School not found for id=" + schoolIdNumber));
    }

    private void ensureEmployeeTeacherExists(String email, String firstName, String lastName, String schoolName) {
        Employee emp = employeeRepository.findByEmailIgnoreCase(email);
        if (emp == null) {
            Employee newEmp = new Employee();
            newEmp.setEmail(email);
            newEmp.setFirstName(firstName);
            newEmp.setLastName(lastName);
            newEmp.setSchool(schoolName);
            newEmp.setArchived(false);
            newEmp.setRoles(teacherRoles());
            employeeRepository.save(newEmp);
        }
    }

    private void ensureUserModelTeacherExists(
            String email,
            String schoolName,
            String stripeCustomerId,
            String stripeSubscriptionId,
            String stripePriceId
    ) {
        UserModel user = userRepository.findByUsername(email);
        Instant now = Instant.now();

        if (user == null) {
            UserModel newUser = new UserModel();
            newUser.setUsername(email);
            newUser.setSchool(schoolName);
            // Payment does not prove school membership. A school administrator must activate the account.
            newUser.setEnabled(false);
            newUser.setPasswordSetupRequired(true);
            newUser.setRoles(teacherRoles());

            newUser.setPaid(true);
            newUser.setStripeCustomerId(stripeCustomerId);
            newUser.setStripeSubscriptionId(stripeSubscriptionId);
            newUser.setStripePriceId(stripePriceId);
            newUser.setStripeStatus("active");
            newUser.setLastPaymentAt(now);

            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            userRepository.save(newUser);
        } else {
            userRepository.updateBillingIfEnabled(user.getId(), user.getSchool(),
                    stripeCustomerId, stripeSubscriptionId, stripePriceId, now);
        }
    }

    private Set<RoleModel> teacherRoles() {
        Set<RoleModel> roles = new HashSet<>();
        RoleModel teacher = new RoleModel();
        teacher.setRole("TEACHER");
        roles.add(teacher);
        return roles;
    }

    private String safeTrim(String s) { return s == null ? null : s.trim(); }
    private String safeLower(String s) { return s == null ? null : s.trim().toLowerCase(); }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
