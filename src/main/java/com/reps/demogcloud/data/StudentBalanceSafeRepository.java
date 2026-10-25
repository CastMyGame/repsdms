package com.reps.demogcloud.data;
import com.reps.demogcloud.models.student.Student;
import java.util.List;
public interface StudentBalanceSafeRepository<T> {
    <S extends T> S save(S entity);
    <S extends T> List<S> saveAll(Iterable<S> entities);
}
