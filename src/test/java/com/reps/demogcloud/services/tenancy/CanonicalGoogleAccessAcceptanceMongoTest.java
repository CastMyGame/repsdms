package com.reps.demogcloud.services.tenancy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.mock.web.MockHttpSession;
import org.bson.Document;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@org.springframework.test.context.ContextConfiguration(initializers=CanonicalGoogleAccessAcceptanceMongoTest.GoogleInitializer.class,inheritInitializers=false)
class CanonicalGoogleAccessAcceptanceMongoTest extends CanonicalAcceptanceFixture {
 static class GoogleInitializer implements org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext>{
  public void initialize(org.springframework.context.ConfigurableApplicationContext context){new IsolatedCanonicalBootstrap().initialize(context);IsolatedCanonicalBootstrap.PROPERTIES.put("auth.sso.google.enabled","true");IsolatedCanonicalBootstrap.PROPERTIES.put("auth.sso.google.redirect-url","");}
 }
 @MockitoBean OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokens;
 @MockitoBean JwtDecoderFactory<ClientRegistration> decoders;
 @MockitoBean com.reps.demogcloud.security.services.CustomOAuth2UserService googleIdentity;
 @ParameterizedTest @ValueSource(strings={"valid","foreign-profile","disabled","unprovisioned"}) void actualGoogleFilterCallbackUsesPersistedCanonicalMembership(String mode)throws Exception{
  account("googlea","TEACHER","A",false);
  if(mode.equals("foreign-profile"))mongo.getCollection("employee").updateOne(new Document("_id","googlea"),new Document("$set",new Document("tenantSchoolId","school-B")));
  if(mode.equals("disabled"))mongo.getCollection("users").updateOne(new Document("_id","googlea"),new Document("$set",new Document("enabled",false)));
  if(mode.equals("unprovisioned"))mongo.getCollection("employee").deleteOne(new Document("_id","googlea"));
  var start=mvc.perform(get("/oauth2/authorization/google")).andReturn();assertEquals(302,start.getResponse().getStatus());String url=start.getResponse().getRedirectedUrl();assertNotNull(url);var query=org.springframework.web.util.UriComponentsBuilder.fromUriString(url).build().getQueryParams();String nonce=java.net.URLDecoder.decode(query.getFirst("nonce"),java.nio.charset.StandardCharsets.UTF_8),state=java.net.URLDecoder.decode(query.getFirst("state"),java.nio.charset.StandardCharsets.UTF_8);var now=java.time.Instant.now();
  var claims=new HashMap<String,Object>();claims.put("iss","https://accounts.google.com");claims.put("sub","synthetic-google-subject");claims.put("aud",List.of("synthetic-client"));claims.put("email","googlea@example.test");claims.put("email_verified",true);claims.put("nonce",nonce);claims.put("iat",now);claims.put("exp",now.plusSeconds(3600));
  Jwt decoded=Jwt.withTokenValue("synthetic-provider-id").header("alg","RS256").claims(c->c.putAll(claims)).build();when(decoders.createDecoder(any())).thenReturn(value->decoded);
  when(tokens.getTokenResponse(any())).thenReturn(OAuth2AccessTokenResponse.withToken("synthetic-google-access").tokenType(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER).expiresIn(3600).scopes(Set.of("openid","email","profile")).additionalParameters(Map.of("id_token","synthetic-provider-id")).build());
  when(googleIdentity.loadUser(any())).thenReturn(new DefaultOidcUser(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),new OidcIdToken("synthetic-provider-id",now,now.plusSeconds(3600),claims)));
  String before=snapshot();org.springframework.test.web.servlet.MvcResult result=null;Exception denied=null;
  try{result=mvc.perform(get("/login/oauth2/code/google").session((MockHttpSession)start.getRequest().getSession()).param("code","synthetic-code").param("state",state)).andReturn();}catch(Exception ex){denied=ex;}
  if(mode.equals("valid")){assertNull(denied);assertNotNull(result);success(result);String response=result.getResponse().getContentAsString();var tree=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);assertTrue(tree.has("response"),"Google login must produce a REPS credential");assertEquals("school-A",mongo.getCollection("users").find(new Document("_id","googlea")).first().getString("tenantSchoolId"));}
  else {if(result!=null)assertFalse(result.getResponse().getContentAsString().contains("eyJ"),"Invalid membership received JWT");assertTrue(denied!=null||result.getResponse().getStatus()>=300);assertEquals(before,snapshot(),"Rejected Google login changed canonical membership");}
  verify(tokens).getTokenResponse(any());verify(googleIdentity).loadUser(any());
  evidence("FILTER","/login/oauth2/code/google","actualGoogleFilterCallbackUsesPersistedCanonicalMembership","OAuth state + mocked provider identity; real success handler checks persisted membership","Actual filter callback produces JWT for matching profile","Disabled/missing/conflicting profile receives no JWT and no DB changes","Token exchange, JWT decoder and provider identity mocked; Google is never contacted");
 }
}
