package com.reps.demogcloud.security.models;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RoleRepository extends MongoRepository<RoleModel, String> {
    RoleModel findByRole(String role);
}
