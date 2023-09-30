package com.reps.demogcloud.security.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.catalina.User;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResponse {
    private String response;
    private UserModel userModel;

}
