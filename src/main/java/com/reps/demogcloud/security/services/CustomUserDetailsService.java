package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserModel foundUser = userRepository.findByUsername(username);

        if (foundUser == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }

        if (!foundUser.isEnabled()) {
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }

        Collection<GrantedAuthority> authorities = new ArrayList<>();
        if (foundUser.getRoles() != null) {
            for (RoleModel role : foundUser.getRoles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRole()));
            }
        }

        return new User(
                foundUser.getUsername(),
                foundUser.getPassword(),
                true,
                true,
                true,
                true,
                authorities
        );
    }
}
