package com.reps.demogcloud.security.services.serviceImpl;

import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.repository.RoleRepository;
import com.reps.demogcloud.security.services.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service(value = "roleService")
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public RoleModel findByRole(String name) {
        // Find role by name using the roleDao
        RoleModel role = roleRepository.findByRole(name);
        return role;
    }
}