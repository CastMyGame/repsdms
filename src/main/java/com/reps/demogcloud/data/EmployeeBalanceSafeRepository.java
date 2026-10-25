package com.reps.demogcloud.data;
import com.reps.demogcloud.models.employee.Employee;
import java.util.List;
public interface EmployeeBalanceSafeRepository<T> {
    <S extends T> S save(S entity);
    <S extends T> List<S> saveAll(Iterable<S> entities);
}
