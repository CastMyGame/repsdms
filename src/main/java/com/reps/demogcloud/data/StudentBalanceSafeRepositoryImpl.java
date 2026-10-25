package com.reps.demogcloud.data;
import com.reps.demogcloud.models.student.Student;
import org.springframework.data.mongodb.core.MongoTemplate;
import lombok.RequiredArgsConstructor;
import java.util.*;
@RequiredArgsConstructor
public class StudentBalanceSafeRepositoryImpl implements StudentBalanceSafeRepository<Student> {
    private final MongoTemplate mongo;
    @SuppressWarnings("unchecked") public <S extends Student> S save(S entity) {return (S)BalanceSafeSave.save(mongo,entity,Student.class);}
    public <S extends Student> List<S> saveAll(Iterable<S> entities) {var result=new ArrayList<S>();entities.forEach(e->result.add(save(e)));return result;}
}
