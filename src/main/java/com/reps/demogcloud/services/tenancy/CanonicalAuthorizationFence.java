package com.reps.demogcloud.services.tenancy;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Internal transaction fences, never authentication or migration authority. Unknown additive
 * fence counters are preserved by existing targeted/profile-save writers; balances are untouched. */
public final class CanonicalAuthorizationFence {
    private CanonicalAuthorizationFence() {}
    public static void actor(MongoTemplate mongo, CanonicalAccountContext.Actor actor) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;
        touch(mongo,actor.accountId(),actor.tenantSchoolId(),UserModel.class,"enabled");
        touch(mongo,actor.profileId(),actor.tenantSchoolId(),actor.roles().contains("STUDENT")?Student.class:Employee.class,"archived");
    }
    public static void student(MongoTemplate mongo,Student student,String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;
        if(student==null || !tenant.equals(student.getTenantSchoolId()))throw new AccessDeniedException("Canonical relationship is unavailable");
        touch(mongo,student.getStudentIdNumber(),tenant,Student.class,"archived");
        // Student disabled-state changes must serialize with a related case/currency write too.
        var users=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(tenant).and("username").regex(CanonicalWorkflowAccess.exact(student.getStudentEmail()))).limit(2),UserModel.class);
        if(users.size()!=1)throw new AccessDeniedException("Canonical relationship is unavailable");
        touch(mongo,users.get(0).getId(),tenant,UserModel.class,"enabled");
    }
    public static RuntimeException failure(RuntimeException failure){
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof com.mongodb.MongoException mongo){
            if(mongo.hasErrorLabel("UnknownTransactionCommitResult"))return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Action outcome requires review; reload before retrying");
            if(mongo.hasErrorLabel("TransientTransactionError"))return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Record or authorization changed concurrently; reload before retrying");
        }
        return failure;
    }
    private static void touch(MongoTemplate mongo,String id,String tenant,Class<?> type,String state){
        var criteria=Criteria.where("_id").is(id).and("tenantSchoolId").is(tenant);
        if("enabled".equals(state))criteria.and(state).ne(false);else criteria.and(state).ne(true);
        if(mongo.updateFirst(Query.query(criteria),new Update().inc("authorizationFence",1L),type).getMatchedCount()!=1)
            throw new AccessDeniedException("Canonical relationship is unavailable");
    }
}
