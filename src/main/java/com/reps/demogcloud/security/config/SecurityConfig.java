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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
                                "/auth/refresh",
                                "/forgot-password",
                                "/reset-password",
                                "/oauth2/**",
                                "/login",
                                "/error",
                                "/stripe/v1/**",
                                "/school/v1/all",
                                "/school/v1/newSchool",
                                "/school/v1/search"
                        ).permitAll()
                        .requestMatchers("/users/v1/**", "/employees/v1/**").hasRole("ADMIN")
                        .requestMatchers("/DTO/v1/AdminOverviewData", "/DTO/v1/punishmentsDTO").hasRole("ADMIN")
                        .requestMatchers("/DTO/v1/TeacherOverviewData").hasAnyRole("TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers("/DTO/v1/StudentOverviewData", "/DTO/v1/StudentOverviewData/**")
                        .hasAnyRole("STUDENT", "TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers("/assignments/v1/bindings/by-template/**").hasRole("ADMIN")
                        .requestMatchers("/assignments/v1/bindings/**").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/assignments/v1/by-punishment/**",
                                "/assignments/v1/templates/for-punishment/**",
                                "/assignments/v1/*/assignment-template")
                        .hasAnyRole("STUDENT", "TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers("/assignments/v1/**").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/officeReferral/v1/**").hasAnyRole("TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers("/guidance/v1/**").hasAnyRole("TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers("/tracked-behaviors/v1/**").hasAnyRole("TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers("/email/v1/**").hasAnyRole("TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/student/v1/points/transfer").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/student/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/student/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/student/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/student/v1/**")
                        .hasAnyRole("STUDENT", "TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/punish/v1/punishments",
                                "/punish/v1/archived",
                                "/punish/v1/openPunishments",
                                "/punish/v1/punishStatus/**")
                        .hasAnyRole("TEACHER", "GUIDANCE", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/punish/v1/startPunish/form",
                                "/punish/v1/startPunish/formList")
                        .hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/punish/v1/archived/**",
                                "/punish/v1/rejected/**",
                                "/punish/v1/updates",
                                "/punish/v1/descriptions",
                                "/punish/v1/emails",
                                "/punish/v1/schoolName",
                                "/punish/v1/infractionName",
                                "/punish/v1/infractionLevel")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/punish/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/officeReferral/v1/startPunish/adminReferral")
                        .hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/officeReferral/v1/closeId",
                                "/officeReferral/v1/submit/**")
                        .hasAnyRole("GUIDANCE", "ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/officeReferral/v1/rejected/**",
                                "/officeReferral/v1/descriptions")
                        .hasAnyRole("GUIDANCE", "ADMIN")
                        .requestMatchers("/punish/v1/**").hasAnyRole("STUDENT", "TEACHER", "GUIDANCE", "ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
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
