package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Classes retain legacy storage; identity is the normalized (name, period) pair. */
@Service
@RequiredArgsConstructor
public class EmployeeClassService {
    private final EmployeeRepository employeeRepository;
    private final MongoTemplate mongo;
    private com.reps.demogcloud.services.tenancy.CanonicalDomainAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalDomainAccess(com.reps.demogcloud.services.tenancy.CanonicalDomainAccess value){canonical=value;}

    public Employee addOrUpdateClassToEmployee(String email, ClassRequest request) {
        return change(email, request, false);
    }
    public Employee removeClassFromEmployee(String email, ClassRequest request) {
        return change(email, request, true);
    }
    private Employee change(String email, ClassRequest request, boolean delete) {
        if(canonical!=null && !org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())return canonical.transaction(()->change(email,request,delete));
        if (request == null || request.getClassToUpdate() == null) throw new IllegalArgumentException("Class is required");
        var input = request.getClassToUpdate();
        String name = text(input.getClassName()), period = text(input.getClassPeriod());
        if (name.isEmpty() || period.isEmpty()) throw new IllegalArgumentException("Class name and period are required");
        var teacher = canonical==null?employeeRepository.findByEmailIgnoreCase(email):canonical.roster(email,request);
        if (teacher == null) throw new ResourceNotFoundException("Teacher not found");
        if (teacher.isArchived()) throw new org.springframework.security.access.AccessDeniedException("Access denied");
        if (request.getExpectedRosterRevision() == null || request.getExpectedRosterRevision() != teacher.getRosterRevision()) throw conflict();
        var classes = new ArrayList<>(teacher.getClasses() == null ? List.<Employee.ClassRoster>of() : teacher.getClasses());
        var matches = classes.stream().filter(Objects::nonNull)
                .filter(c -> text(c.getClassName()).toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)) && text(c.getClassPeriod()).toLowerCase(Locale.ROOT).equals(period.toLowerCase(Locale.ROOT))).toList();
        // Never guess which duplicate legacy class the caller meant.
        if (matches.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate class identity requires administrator review");
        var existing = matches.isEmpty() ? null : matches.get(0);
        if (delete) {
            if (existing == null) throw new ResourceNotFoundException("Class not found");
            classes.remove(existing);
        } else {
            if (request.isCreateOnly() && existing != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This class name and period already exist");
            if (!request.isCreateOnly() && existing == null) throw new ResourceNotFoundException("Class no longer exists; reload classes");
            var updated = new Employee.ClassRoster();
            updated.setClassName(existing == null ? name : existing.getClassName());
            updated.setClassPeriod(existing == null ? period : existing.getClassPeriod());
            updated.setClassRoster(input.getClassRoster() == null ? List.of() : input.getClassRoster().stream()
                    .map(EmployeeClassService::text).map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).distinct().toList());
            updated.setPunishmentsThisWeek(existing == null ? 0 : existing.getPunishmentsThisWeek());
            if (existing == null) classes.add(updated); else classes.set(classes.indexOf(existing), updated);
        }
        Criteria revision = teacher.getRosterRevision() == 0
                ? new Criteria().orOperator(Criteria.where("rosterRevision").exists(false), Criteria.where("rosterRevision").is(0))
                : Criteria.where("rosterRevision").is(teacher.getRosterRevision());
        var query = new Query(new Criteria().andOperator(Criteria.where("_id").is(teacher.getEmployeeId()),
                (canonical==null?Criteria.where("school").is(teacher.getSchool()):Criteria.where("tenantSchoolId").is(canonical.actor().tenantSchoolId())), Criteria.where("archived").ne(true), revision));
        var result = mongo.findAndModify(query, new Update().set("classes", classes).inc("rosterRevision", 1),
                FindAndModifyOptions.options().returnNew(true), Employee.class);
        if (result == null) throw conflict();
        return result;
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Roster changed. Reload classes and reapply your changes; your input has not been saved.");
    }
}
