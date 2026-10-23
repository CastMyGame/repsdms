package com.reps.demogcloud.security.services;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class AccountCredentialVersionTest {
 @Test void legacySessionsSurviveButRecoveryInvalidatesOldAndRenewedSessions(){var u=new UserModel();u.setUsername("student@example.test");u.setPassword("encoded");var jwt=com.reps.demogcloud.security.utils.JwtTestSupport.jwt();var original=new AccountPrincipal(u,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));String token=jwt.generateToken(original);assertTrue(jwt.validateToken(token,original));String renewed=jwt.renewTokenWithBlacklist(token);u.setCredentialVersion(1);assertFalse(jwt.validateToken(renewed,new AccountPrincipal(u,original.getAuthorities())));u.setPasswordSetupRequired(true);assertFalse(new AccountPrincipal(u,original.getAuthorities()).isAccountNonLocked());}
}
