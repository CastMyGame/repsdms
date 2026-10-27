package com.reps.demogcloud.security.config;

import com.reps.demogcloud.controllers.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.employee.*;
import com.reps.demogcloud.services.student.*;
import com.reps.demogcloud.services.currency.*;
import com.reps.demogcloud.services.email.*;
import com.reps.demogcloud.utils.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real production filter chain, but an explicitly TEST-ONLY controller exercising the unwired A7 contract.
 * These tests do not assert that production endpoints have migrated to ID authorization. */
@SpringJUnitWebConfig(TenantPreparationSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false","reps.email.routing.phase1-enabled=false","reps.email.notifications.enabled=false"})
class TenantPreparationSecurityTest {
    record Actor(String tenantSchoolId,String role,String ownStudent,Set<String> assigned,boolean active) {}
    record Case(String id,String tenantSchoolId,String student,String displayName) {}
    interface Store { Actor actor(String username); Case record(String id); }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses=SecurityConfig.class,useDefaultFilters=false,includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=SecurityConfig.class))
    @Import({FixtureController.class,JwtFilterRequest.class,GlobalExceptionHandler.class,com.reps.demogcloud.services.tenancy.TenantPreparationGuard.class})
    static class App { @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);} @Bean Store store(){return mock(Store.class);} }
    @org.springframework.web.bind.annotation.RestController
    static class FixtureController {
        private final Store store;FixtureController(Store store){this.store=store;}
        @org.springframework.web.bind.annotation.GetMapping("/__test__/a7/records/{id}")
        public Case read(@org.springframework.web.bind.annotation.PathVariable String id) {
            var actor=store.actor(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName());
            var target=store.record(id);
            if(actor==null || !actor.active() || target==null)throw new org.springframework.security.access.AccessDeniedException("Access denied");
            var policy=com.reps.demogcloud.services.tenancy.CanonicalOwnership.class;
            com.reps.demogcloud.services.tenancy.CanonicalOwnership.requireSameTenant(new com.reps.demogcloud.services.tenancy.CanonicalOwnership.StoredOwner(actor.tenantSchoolId()),new com.reps.demogcloud.services.tenancy.CanonicalOwnership.StoredOwner(target.tenantSchoolId()),Set.of("school-A","school-B"));
            boolean allowed="ADMIN".equals(actor.role()) || "STUDENT".equals(actor.role()) && target.student().equals(actor.ownStudent())
                || Set.of("TEACHER","GUIDANCE").contains(actor.role()) && actor.assigned().contains(target.student());
            if(!allowed)throw new org.springframework.security.access.AccessDeniedException("Access denied");
            return target;
        }
    }
    @Autowired WebApplicationContext context;@Autowired Store store;
    @MockitoBean GoogleOAuthTokenStore google; @MockitoBean CustomOAuth2UserService oidc; @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure; @MockitoBean CustomUserDetailsService details; @MockitoBean JwtUtils jwt;
    MockMvc mvc;
    @BeforeEach void setup(){reset(store);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();when(store.record("A")).thenReturn(new Case("A","school-A","student-A","Same School"));when(store.record("B")).thenReturn(new Case("B","school-B","student-B","Same School"));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder read(String id){return get("/__test__/a7/records/"+id).with(user("actor").roles("ADMIN")).accept(MediaType.APPLICATION_JSON);}
    @Test void anonymousCannotReachFixture() throws Exception {mvc.perform(get("/__test__/a7/records/A")).andExpect(status().isUnauthorized());verifyNoInteractions(store);}
    @Test void everyRoleRemainsIsolatedWithIdenticalDisplayNames() throws Exception {
        for(String role:List.of("STUDENT","TEACHER","GUIDANCE","ADMIN")){
            when(store.actor("actor")).thenReturn(new Actor("school-A",role,"student-A",Set.of("student-A","student-B"),true));
            mvc.perform(read("A")).andExpect(status().isOk());mvc.perform(read("B")).andExpect(status().isForbidden());
        }
    }
    @Test void forgedTenantAndRoleClaimsDoNotChangePersistedFixtureOwner() throws Exception {
        when(store.actor("actor")).thenReturn(new Actor("school-A","TEACHER",null,Set.of("student-A"),true));
        mvc.perform(read("B").param("tenantSchoolId","school-B").param("school","Same School").header("X-School-Id","school-B")).andExpect(status().isForbidden());
        when(store.record("A")).thenReturn(new Case("A","school-A","unrelated","Same School"));mvc.perform(read("A")).andExpect(status().isForbidden());
    }
    @Test void missingUnknownConflictingAndDisabledFixtureMembershipFailsClosed() throws Exception {
        for(String id:new String[]{null,"unknown","school-a"," school-A"}){
            when(store.actor("actor")).thenReturn(new Actor(id,"ADMIN",null,Set.of(),true));mvc.perform(read("A")).andExpect(status().isForbidden());
        }
        when(store.actor("actor")).thenReturn(new Actor("school-A","ADMIN",null,Set.of(),false));mvc.perform(read("A")).andExpect(status().isForbidden());
        when(store.actor("actor")).thenReturn(new Actor("school-A","ADMIN",null,Set.of(),true));when(store.record("A")).thenReturn(new Case("A",null,"student-A","Same School"));mvc.perform(read("A")).andExpect(status().isForbidden());
    }
}
