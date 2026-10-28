package com.reps.demogcloud.data;

import java.util.List;

/** Root saves preserve server-owned metadata; batch atomicity belongs to the domain transaction. */
public interface TenantRootRepository<T> {
    <S extends T> S save(S entity);
    <S extends T> List<S> saveAll(Iterable<S> entities);
    void delete(T entity);
}
