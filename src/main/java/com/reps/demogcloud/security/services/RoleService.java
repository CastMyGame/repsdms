package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.RoleModel;

public interface RoleService {
    RoleModel findByRole(String role);
}
