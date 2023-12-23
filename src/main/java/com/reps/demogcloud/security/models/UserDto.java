package com.reps.demogcloud.security.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private String id;
    //UserName is the email address
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String schoolName;
    @DBRef
    private Set<RoleModel> roles = new HashSet<>();

    public UserModel getUserFromDto(){
        UserModel user = new UserModel();
        user.setUsername(username);
        user.setPassword(password);
        user.setRoles(roles);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setSchoolName(schoolName);

        return user;
    }
}
