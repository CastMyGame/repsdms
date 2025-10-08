package com.reps.demogcloud.security.config;

import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.security.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

// Add your OAuth2 components:
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final UserService userService;
    private final JwtFilterRequest jwtFilterRequest;
    private final Environment env;

    // Provided elsewhere (you'll implement these two):
    private final OAuth2UserService<OidcUserRequest, OidcUser> customOAuth2UserService;
    private final AuthenticationSuccessHandler oAuth2LoginSuccessHandler;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        boolean ssoEnabled = Boolean.parseBoolean(env.getProperty("auth.sso.google.enabled", "false"));
        boolean ssoOnly   = Boolean.parseBoolean(env.getProperty("auth.sso.only", "false"));

        http.cors().and().csrf().disable()
                .authorizeRequests()
                .antMatchers(
                        "/register", "/contact-us", "/auth", "/forgot-password", "/reset-password",
                        "/student/v1/points/transfer", "/DTO/v1/**",
                        "/oauth2/**", "/login", "/error"
                ).permitAll()
                .anyRequest().authenticated()
                .and();

        // Keep traditional form login unless forcing SSO-only
        if (!ssoOnly) {
            http.formLogin()
                    .loginPage("/login")
                    .permitAll();
        } else {
            http.formLogin().disable();
        }

        // Conditionally enable Google SSO
        if (ssoEnabled) {
            http.oauth2Login()
                    .loginPage("/login") // reuse your login page
                    .userInfoEndpoint()
                    .oidcUserService(customOAuth2UserService)
                    .and()
                    .successHandler(oAuth2LoginSuccessHandler);
        } else {
            // Harden: if disabled, ensure oauth2 login is not active
            http.oauth2Login().disable();
        }

        // Your JWT filter remains in place (protects API calls with your token)
        http.addFilterBefore(jwtFilterRequest, UsernamePasswordAuthenticationFilter.class);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userService)
                .passwordEncoder(passwordEncoder());
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // ⚠️ Origins must NOT include trailing slashes; keep them as pure origins.
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("https://reps-react-ui.vercel.app");
        config.addAllowedOrigin("https://repsdev.vercel.app");
        config.addAllowedOrigin("https://repsdiscipline.vercel.app");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
