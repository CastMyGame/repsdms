package com.reps.demogcloud.security.models;

import java.util.List;

/** Account changes must use existing fixed-field, authorized update operations, never replacement saves. */
public interface UserInsertOnlyRepository<T> {
    <S extends T> S save(S entity);
    <S extends T> List<S> saveAll(Iterable<S> entities);
}
