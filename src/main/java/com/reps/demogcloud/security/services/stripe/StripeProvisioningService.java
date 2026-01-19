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


    public void provisionTeacherFromCheckoutSession(Session session) {
        if (session == null) return;

        String intentId = session.getMetadata() != null ? session.getMetadata().get("registrationIntentId") : null;
        if (isBlank(intentId)) {
            // Fallback to old behavior? I'd rather fail fast so you notice wiring issues.
            throw new IllegalStateException("Missing registrationIntentId in session metadata");
        }

        RegistrationIntent intent = registrationIntentRepository.findById(intentId).orElse(null);
        if (intent == null) {
            throw new IllegalStateException("RegistrationIntent not found: " + intentId);
        }

        // Idempotency at your business level (in addition to StripeEventLog)
        if ("COMPLETED".equalsIgnoreCase(intent.getStatus())) return;

        // Stripe identifiers
        String stripeCheckoutSessionId = session.getId();
        String stripeCustomerId = session.getCustomer();
        String stripeSubscriptionId = session.getSubscription();

        // Use intent data as source of truth
        String schoolName = safeTrim(intent.getSchoolName());
        String schoolIdNumber = safeTrim(intent.getSchoolIdNumber());
        String currencyName = safeTrim(intent.getCurrencyName());

        String email = safeLower(intent.getEmail());
        String firstName = safeTrim(intent.getFirstName());
        String lastName = safeTrim(intent.getLastName());
        String stripePriceId = safeTrim(intent.getPriceId());

        if (isBlank(schoolName) || isBlank(schoolIdNumber) || isBlank(email)) {
            throw new IllegalStateException("RegistrationIntent is missing required fields: " + intentId);
        }

        // Ensure school exists (prefer id when possible)
        ensureSchoolExists(schoolIdNumber, schoolName, currencyName);

        // Create/update employee using first/last from intent
        ensureEmployeeTeacherExists(email, firstName, lastName, schoolName);

        // Create/update user model
        ensureUserModelTeacherExists(email, schoolName, stripeCustomerId, stripeSubscriptionId, stripePriceId);

        // Mark intent completed + store Stripe IDs for audit/debug
        intent.setStatus("COMPLETED");
        intent.setStripeCheckoutSessionId(stripeCheckoutSessionId);
        intent.setStripeCustomerId(stripeCustomerId);
        intent.setStripeSubscriptionId(stripeSubscriptionId);
        registrationIntentRepository.save(intent);
    }

    private String safeTrim(String s) { return s == null ? null : s.trim(); }
    private String safeLower(String s) { return s == null ? null : s.trim().toLowerCase(); }


    private void ensureSchoolExists(String schoolIdNumber, String schoolName, String currencyName) {
        School school = schoolRepository.findById(schoolIdNumber).orElse(null);

        if (school == null) {
            School newSchool = new School();
            newSchool.setSchoolIdNumber(schoolIdNumber);
            newSchool.setSchoolName(schoolName);
            newSchool.setMaxPunishLevel(4);
            newSchool.setCurrency(isBlank(currencyName) ? "points" : currencyName);
            schoolRepository.save(newSchool);
            return;
        }

        // Optional: keep it simple, but you can ensure name/currency match what user picked
        boolean changed = false;

        if (!isBlank(schoolName) && (school.getSchoolName() == null || !school.getSchoolName().equals(schoolName))) {
            school.setSchoolName(schoolName);
            changed = true;
        }
        if (!isBlank(currencyName) && (school.getCurrency() == null || !school.getCurrency().equals(currencyName))) {
            school.setCurrency(currencyName);
            changed = true;
        }

        if (changed) schoolRepository.save(school);
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
        } else {
            emp.setArchived(false);
            emp.setSchool(schoolName);

            // backfill names if missing (keeps it safe)
            if (!isBlank(firstName) && isBlank(emp.getFirstName())) emp.setFirstName(firstName);
            if (!isBlank(lastName) && isBlank(emp.getLastName())) emp.setLastName(lastName);

            ensureRole(emp.getRoles(), "TEACHER");
            employeeRepository.save(emp);
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
            newUser.setEnabled(true);
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
            user.setEnabled(true);
            user.setSchool(schoolName);
            user.setPaid(true);
            user.setStripeCustomerId(stripeCustomerId);
            user.setStripeSubscriptionId(stripeSubscriptionId);
            user.setStripePriceId(stripePriceId);
            user.setStripeStatus("active");
            user.setLastPaymentAt(now);

            if (user.getRoles() != null) ensureRole(user.getRoles(), "TEACHER");
            if (isBlank(user.getPassword())) {
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            }
            userRepository.save(user);
        }
    }

    private Set<RoleModel> teacherRoles() {
        Set<RoleModel> roles = new HashSet<>();
        RoleModel teacher = new RoleModel();
        teacher.setRole("TEACHER");
        roles.add(teacher);
        return roles;
    }

    private void ensureRole(Set<RoleModel> roles, String roleName) {
        if (roles == null) return;
        boolean has = roles.stream().anyMatch(r -> r != null && roleName.equalsIgnoreCase(r.getRole()));
        if (!has) {
            RoleModel rm = new RoleModel();
            rm.setRole(roleName);
            roles.add(rm);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

