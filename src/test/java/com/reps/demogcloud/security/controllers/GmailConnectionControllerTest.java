package com.reps.demogcloud.security.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.services.email.EmailRoutingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class GmailConnectionControllerTest {
    @Mock GoogleOAuthTokenStore googleOAuthTokenStore;
    @Mock UserContextService userContextService;
    @Mock EmailRoutingService emailRoutingService;
    @Mock CustomUserDetailsService customUserDetailsService;
    @Mock UserRepository userRepository;
    @Mock UserAccountService userAccountService;
    @Spy JwtUtils jwtUtils = com.reps.demogcloud.security.utils.JwtTestSupport.jwt();
    @InjectMocks AuthControllers controller;
    private final UserDetails details=User.withUsername("staff@example.test").password("unused").roles("TEACHER").build();
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private MockHttpServletRequest request(String method) {
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer "+jwtUtils.generateToken(details,method));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities()));
        when(userContextService.getCurrentStaffEmail()).thenReturn("staff@example.test");
        when(customUserDetailsService.loadUserByUsername("staff@example.test")).thenReturn(details); return request;
    }
    @ParameterizedTest @ValueSource(strings={"PASSWORD","GOOGLE"})
    void eitherLoginMethodCanConnectWithoutChangingRepsIdentity(String loginMethod) {
        var request=request(loginMethod); var before=SecurityContextHolder.getContext().getAuthentication();
        when(googleOAuthTokenStore.beginConnection("staff@example.test","p".repeat(43))).thenReturn("https://accounts.google.com/?state=synthetic");
        var response=controller.connectGmail(request,Map.of("browserProof","p".repeat(43),"email","attacker@example.test"));
        assertEquals(200,response.getStatusCode().value());
        verify(googleOAuthTokenStore).beginConnection("staff@example.test","p".repeat(43));
        verifyNoInteractions(userRepository,userAccountService); assertSame(before,SecurityContextHolder.getContext().getAuthentication());
        assertFalse(Objects.toString(response.getBody()).contains("unused"));
    }
    @ParameterizedTest @ValueSource(strings={"PASSWORD","GOOGLE"})
    void capabilitiesDescribeActualAuthorizationRatherThanLoginMethod(String loginMethod) {
        when(userContextService.getCurrentStaffEmail()).thenReturn("staff@example.test");
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer "+jwtUtils.generateToken(details,loginMethod));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities()));
        when(googleOAuthTokenStore.hasUsableGmailSendToken("staff@example.test")).thenReturn(true);
        when(googleOAuthTokenStore.getConnectionState("staff@example.test")).thenReturn(GoogleOAuthTokenStore.ConnectionState.CONNECTED);
        when(emailRoutingService.isStaffGmailPolicyEnabled()).thenReturn(true);
        var capabilities=controller.getAuthCapabilities(request).getBody();
        assertNotNull(capabilities); assertTrue(capabilities.isCanSendAsUser()); assertEquals("GOOGLE_GMAIL",capabilities.getEmailProvider()); assertEquals(loginMethod,capabilities.getLoginMethod());
    }
    @Test void refreshFailureCapabilitiesBlockManagedGmailInsteadOfPromisingSmtp() {
        when(userContextService.getCurrentStaffEmail()).thenReturn("staff@example.test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities()));
        when(googleOAuthTokenStore.getConnectionState("staff@example.test")).thenReturn(GoogleOAuthTokenStore.ConnectionState.RECONNECT_REQUIRED);
        when(emailRoutingService.isStaffGmailPolicyEnabled()).thenReturn(true); when(emailRoutingService.isPhase1RoutingEnabled()).thenReturn(true);
        var capabilities=controller.getAuthCapabilities(new MockHttpServletRequest()).getBody();
        assertNotNull(capabilities); assertFalse(capabilities.isCanSendAsUser()); assertEquals("RECONNECT_REQUIRED",capabilities.getEmailProvider());
    }
    @Test void cookieOnlyOrMismatchedBearerIdentityCannotLink() {
        assertThrows(AccessDeniedException.class,()->controller.connectGmail(new MockHttpServletRequest(),Map.of("browserProof","p".repeat(43))));
        var request=request("GOOGLE"); request.removeHeader("Authorization");
        request.addHeader("Authorization","Bearer "+jwtUtils.generateToken(User.withUsername("other@example.test").password("unused").roles("TEACHER").build()));
        assertThrows(AccessDeniedException.class,()->controller.connectGmail(request,Map.of("browserProof","p".repeat(43)))); verifyNoInteractions(googleOAuthTokenStore);
    }
    @Test void blacklistedBearerCannotUseRememberedLoginSession() {
        var request=request("PASSWORD"); jwtUtils.blacklistToken(request.getHeader("Authorization").substring(7));
        assertThrows(AccessDeniedException.class,()->controller.gmailStatus(request)); verifyNoInteractions(googleOAuthTokenStore);
    }
    @Test void completionHasNoPublicAccountOrEmailParameter() {
        var request=request("PASSWORD"); when(googleOAuthTokenStore.status("staff@example.test")).thenReturn(Map.of("state","CONNECTED"));
        controller.completeGmail(request,Map.of("state","state","code","code","browserProof","proof","email","other@example.test"));
        verify(googleOAuthTokenStore).completeConnection("staff@example.test","state","code","proof"); verifyNoInteractions(userRepository,userAccountService);
    }
    @Test void revocationFailureIsReportedAfterLocalDisconnect() {
        var response=controller.disconnectGmail(request("PASSWORD"));
        assertEquals(200,response.getStatusCode().value()); assertTrue(response.getBody().toString().contains("not confirmed"));
        assertFalse(response.getBody().toString().contains("refresh_token"));
    }
    @Test void userSerializationDoesNotExposePasswordHash() {
        var user=new UserModel(); user.setUsername("staff@example.test"); user.setPassword("synthetic-password-hash"); user.setSchool("School");
        assertFalse(new ObjectMapper().valueToTree(user).toString().contains("synthetic-password-hash"));
        assertFalse(user.toString().contains("synthetic-password-hash")); assertEquals("School",user.getSchool());
    }
    @Test void bearerFilterReplacesRememberedGoogleSubjectWithoutChangingTheJwtLoginMethod() throws Exception {
        var custom=mock(CustomUserDetailsService.class); var filter=new JwtFilterRequest(jwtUtils,custom);
        String token=jwtUtils.generateToken(details,"GOOGLE"); var request=new MockHttpServletRequest("GET","/auth/gmail/status"); request.addHeader("Authorization","Bearer "+token);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("old-google-subject",null,List.of()));
        when(custom.loadUserByUsername("staff@example.test")).thenReturn(details);
        filter.doFilter(request,new MockHttpServletResponse(),(r,s)->assertEquals("staff@example.test",SecurityContextHolder.getContext().getAuthentication().getName()));
        assertEquals("GOOGLE",jwtUtils.extractLoginMethod(token));
    }
}
