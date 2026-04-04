package com.reps.demogcloud.security.config;

import com.reps.demogcloud.security.services.CustomOAuth2UserService;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final Environment env;

    // OAuth2 components for Google SSO
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtFilterRequest jwtFilterRequest) throws Exception {
        boolean ssoEnabled = Boolean.parseBoolean(env.getProperty("auth.sso.google.enabled", "false"));
        boolean ssoOnly = Boolean.parseBoolean(env.getProperty("auth.sso.only", "false"));

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/register",
                                "/contact-us",
                                "/auth",
                                "/forgot-password",
                                "/reset-password",
                                "/student/v1/points/transfer",
                                "/DTO/v1/**",
                                "/oauth2/**",
                                "/login",
                                "/error",
                                "/assignments/v1/templates",
                                "/stripe/v1/**",
                                "/school/v1/all",
                                "/school/v1/newSchool",
                                "/school/v1/search"
                        ).permitAll()
                        .anyRequest().authenticated()
                );

        // Keep traditional form login unless forcing SSO-only
        if (!ssoOnly) {
            http.formLogin(form -> form
                    .loginPage("/login")
                    .permitAll());
        } else {
            http.formLogin(AbstractHttpConfigurer::disable);
        }

        // Conditionally enable Google SSO
        if (ssoEnabled) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo
                            .oidcUserService(customOAuth2UserService))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler));
        } else {
            // Ensure oauth2 login is not active if disabled
            http.oauth2Login(AbstractHttpConfigurer::disable);
        }

        // JWT filter remains in place for API token protection
        http.addFilterBefore(jwtFilterRequest, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        // Origins must not include trailing slashes
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("https://reps-react-ui.vercel.app");
        config.addAllowedOrigin("https://repsdev.vercel.app");
        config.addAllowedOrigin("https://repsdiscipline.vercel.app");
        config.addAllowedOrigin("https://www.repsdiscipline.com");

        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}