package com.reps.demogcloud.data;
import com.reps.demogcloud.models.employee.Employee;
import org.springframework.data.mongodb.core.MongoTemplate;
import lombok.RequiredArgsConstructor;
import java.util.*;
@RequiredArgsConstructor
public class EmployeeBalanceSafeRepositoryImpl implements EmployeeBalanceSafeRepository<Employee> {
    private final MongoTemplate mongo;
    @SuppressWarnings("unchecked") public <S extends Employee> S save(S entity) {return (S)BalanceSafeSave.save(mongo,entity,Employee.class);}
    public <S extends Employee> List<S> saveAll(Iterable<S> entities) {var result=new ArrayList<S>();entities.forEach(e->result.add(save(e)));return result;}
}
