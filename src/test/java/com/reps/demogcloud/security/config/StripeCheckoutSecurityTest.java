package com.reps.demogcloud.security.config;

import com.reps.demogcloud.security.controllers.StripeController;
import com.reps.demogcloud.security.models.stripe.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.services.stripe.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual security chain and Stripe signature verification; no SDK network requests. */
@SpringJUnitWebConfig(StripeCheckoutSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false","stripe.webhook.secret=whsec_synthetic_test_fixture_only"})
class StripeCheckoutSecurityTest {
    static final String FIXTURE="whsec_synthetic_test_fixture_only";
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,JwtFilterRequest.class,StripeController.class})
    static class App {
        @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
        @Bean StripeWebhookService webhooks(Environment env,StripeEventLogRepository events,StripeProvisioningService provisioning,RegistrationIntentRepository intents){return new StripeWebhookService(env,events,provisioning,intents);}
    }
    @Autowired WebApplicationContext context;
    @MockitoBean GoogleOAuthTokenStore google; @MockitoBean CustomOAuth2UserService oidc;
    @MockitoBean OAuth2LoginSuccessHandler success; @MockitoBean OAuth2LoginFailureHandler failure;
    @MockitoBean CustomUserDetailsService details; @MockitoBean JwtUtils jwt;
    @MockitoBean StripeBillingService billing; @MockitoBean StripeProvisioningService provisioning;
    @MockitoBean StripeEventLogRepository events; @MockitoBean RegistrationIntentRepository intents;
    MockMvc mvc;Set<String> processed;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();processed=new HashSet<>();
        when(events.existsById(anyString())).thenAnswer(call->processed.contains(call.getArgument(0)));
        when(events.insert(any(StripeEventLog.class))).thenAnswer(call->{StripeEventLog event=call.getArgument(0);processed.add(event.getId());return event;});
        var intent=RegistrationIntent.builder().id("synthetic-intent").status("COMPLETED").build();when(intents.findById("synthetic-intent")).thenReturn(Optional.of(intent));
    }
    @Test void checkoutUnavailableForAnonymousAndAllRolesWithoutCallingService()throws Exception{
        var path="/stripe/v1/create-checkout-session";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value(StripeBillingService.CHECKOUT_UNAVAILABLE));
        for(String role:List.of("STUDENT","TEACHER","GUIDANCE","ADMIN","PARENT","OPERATOR"))
            mvc.perform(post(path).with(user("synthetic@example.test").roles(role)).contentType(MediaType.APPLICATION_JSON).content("{\"schoolIdNumber\":\"forged\"}"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.url").isEmpty());
        verifyNoInteractions(billing,provisioning,intents,events);
    }
    String event(){return "{\"id\":\"evt_synthetic\",\"object\":\"event\",\"type\":\"checkout.session.completed\",\"api_version\":\""+com.stripe.Stripe.API_VERSION+"\",\"data\":{\"object\":{\"id\":\"cs_synthetic\",\"object\":\"checkout.session\",\"customer\":\"cus_synthetic\",\"subscription\":\"sub_synthetic\",\"metadata\":{\"registrationIntentId\":\"synthetic-intent\"}}}}";}
    String sign(String body)throws Exception{
        long timestamp=Instant.now().getEpochSecond();var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(FIXTURE.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void signedWebhookStillPassesVerificationAndDuplicateDoesNotProvisionAgain()throws Exception{
        String body=event(),signature=sign(body);
        try(var sdk=mockStatic(Session.class)){
            for(int i=0;i<2;i++)mvc.perform(post("/stripe/v1/webhook").header("Stripe-Signature",signature).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
            sdk.verifyNoInteractions();
        }
        verify(provisioning,times(1)).provisionTeacherFromCheckoutSession(any());verify(events,times(1)).insert(any(StripeEventLog.class));verifyNoInteractions(billing);
    }
    @Test void invalidSignatureCannotReachBillingOrProvisioning()throws Exception{
        mvc.perform(post("/stripe/v1/webhook").header("Stripe-Signature","t=1,v1=invalid").contentType(MediaType.APPLICATION_JSON).content(event())).andExpect(status().isBadRequest());verifyNoInteractions(billing,provisioning,intents,events);
    }
}
