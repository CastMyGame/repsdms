package com.reps.demogcloud.data;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;

@RequiredArgsConstructor
public class TenantRootRepositoryImpl<T> implements TenantRootRepository<T> {
    private final MongoTemplate mongo;
    public <S extends T> S save(S entity) { return TenantRootSave.save(mongo,entity); }
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        var result=new ArrayList<S>();for(S entity:entities)result.add(save(entity));return result;
    }
    public void delete(T entity){TenantRootSave.delete(mongo,entity);}
}
