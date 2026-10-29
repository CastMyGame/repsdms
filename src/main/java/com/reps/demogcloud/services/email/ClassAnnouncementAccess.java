package com.reps.demogcloud.services.email;

import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.services.tenancy.CanonicalAccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.regex.Pattern;

/** Always-on announcement-only boundary. No A7 activation, name-based adoption or background authority. */
@Service
@RequiredArgsConstructor
public class ClassAnnouncementAccess {
    private final CanonicalAccountContext accounts;
    private final MongoTemplate mongo;

    public record Audience(String sender, List<Student> students) {
        public Audience { students = List.copyOf(students); }
    }
    private record Staff(CanonicalAccountContext.Actor actor, Employee profile) {}

    public Audience resolve(ClassAnnouncementRequest request) {
        if (request == null) throw denied();
        Staff staff = staff(request.getTeacherEmail());
        var classes = staff.profile().getClasses();
        var matches = classes == null ? List.<Employee.ClassRoster>of() : classes.stream()
                .filter(Objects::nonNull)
                .filter(c -> Objects.equals(c.getClassName(), request.getClassName()))
                .filter(c -> request.getClassPeriod() == null || Objects.equals(c.getClassPeriod(), request.getClassPeriod())).toList();
        if (matches.isEmpty()) throw denied();
        if (matches.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Choose the class period before sending an announcement");
        return recipients(staff, matches.get(0).getClassRoster());
    }

    /** Recheck at the sender boundary. A worker/retry without an authenticated current account fails closed. */
    public String requireRecipients(String requestedSender, List<String> emails) {
        return recipients(staff(requestedSender), emails).sender();
    }

    private Staff staff(String requestedSender) {
        var actor = accounts.requireCurrent();
        if (actor.roles().contains("STUDENT")) throw denied();
        var employee = mongo.findById(actor.profileId(), Employee.class);
        if (employee == null || employee.isArchived() || !actor.tenantSchoolId().equals(employee.getTenantSchoolId())
                || !same(employee.getEmail(), requestedSender)) throw denied();
        return new Staff(actor, employee);
    }

    private Audience recipients(Staff staff, List<String> emails) {
        if (emails == null || emails.isEmpty()) throw denied();
        var result = new ArrayList<Student>();
        var seen = new HashSet<String>();
        for (String email : emails) {
            if (email == null || email.isBlank() || !email.equals(email.trim()) || !rostered(staff.profile(), email)) throw denied();
            // Global uniqueness is checked before ownership: a tenant-scoped lookup must not conceal ambiguity.
            var students = mongo.find(Query.query(Criteria.where("studentEmail").regex(exact(email))).limit(2), Student.class);
            var users = mongo.find(Query.query(Criteria.where("username").regex(exact(email))).limit(2), UserModel.class);
            if (students.size() != 1 || users.size() != 1) throw denied();
            var student = students.get(0);
            var member = accounts.requireProvisioned(users.get(0)); // Pending setup may receive mail; disabled accounts may not.
            if (student.isArchived() || !staff.actor().tenantSchoolId().equals(student.getTenantSchoolId())
                    || !staff.actor().tenantSchoolId().equals(member.tenantSchoolId())
                    || !member.roles().equals(Set.of("STUDENT")) || !Objects.equals(member.profileId(), student.getStudentIdNumber())) throw denied();
            if (seen.add(student.getStudentEmail().toLowerCase(Locale.ROOT))) result.add(student);
        }
        return new Audience(staff.profile().getEmail(), result);
    }

    private static boolean rostered(Employee employee, String email) {
        return employee.getClasses() != null && employee.getClasses().stream().filter(Objects::nonNull)
                .filter(c -> c.getClassRoster() != null).flatMap(c -> c.getClassRoster().stream()).anyMatch(e -> same(e, email));
    }
    private static Pattern exact(String email) { return Pattern.compile("^" + Pattern.quote(email) + "$", Pattern.CASE_INSENSITIVE); }
    private static boolean same(String a, String b) { return a != null && b != null && a.equalsIgnoreCase(b); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Class announcement access is unavailable"); }
}
