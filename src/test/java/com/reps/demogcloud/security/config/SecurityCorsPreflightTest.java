package com.reps.demogcloud.security.config;

import com.reps.demogcloud.data.PasswordResetTokenRepository;
import com.reps.demogcloud.security.controllers.AuthControllers;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.EmailService;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.services.email.EmailRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC/security chain, without Boot configuration imports, databases or provider HTTP. */
@SpringJUnitWebConfig(SecurityCorsPreflightTest.App.class)
@TestPropertySource(properties = {"auth.sso.google.enabled=true", "auth.sso.only=false"})
class SecurityCorsPreflightTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @ComponentScan(basePackageClasses = SecurityConfig.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
    @Import({AuthControllers.class, JwtFilterRequest.class})
    static class App {
        @Bean AuthenticationProvider passwordProvider() { return mock(AuthenticationProvider.class); }
        @Bean ClientRegistrationRepository registrations() {
            var google = ClientRegistration.withRegistrationId("google").clientId("synthetic-client")
                    .clientSecret("synthetic-secret").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid", "email", "profile")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("http://127.0.0.1:1/token").userInfoUri("http://127.0.0.1:1/userinfo")
                    .jwkSetUri("http://127.0.0.1:1/jwks").userNameAttributeName("sub").clientName("Google").build();
            return new InMemoryClientRegistrationRepository(google);
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired AuthenticationProvider passwordProvider;
    @MockitoBean CustomOAuth2UserService oidc;
    @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure;
    @MockitoBean EmailService email;
    @MockitoBean UserAccountService accounts;
    @MockitoBean CustomUserDetailsService details;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean JwtUtils jwt;
    @MockitoBean UserRepository users;
    @MockitoBean PasswordResetTokenRepository resets;
    @MockitoBean GoogleOAuthTokenStore gmail;
    @MockitoBean UserContextService actor;
    @MockitoBean EmailRoutingService routing;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reset(passwordProvider);
        when(passwordProvider.supports(any())).thenReturn(true);
    }
    @Test void allowedPasswordPreflightReturnsCorsPermissionsWithoutRedirect() throws Exception {
        mvc.perform(options("/auth").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("content-type")));
        verifyNoInteractions(gmail, email, details);
    }
    @Test void deniedOriginCannotPreflightOrPostPasswordCredentials() throws Exception {
        mvc.perform(options("/auth").header("Origin", "https://untrusted.example.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/auth").header("Origin", "https://untrusted.example.test")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"teacher@example.test\",\"password\":\"synthetic\"}"))
                .andExpect(status().isForbidden());
        verify(passwordProvider, never()).authenticate(any());
    }
    @Test void allowedPasswordPostReachesExistingControllerAndAuthenticationManager() throws Exception {
        var teacher = User.withUsername("teacher@example.test").password("unused").roles("TEACHER").build();
        var model = new UserModel(); model.setUsername("teacher@example.test");
        when(passwordProvider.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(teacher, null, teacher.getAuthorities()));
        when(details.loadUserByUsername("teacher@example.test")).thenReturn(teacher);
        when(jwt.generateToken(teacher, "PASSWORD")).thenReturn("synthetic-jwt");
        when(accounts.loadUserModelByUsername("teacher@example.test")).thenReturn(model);
        mvc.perform(post("/auth").header("Origin", "http://localhost:3000").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher@example.test\",\"password\":\"synthetic\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.response").value("synthetic-jwt"))
                .andExpect(header().doesNotExist("Location"));
        verify(passwordProvider).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verifyNoInteractions(gmail);
    }
    @Test void protectedAndGmailRoutesRemainAuthenticatedWhileTheirPreflightsSucceed() throws Exception {
        for (var path : new String[]{"/private", "/auth/gmail/status", "/auth/gmail/connect", "/auth/gmail/complete", "/auth/gmail/disconnect"}) {
            mvc.perform(options(path).header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "authorization,content-type,x-reps-action-id"))
                    .andExpect(status().isOk()).andExpect(header().doesNotExist("Location"));
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertTrue(status >= 300 && status < 500, path + " must require authentication");
        }
        verifyNoInteractions(gmail);
    }
    @Test void rememberedLoginWithoutBearerCannotConnectGmail() throws Exception {
        mvc.perform(post("/auth/gmail/connect").with(user("teacher@example.test").roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"browserProof\":\"synthetic\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(gmail);
    }
    @Test void explicitGoogleLoginStillRedirectsToGoogle() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location", containsString("redirect_uri=http://localhost/login/oauth2/code/google")));
        verifyNoInteractions(gmail, oidc);
    }
}
