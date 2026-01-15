package com.reps.demogcloud.security.services.stripe;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
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

    public void provisionTeacherFromCheckoutSession(Session session) {
        if (session == null) return;

        String schoolName = session.getMetadata() != null ? session.getMetadata().get("schoolName") : null;
        String email = session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null;

        if (schoolName != null) schoolName = schoolName.trim();
        if (email != null) email = email.trim().toLowerCase();

        String stripeCustomerId = session.getCustomer();
        String stripeSubscriptionId = session.getSubscription();
        String stripePriceId = session.getMetadata() != null ? session.getMetadata().get("priceId") : null;

        if (isBlank(schoolName) || isBlank(email)) return;

        ensureSchoolExists(schoolName);
        ensureEmployeeTeacherExists(email, schoolName);
        ensureUserModelTeacherExists(email, schoolName, stripeCustomerId, stripeSubscriptionId, stripePriceId);
    }

    private void ensureSchoolExists(String schoolName) {
        School school = schoolRepository.findSchoolBySchoolName(schoolName);
        if (school == null) {
            School newSchool = new School();
            newSchool.setSchoolName(schoolName);
            newSchool.setMaxPunishLevel(4);
            newSchool.setCurrency("points");
            schoolRepository.save(newSchool);
        }
    }

    private void ensureEmployeeTeacherExists(String email, String schoolName) {
        Employee emp = employeeRepository.findByEmailIgnoreCase(email);
        if (emp == null) {
            Employee newEmp = new Employee();
            newEmp.setEmail(email);
            newEmp.setSchool(schoolName);
            newEmp.setArchived(false);
            newEmp.setRoles(teacherRoles());
            employeeRepository.save(newEmp);
        } else {
            emp.setArchived(false);
            emp.setSchool(schoolName);
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

