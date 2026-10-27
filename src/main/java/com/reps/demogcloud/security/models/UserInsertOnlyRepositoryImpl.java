package com.reps.demogcloud.security.models;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.ArrayList;
import java.util.List;

/** Current save callers create accounts only. An insert is race-safe and cannot replace a newer account. */
@RequiredArgsConstructor
public class UserInsertOnlyRepositoryImpl implements UserInsertOnlyRepository<UserModel> {
    private final MongoTemplate mongo;

    public <S extends UserModel> S save(S entity) {
        if (entity == null) throw new IllegalArgumentException("Account required");
        if (entity.getTenantSchoolId() != null) throw conflict();
        try { return mongo.insert(entity); }
        catch (DuplicateKeyException existing) { throw conflict(); }
    }

    public <S extends UserModel> List<S> saveAll(Iterable<S> entities) {
        var result=new ArrayList<S>();
        for (S entity:entities) result.add(save(entity));
        return result;
    }

    private static OptimisticLockingFailureException conflict() {
        return new OptimisticLockingFailureException("Account replacement or ownership assignment is unavailable. Reload and use the authorized account action.");
    }
}
