package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomOAuth2UserService extends OidcUserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // First, load the user from Google
        OidcUser oidcUser = super.loadUser(userRequest);
        
        // Extract email from Google OAuth response
        String email = oidcUser.getEmail();
        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("Email not provided by Google");
        }

        // Normalize email to lowercase (matching your username convention)
        email = email.toLowerCase();

        // Check if user exists in your database
        UserModel userModel = userRepository.findByUsername(email);
        
        if (userModel == null) {
            throw new OAuth2AuthenticationException("User not authorized to use this application. Please contact your administrator.");
        }

        // Check if user is enabled/active
        if (!userModel.isEnabled()) {
            throw new OAuth2AuthenticationException("Your account is not authorized to use this application. Please contact your administrator.");
        }

        // User is valid and enabled, return the OidcUser
        // The OidcUser will be used by Spring Security for authentication
        return oidcUser;
    }
}

