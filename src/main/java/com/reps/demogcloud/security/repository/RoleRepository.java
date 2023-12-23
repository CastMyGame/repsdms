package com.reps.demogcloud.security.repository;

import com.reps.demogcloud.security.models.RoleModel;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RoleRepository extends MongoRepository<RoleModel, String> {
    RoleModel findByRole(String role);
}
