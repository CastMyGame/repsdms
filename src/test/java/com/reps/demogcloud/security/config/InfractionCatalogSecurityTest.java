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

/** Production security chain and catalog services. Only database/external dependencies mocked. */
@SpringJUnitWebConfig(InfractionCatalogSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false","reps.email.routing.phase1-enabled=false","reps.email.notifications.enabled=false"})
class InfractionCatalogSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses=SecurityConfig.class,useDefaultFilters=false,includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=SecurityConfig.class))
    @Import({InfractionController.class,InfractionService.class,com.reps.demogcloud.services.infraction.InfractionQueryService.class,
        com.reps.demogcloud.services.infraction.InfractionMutationService.class,JwtFilterRequest.class,GlobalExceptionHandler.class})
    static class App { @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);} }
    @Autowired WebApplicationContext context;
    @MockitoBean InfractionRepository infractions;
    @MockitoBean GoogleOAuthTokenStore google; @MockitoBean CustomOAuth2UserService oidc; @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure; @MockitoBean CustomUserDetailsService details; @MockitoBean JwtUtils jwt;
    MockMvc mvc;
    static final String ROOT="/infraction/v1",MESSAGE="Infraction catalog editing is unavailable for MVP 1.";
    static final String BODY="{\"infractionId\":\"existing\",\"infractionName\":\"Dress Code\",\"infractionLevel\":\"1\",\"school\":\"Forged\",\"schoolId\":\"forged\",\"roles\":[\"OPERATOR\"]}";
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var catalog=new com.reps.demogcloud.models.infraction.Infraction("existing","Dress Code","1");
        when(infractions.findAll()).thenReturn(List.of(catalog));when(infractions.findByInfractionId("existing")).thenReturn(catalog);
        when(infractions.findByInfractionName("Dress Code")).thenReturn(catalog);
    }
    @Test void anonymousReadsAndWritesRemainProtected() throws Exception {
        for(String path:List.of("/all","/infractionId/existing","/infractionName/Dress Code"))mvc.perform(get(ROOT+path)).andExpect(status().isUnauthorized());
        for(String method:List.of("POST","PUT","PATCH","DELETE"))mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),ROOT+"/createInfraction").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());verifyNoInteractions(infractions);
    }
    @Test void authorizedCatalogReadsPreserveDefinitionsAndSeverityForEveryRole() throws Exception {
        for(String role:List.of("STUDENT","TEACHER","GUIDANCE","ADMIN","PARENT")){
            mvc.perform(get(ROOT+"/all").with(user("actor@example.test").roles(role)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isAccepted()).andExpect(jsonPath("$[0].infractionId").value("existing")).andExpect(jsonPath("$[0].infractionLevel").value("1"));
            mvc.perform(get(ROOT+"/infractionId/existing").with(user("actor@example.test").roles(role)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isAccepted());
            mvc.perform(get(ROOT+"/infractionName/Dress Code").with(user("actor@example.test").roles(role)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isAccepted());
        }verify(infractions,never()).save(any());verify(infractions,never()).delete(any());
    }
    @Test void everyRoleAndForgedSchoolOwnershipCannotCreateOverwriteDeleteOrBulkEdit() throws Exception {
        for(String role:List.of("STUDENT","TEACHER","GUIDANCE","ADMIN","PARENT","OPERATOR"))
            for(String path:List.of("/createInfraction","/delete/infraction","/bulk","/update/existing","/legacy/create",""))
                for(String method:List.of("POST","PUT","PATCH","DELETE"))
                    mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),ROOT+path).with(user("actor@example.test").roles(role)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                        .andExpect(status().isMethodNotAllowed()).andExpect(content().string(MESSAGE));
        verifyNoInteractions(infractions);
    }
    @Test void existingPostAndDeleteServicesCannotBypassTheRestriction() {
        var mutations=context.getBean(com.reps.demogcloud.services.infraction.InfractionMutationService.class);
        var catalog=new com.reps.demogcloud.models.infraction.Infraction("existing","Dress Code","1");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->mutations.createNewInfraction(catalog));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->mutations.deleteInfraction(catalog));verifyNoInteractions(infractions);
    }
}
