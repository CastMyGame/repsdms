package com.reps.demogcloud.security.config;

import com.reps.demogcloud.security.controllers.AuthControllers;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.email.EmailRoutingService;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real signing, user-details loader, JWT filter and SecurityFilterChain. Persistence/providers mocked. */
@SpringJUnitWebConfig(JwtSessionSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false"})
class JwtSessionSecurityTest {
 @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
 @ComponentScan(basePackageClasses=SecurityConfig.class,useDefaultFilters=false,includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=SecurityConfig.class))
 @Import({JwtFilterRequest.class,CustomUserDetailsService.class,AuthControllers.class,GlobalExceptionHandler.class,Probe.class})
 static class App {
  @Bean JwtRevocationStore revocations(){return JwtTestSupport.store();}
  @Bean JwtUtils jwt(JwtRevocationStore store){return new JwtUtils(JwtTestSupport.environment(),store);}
  @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
 }
 @RestController static class Probe {
  @GetMapping("/session-test/staff") @PreAuthorize("hasRole('ADMIN')") public String staff(){return "allowed";}
  @GetMapping("/session-test/failure") public String failure(){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Synthetic conflict");}
 }
 @Autowired WebApplicationContext context;
 @Autowired JwtUtils jwt;
 @Autowired JwtRevocationStore revocations;
 @Autowired AuthenticationProvider provider;
 @Autowired CustomUserDetailsService details;
 @MockitoBean CustomOAuth2UserService oidc;
 @MockitoBean OAuth2LoginSuccessHandler success;
 @MockitoBean OAuth2LoginFailureHandler failure;
 @MockitoBean UserRepository users;
 @MockitoBean UserAccountService accounts;
 @MockitoBean PasswordEncoder encoder;
 @MockitoBean AccountAccessService setup;
 @MockitoBean PasswordResetTokenRepository resets;
 @MockitoBean EmailService mail;
 @MockitoBean GoogleOAuthTokenStore gmail;
 @MockitoBean UserContextService actor;
 @MockitoBean EmailRoutingService routing;
 MockMvc mvc;UserModel account;
 @BeforeEach void init(){
  mvc=MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
  account=new UserModel();account.setUsername("staff@example.test");account.setPassword("synthetic-encoded");account.setSchool("Testing");account.setRoles(Set.of(new RoleModel(null,"ADMIN")));
  when(users.findByUsername(account.getUsername())).thenReturn(account);
  when(accounts.loadUserModelByUsername(account.getUsername())).thenReturn(account);
  reset(provider);when(provider.supports(any())).thenReturn(true);when(provider.authenticate(any())).thenAnswer(i->new UsernamePasswordAuthenticationToken(details.loadUserByUsername(account.getUsername()),null,details.loadUserByUsername(account.getUsername()).getAuthorities()));
 }
 String token(){return jwt.generateToken(details.loadUserByUsername(account.getUsername()));}
 @Test void passwordLoginTokenAndRenewalUseSameStableInfrastructure()throws Exception{
  var result=mvc.perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"staff@example.test\",\"password\":\"synthetic-input\"}")).andExpect(status().isOk()).andReturn();
  String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("response").asText();
  assertTrue(new JwtUtils(JwtTestSupport.environment(),revocations).validateToken(token,details.loadUserByUsername(account.getUsername())));
  var renewed=mvc.perform(post("/v1/renew-token").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn();
  String next=new com.fasterxml.jackson.databind.ObjectMapper().readTree(renewed.getResponse().getContentAsString()).get("newToken").asText();assertNotEquals(token,next);
  mvc.perform(get("/session-test/staff").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  mvc.perform(get("/session-test/staff").header("Authorization","Bearer "+next)).andExpect(status().isOk());
 }
 @Test void persistedRoleChangesYield403RatherThanAnExpiredSession()throws Exception{
  String token=token();account.setRoles(Set.of(new RoleModel(null,"TEACHER")));
  mvc.perform(get("/session-test/staff").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  mvc.perform(post("/v1/renew-token").header("Authorization","Bearer "+token)).andExpect(status().isOk());
 }
 @Test void disabledPendingOrCredentialChangedAccountsCannotRefreshOrAccess()throws Exception{
  String token=token();account.setEnabled(false);
  mvc.perform(post("/v1/renew-token").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  account.setEnabled(true);account.setCredentialVersion(1);
  mvc.perform(get("/session-test/staff").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  account.setCredentialVersion(0);account.setPasswordSetupRequired(true);
  mvc.perform(post("/v1/renew-token").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void missingMalformedOrForeignSignedTokensGet401WithoutOAuthRedirect()throws Exception{
  mvc.perform(post("/v1/renew-token")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
  mvc.perform(get("/session-test/staff").header("Authorization","Bearer synthetic-invalid")).andExpect(status().isUnauthorized());
 }
 @Test void downstreamConflictIsNotTranslatedTo401()throws Exception{
  mvc.perform(get("/session-test/failure").header("Authorization","Bearer "+token())).andExpect(status().isConflict());
 }
 @Test void storeOutageFailsClosedWith503RatherThanSessionExpiry()throws Exception{
  String token=token();doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic failure")).when(revocations).contains(token);
  mvc.perform(get("/session-test/staff").header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable()).andExpect(content().string("Authentication temporarily unavailable"));
 }
}
