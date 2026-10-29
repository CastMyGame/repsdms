package com.reps.demogcloud.services.currency;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.*;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import com.reps.demogcloud.services.punishment.PunishmentCreationService.Prepared;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.function.*;

@Service
@RequiredArgsConstructor
public class CurrencyService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private CanonicalCurrencyTransactions canonicalTransactions;
    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical(){return access.canonical();}
    private CurrencyOperation execute(String kind,Object payload,Consumer<CurrencyOperation> work){
        if(canonical()==null){if(canonicalTransactions!=null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Canonical currency requires domain ownership validation");return transactions.execute(kind,payload,work);}
        if(canonicalTransactions==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Canonical currency replay verification is required");
        return canonicalTransactions.execute(kind,payload,work);
    }

    private final CurrencyTransactions transactions;
    private final MongoTemplate mongo;
    private final EmployeeRepository employees;
    private final StudentRepository students;
    private final PunishRepository punishments;
    private final WorkflowAccessService access;
    private final UserContextService users;
    private final RequiredNotificationService notifications;

    public List<PunishmentResponse> award(List<PunishmentFormRequest> requests,
            Supplier<List<Prepared>> prepare,Consumer<Prepared> persist) {
        access.requireStaff(); batch(requests);
        var receipt=execute("AWARD",requests,operation->{
            var teacher=(canonical()==null?employees.findByEmailIgnoreCase(users.getCurrentStaffEmail()):canonical().employee(users.getCurrentStaffEmail()));
            access.requireStaff();
            if(teacher==null || teacher.isArchived() || !(canonical()==null?operation.getSchool().equalsIgnoreCase(teacher.getSchool()):canonical().owns(teacher))) denied();
            int balance=balance(teacher.getCurrency());
            var seen=new HashSet<String>(); long total=0;
            // All identity, template/infraction and payload validation precedes every wallet change.
            List<Prepared> prepared=prepare.get();
            if(prepared.size()!=requests.size()) throw new IllegalArgumentException("Incomplete award batch");
            for(Prepared item:prepared) {
                var request=item.request(); var student=item.student();
                requireStudent(student);
                if(!seen.add(student.getStudentEmail().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A student may appear only once per award batch");
                if(request.getCurrency()<0) throw new IllegalArgumentException("Award must be a nonnegative whole number");
                int amount=request.getCurrency(); total+=amount;
                if(total>Integer.MAX_VALUE || total>balance) conflict("Insufficient teacher balance for the complete batch");
                if(amount>0 && (long)balance(student.getCurrency())+amount>Integer.MAX_VALUE) conflict("Student balance limit reached");
                if(mongo.exists(Query.query(Criteria.where("_id").is(item.punishment().getPunishmentId())),Punishment.class))
                    conflict("Existing recognition has no matching currency receipt; staff review is required");
                operation.getAmounts().put(student.getStudentIdNumber(),amount);
            }
            if(total>0) changeEmployee(teacher,-(int)total);
            operation.setTeacherBalance(balance-(int)total);
            for(Prepared item:prepared) {
                int amount=item.request().getCurrency();
                if(amount>0) changeStudent(item.student(),amount);
                item.punishment().setCurrencyOperationId(operation.getId());
                item.punishment().setCurrencyAwarded(amount);
                persist.accept(item);
                operation.getCaseIds().add(item.punishment().getPunishmentId());
            }
        });
        var result=new ArrayList<PunishmentResponse>();
        for(String id:receipt.getCaseIds()) {
            var punishment=canonical()==null?punishments.findById(id).orElseThrow(()->new IllegalStateException("Committed recognition unavailable")):canonical().record(id,Punishment.class,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.PUNISHMENT);
            access.requireStudentAccess(punishment.getStudentEmail());
            var response=notifications.response(punishment);
            response.setCurrencyOperationId(receipt.getId());response.setCurrencyState("COMMITTED");
            response.setTeacherCurrency(receipt.getTeacherBalance());result.add(response);
        }
        return result;
    }
    public List<Student> spend(List<CurrencySpendRequest> requests) {
        access.requireStaff();batch(requests);
        var receipt=execute("REDEEM",requests,operation->{
            access.requireStaff();if(canonical()==null && !access.sameSchool(operation.getSchool())) denied();
            var prepared=new LinkedHashMap<Student,Integer>();var seen=new HashSet<String>();
            for(var request:requests) {
                if(request==null || request.getCurrencyTransferred()==null || request.getCurrencyTransferred()<=0)
                    throw new IllegalArgumentException("Redemption requires a positive whole amount");
                Student student=canonical()==null?students.findByStudentEmailIgnoreCase(request.getStudentEmail()):canonical().student(request.getStudentEmail());requireStudent(student);
                if(!seen.add(student.getStudentIdNumber())) throw new IllegalArgumentException("Duplicate student in redemption");
                if(balance(student.getCurrency())<request.getCurrencyTransferred()) conflict("Insufficient student balance");
                prepared.put(student,request.getCurrencyTransferred());
            }
            prepared.forEach((student,amount)->{
                changeStudent(student,-amount);operation.getStudentIds().add(student.getStudentIdNumber());
                operation.getAmounts().put(student.getStudentIdNumber(),-amount);
            });
        });
        var result=new ArrayList<Student>();
        for(String id:receipt.getStudentIds()) {
            var student=canonical()==null?students.findById(id).orElseThrow():canonical().one(id,Student.class);requireStudent(student);result.add(student);
        }
        return result;
    }
    /** Existing administrator action: reset this school's staff budgets to five, now audited and atomic. */
    public List<Employee> resetSchool(String school) {
        access.requireAdmin();if(canonical()==null){if(!access.sameSchool(school)) denied();}else canonical().label(school);
        var receipt=execute("RESET_STAFF_BUDGETS",school,operation->{
            access.requireAdmin();if(canonical()==null){if(!access.sameSchool(school)) denied();}else canonical().label(school);
            var staff=canonical()==null?employees.findBySchool(school):mongo.find(canonical().query(),Employee.class);batch(staff);
            for(var employee:staff) {
                if(employee.isArchived()) continue;
                int old=balance(employee.getCurrency());
                changeEmployee(employee,5-old);
                operation.getEmployeeIds().add(employee.getEmployeeId());
                operation.getAmounts().put(employee.getEmployeeId(),5-old);
            }
        });
        return receipt.getEmployeeIds().stream().map(id->canonical()==null?employees.findById(id).orElseThrow():canonical().one(id,Employee.class)).toList();
    }
    private void requireStudent(Student student) {
        if(student==null || student.isArchived()) denied();
        users.requireStaffInStudentSchool(users.getCurrentStaffEmail(),student);
        access.requireStudentAccess(student.getStudentEmail());
    }
    private int balance(Integer value) {
        if(value==null || value<0) conflict("Existing currency balance requires staff review; no automatic balance initialization");
        return value;
    }
    private void changeEmployee(Employee employee,int delta) {
        change(Employee.class,employee.getEmployeeId(),employee.getSchool(),balance(employee.getCurrency()),delta);
    }
    private void changeStudent(Student student,int delta) {
        change(Student.class,student.getStudentIdNumber(),student.getSchool(),balance(student.getCurrency()),delta);
    }
    private void change(Class<?> type,String id,String school,int old,int delta) {
        long next=(long)old+delta;if(next<0 || next>Integer.MAX_VALUE) conflict("Invalid resulting currency balance");
        var query=canonical()==null?Query.query(Criteria.where("_id").is(id).and("school").is(school).and("archived").ne(true).and("currency").is(old)):canonical().id(id).addCriteria(Criteria.where("archived").ne(true).and("currency").is(old));
        if(mongo.updateFirst(query,new Update().inc("currency",delta),type).getMatchedCount()!=1)
            conflict("Currency changed; retry the same action");
    }
    private static void batch(List<?> requests) {
        if(requests==null || requests.isEmpty() || requests.size()>200) throw new IllegalArgumentException("Currency batches require 1 to 200 entries");
    }
    private static void denied() {throw new org.springframework.security.access.AccessDeniedException("Access denied");}
    private static void conflict(String message) {throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
