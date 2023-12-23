package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.UserDto;
import com.reps.demogcloud.security.models.UserModel;
import org.springframework.stereotype.Service;

import java.util.List;



public interface UserService {
    UserModel save(UserDto user);

    List<UserModel> findAll();

    UserModel findOne(String username);

    UserModel createUser(UserDto user);
}
