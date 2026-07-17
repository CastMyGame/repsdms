package com.reps.demogcloud.security.config;

import com.reps.demogcloud.security.services.CustomOAuth2UserService;
import com.reps.demogcloud.security.services.CustomUserDetailsService;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.security.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = SecurityRouteAuthorizationTest.TestApplication.class,
        properties = {
                "auth.sso.google.enabled=false",
                "auth.sso.only=false"
        }
)
@AutoConfigureMockMvc
class SecurityRouteAuthorizationTest {

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedDashboardAndTemplateRoutesRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/DTO/v1/StudentOverviewData"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/assignments/v1/templates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshEndpointRemainsPublic() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanReachStudentDashboardButNotAdminDashboard() throws Exception {
        mockMvc.perform(get("/DTO/v1/StudentOverviewData"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/DTO/v1/AdminOverviewData"))
                .andExpect(status().isForbidden());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, TestRoutes.class})
    static class TestApplication {

        @Bean
        JwtFilterRequest jwtFilterRequest() {
            return new JwtFilterRequest(mock(JwtUtils.class), mock(CustomUserDetailsService.class));
        }

    }

    @RestController
    static class TestRoutes {
        @GetMapping("/DTO/v1/StudentOverviewData")
        ResponseEntity<Void> studentOverview() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/DTO/v1/AdminOverviewData")
        ResponseEntity<Void> adminOverview() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/assignments/v1/templates")
        ResponseEntity<Void> templates() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/auth/refresh")
        ResponseEntity<Void> refresh() {
            return ResponseEntity.ok().build();
        }
    }
}
