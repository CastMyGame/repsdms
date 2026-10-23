package com.reps.demogcloud.security.models;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
/** Versioned authentication without changing existing account roles or membership. */
public class AccountPrincipal extends User {
    public final long credentialVersion;
    public AccountPrincipal(UserModel model, Collection<? extends GrantedAuthority> roles) {
        super(model.getUsername(), model.getPassword(), model.isEnabled(), true, true, !model.isPasswordSetupRequired(), roles);
        credentialVersion=model.getCredentialVersion();
    }
}
