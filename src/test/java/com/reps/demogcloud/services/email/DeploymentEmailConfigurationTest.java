package com.reps.demogcloud.services.email;

import com.google.cloud.secretmanager.v1.*;
import com.google.cloud.spring.autoconfigure.secretmanager.GcpSecretManagerEnvironmentPostProcessor;
import com.google.cloud.spring.secretmanager.SecretManagerPropertySource;
import com.google.cloud.spring.secretmanager.SecretManagerTemplate;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.*;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring property resolution and OAuth redirect filter; all provider I/O is mocked. */
class DeploymentEmailConfigurationTest {
    private Map<String,Object> deploymentVariables() throws Exception {
        try (var input = Files.newInputStream(Path.of("app.yaml"))) {
            Map<String,Object> yaml = new Yaml().load(input);
            return (Map<String,Object>) yaml.get("env_variables");
        }
    }
    private Properties profile(String name) throws Exception {
        var values = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application-" + name + ".properties"))) {
            values.load(input);
        }
        return values;
    }
    private StandardEnvironment environment(Map<String,Object> vars) {
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource("systemEnvironment", vars));
        ConfigurationPropertySources.attach(env);
        return env;
    }
    @Test void deploymentFlagsBindAndDispatchStaysOffUntilManualPrerequisitesPass() throws Exception {
        var env = environment(deploymentVariables());
        var router = new EmailRoutingService(env, null, null, null, null);
        var notifications = new RequiredNotificationService(null, env, null, null, router, null);
        assertTrue(router.isPhase1RoutingEnabled());
        assertTrue(notifications.isEnabled());
        assertTrue(new com.reps.demogcloud.security.services.GoogleOAuthTokenStore(env, null, Optional.empty(), null).isConnectionEnabled());
        assertEquals("false", env.getProperty("reps.email.notifications.dispatch-enabled"));
        notifications.dispatchPending(); // No Mongo or transport object exists; disabled dispatch must not access either.
        assertEquals("https://repsdev.vercel.app/account/gmail/callback", env.getProperty("reps.email.gmail.redirect-uri"));
        assertEquals("http://localhost:3000/account/gmail/callback", profile("email-local").getProperty("reps.email.gmail.redirect-uri"));
        assertNull(profile("prod").getProperty("reps.email.notifications.dispatch-enabled"));
    }
    @Test void literalEnvironmentReferenceResolvesThroughInstalledSecretManagerIntegration() throws Exception {
        var vars = deploymentVariables();
        assertEquals("${sm@reps-dev-gmail-encryption-key}", vars.get("REPS_EMAIL_GMAIL_ENCRYPTION_KEY"));
        var env = environment(vars);
        new GcpSecretManagerEnvironmentPostProcessor().postProcessEnvironment(env, null);
        // Generate a transient synthetic key, never print it or contact ADC/Secret Manager.
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String fixture = Base64.getEncoder().encodeToString(bytes);
        var client = mock(SecretManagerServiceClient.class);
        when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(AccessSecretVersionResponse.newBuilder()
            .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(fixture))).build());
        var template = new SecretManagerTemplate(client, () -> "repsdev31");
        env.getPropertySources().addLast(new SecretManagerPropertySource("mocked-secret-manager", template, () -> "repsdev31"));
        assertTrue(fixture.equals(env.getProperty("reps.email.gmail.encryption-key")), "Secret reference must resolve to the key payload");
        verify(client).accessSecretVersion(SecretVersionName.of("repsdev31", "reps-dev-gmail-encryption-key", "latest"));
        env.getPropertySources().addFirst(new MapPropertySource("simulated-command-line", Map.of("reps.email.notifications.dispatch-enabled", "true")));
        assertEquals("true", env.getProperty("reps.email.notifications.dispatch-enabled")); // Higher-priority overrides remain operator-controlled.
    }
    @Test void googleLoginUsesBackendHttpsCallbackAndNeverAcceptsRequestRedirectOverride() throws Exception {
        String callback = environment(deploymentVariables()).getProperty("spring.security.oauth2.client.registration.google.redirect-uri");
        assertEquals("https://repsdev31.ue.r.appspot.com/login/oauth2/code/google", callback);
        assertNull(profile("dev").getProperty("spring.security.oauth2.client.registration.google.redirect-uri")); // Local dev retains Spring's request-derived callback.
        assertEquals("http://localhost:8080/login/oauth2/code/google", profile("email-local").getProperty("spring.security.oauth2.client.registration.google.redirect-uri"));
        assertEquals("https://repsdev.vercel.app", profile("dev").getProperty("auth.sso.google.redirect-url"));
        assertEquals("openid,email,profile", profile("dev").getProperty("spring.security.oauth2.client.registration.google.scope"));
        var google = ClientRegistration.withRegistrationId("google").clientId("synthetic-client").clientSecret("synthetic-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(callback).scope("openid", "email", "profile")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").tokenUri("http://127.0.0.1:1/token")
            .userInfoUri("http://127.0.0.1:1/userinfo").userNameAttributeName("sub").build();
        var filter = new OAuth2AuthorizationRequestRedirectFilter(new InMemoryClientRegistrationRepository(google));
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        request.addParameter("redirect_uri", "https://untrusted.example.test");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        String location = response.getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.contains("redirect_uri=" + callback));
        assertFalse(location.contains("untrusted.example.test"));
        assertTrue(location.contains("state="));
    }
}
