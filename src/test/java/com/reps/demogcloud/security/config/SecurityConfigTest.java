package com.reps.demogcloud.security.config;

import com.reps.demogcloud.security.services.CustomOAuth2UserService;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecurityConfigTest {

    @Test
    void authenticationManager_shouldReturnAuthenticationManagerFromConfiguration() throws Exception {
        AuthenticationConfiguration authenticationConfiguration = mock(AuthenticationConfiguration.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(authenticationManager);

        SecurityConfig securityConfig = new SecurityConfig(
                mock(org.springframework.core.env.Environment.class),
                mock(CustomOAuth2UserService.class),
                mock(OAuth2LoginSuccessHandler.class),
                mock(OAuth2LoginFailureHandler.class)
        );

        AuthenticationManager result = securityConfig.authenticationManager(authenticationConfiguration);

        assertNotNull(result);
        assertSame(authenticationManager, result);
    }

    @Test
    void corsFilter_shouldRegisterExpectedCorsConfiguration() throws Exception {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(org.springframework.core.env.Environment.class),
                mock(CustomOAuth2UserService.class),
                mock(OAuth2LoginSuccessHandler.class),
                mock(OAuth2LoginFailureHandler.class)
        );

        CorsFilter corsFilter = securityConfig.corsFilter();

        assertNotNull(corsFilter);

        Field field = CorsFilter.class.getDeclaredField("configSource");
        field.setAccessible(true);
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) field.get(corsFilter);

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/anything"));

        assertNotNull(config);
        assertEquals(Boolean.TRUE, config.getAllowCredentials());

        assertNotNull(config.getAllowedOrigins());
        assertEquals(5, config.getAllowedOrigins().size());
        assertTrue(config.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(config.getAllowedOrigins().contains("https://reps-react-ui.vercel.app"));
        assertTrue(config.getAllowedOrigins().contains("https://repsdev.vercel.app"));
        assertTrue(config.getAllowedOrigins().contains("https://repsdiscipline.vercel.app"));
        assertTrue(config.getAllowedOrigins().contains("https://www.repsdiscipline.com"));

        assertNotNull(config.getAllowedHeaders());
        assertTrue(config.getAllowedHeaders().contains("*"));

        assertNotNull(config.getAllowedMethods());
        assertTrue(config.getAllowedMethods().contains("*"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SecurityConfig.class)
    static class TestApp {
    }
}

/**
 * ssoEnabled=false, ssoOnly=false
 * Expect: form login present, oauth2 login absent, jwt filter present
 */
@SpringBootTest(
        classes = SecurityConfigTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "auth.sso.google.enabled=false",
                "auth.sso.only=false"
        }
)
class SecurityConfig_FormLoginOnly_Test {

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @MockitoBean
    private JwtFilterRequest jwtFilterRequest;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

    @MockitoBean
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Test
    void securityFilterChain_shouldEnableFormLoginAndDisableOauth2() {
        List<Filter> filters = springSecurityFilterChain.getFilters("/private");

        assertNotNull(filters);
        assertTrue(filters.stream().anyMatch(UsernamePasswordAuthenticationFilter.class::isInstance));
        assertFalse(filters.stream().anyMatch(OAuth2LoginAuthenticationFilter.class::isInstance));
        assertTrue(filters.stream().anyMatch(JwtFilterRequest.class::isInstance));
    }
}

/**
 * ssoEnabled=true, ssoOnly=false
 * Expect: form login present, oauth2 login present, jwt filter present
 */
@SpringBootTest(
        classes = SecurityConfigTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "auth.sso.google.enabled=true",
                "auth.sso.only=false"
        }
)
class SecurityConfig_FormLoginAndOauth2_Test {

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @MockitoBean
    private JwtFilterRequest jwtFilterRequest;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

    @MockitoBean
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Test
    void securityFilterChain_shouldEnableFormLoginAndOauth2() {
        List<Filter> filters = springSecurityFilterChain.getFilters("/private");

        assertNotNull(filters);
        assertTrue(filters.stream().anyMatch(UsernamePasswordAuthenticationFilter.class::isInstance));
        assertTrue(filters.stream().anyMatch(OAuth2LoginAuthenticationFilter.class::isInstance));
        assertTrue(filters.stream().anyMatch(JwtFilterRequest.class::isInstance));
    }
}

/**
 * ssoEnabled=false, ssoOnly=true
 * Expect: form login absent, oauth2 login absent, jwt filter present
 */
@SpringBootTest(
        classes = SecurityConfigTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "auth.sso.google.enabled=false",
                "auth.sso.only=true"
        }
)
class SecurityConfig_JwtOnly_Test {

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @MockitoBean
    private JwtFilterRequest jwtFilterRequest;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

    @MockitoBean
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Test
    void securityFilterChain_shouldDisableFormLoginAndDisableOauth2() {
        List<Filter> filters = springSecurityFilterChain.getFilters("/private");

        assertNotNull(filters);
        assertFalse(filters.stream().anyMatch(UsernamePasswordAuthenticationFilter.class::isInstance));
        assertFalse(filters.stream().anyMatch(OAuth2LoginAuthenticationFilter.class::isInstance));
        assertTrue(filters.stream().anyMatch(JwtFilterRequest.class::isInstance));
    }
}

/**
 * ssoEnabled=true, ssoOnly=true
 * Expect: form login absent, oauth2 login present, jwt filter present
 */
@SpringBootTest(
        classes = SecurityConfigTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "auth.sso.google.enabled=true",
                "auth.sso.only=true"
        }
)
class SecurityConfig_Oauth2Only_Test {

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @MockitoBean
    private JwtFilterRequest jwtFilterRequest;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

    @MockitoBean
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Test
    void securityFilterChain_shouldDisableFormLoginAndEnableOauth2() {
        List<Filter> filters = springSecurityFilterChain.getFilters("/private");

        assertNotNull(filters);
        assertFalse(filters.stream().anyMatch(UsernamePasswordAuthenticationFilter.class::isInstance));
        assertTrue(filters.stream().anyMatch(OAuth2LoginAuthenticationFilter.class::isInstance));
        assertTrue(filters.stream().anyMatch(JwtFilterRequest.class::isInstance));
    }
}