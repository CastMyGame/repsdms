package com.reps.demogcloud.security.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.util.UriComponentsBuilder;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

/** Stateful mocked Mongo persistence, intercepted Google HTTP; no Spring context, sockets, or real credentials. */
class GoogleOAuthTokenStoreTest {
    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000/account/gmail/callback", "https://repsdev.vercel.app/account/gmail/callback"})
    void connectUsesFixedEnvironmentCallbackAndSeparateSendScope(String callback) {
        env.setProperty("reps.email.gmail.redirect-uri", callback);
        String authorization = store.beginConnection(EMAIL, PROOF);
        var query = UriComponentsBuilder.fromUriString(authorization).build().getQueryParams();
        assertEquals(callback, java.net.URLDecoder.decode(query.getFirst("redirect_uri"), StandardCharsets.UTF_8));
        assertTrue(java.net.URLDecoder.decode(query.getFirst("scope"), StandardCharsets.UTF_8).contains(GoogleOAuthTokenStore.GMAIL_SCOPE));
        assertNotNull(query.getFirst("state"));
        server.verify(); // Beginning consent makes no Google HTTP request.
    }
    private static final String EMAIL = "teacher@example.test", PROOF = "p".repeat(43), COLLECTION = "oauth2_authorized_clients";
    private final byte[] key = new byte[32];
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MockEnvironment env = new MockEnvironment();
    private Document database;
    private GoogleOAuthTokenStore store;
    private MockRestServiceServer server;
    private org.springframework.web.client.RestTemplate http;
    private Optional<ClientRegistrationRepository> registrations;

    @BeforeEach void setup() {
        new java.security.SecureRandom().nextBytes(key);
        env.withProperty("reps.email.gmail.connection-enabled", "true")
                .withProperty("reps.email.gmail.encryption-key", Base64.getEncoder().encodeToString(key))
                .withProperty("reps.email.gmail.redirect-uri", "http://localhost:3000/account/gmail/callback");
        var registration = ClientRegistration.withRegistrationId("google").clientId("synthetic-client").clientSecret("synthetic-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/unused")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").tokenUri("https://oauth2.googleapis.com/token").build();
        registrations = Optional.of(new InMemoryClientRegistrationRepository(registration));
        http = GoogleOAuthTokenStore.googleHttp(); server = MockRestServiceServer.bindTo(http).build();
        store = new GoogleOAuthTokenStore(env, mongo, registrations, http);
        when(mongo.findOne(any(Query.class), eq(Document.class), eq(COLLECTION))).thenAnswer(i -> matches(i.getArgument(0)) ? cloneDoc(database) : null);
        when(mongo.upsert(any(Query.class), any(Update.class), eq(COLLECTION))).thenAnswer(i -> {
            if (database == null) database = new Document("_id", EMAIL); apply(i.getArgument(1)); return UpdateResult.acknowledged(1, 1L, null);
        });
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION))).thenAnswer(i -> {
            boolean match = matches(i.getArgument(0)); if (match) apply(i.getArgument(1)); return UpdateResult.acknowledged(match ? 1 : 0, match ? 1L : 0L, null);
        });
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Document.class), eq(COLLECTION))).thenAnswer(i -> {
            if (!matches(i.getArgument(0))) {
                if (database == null && ((FindAndModifyOptions)i.getArgument(2)).isUpsert()) { database = new Document("_id", EMAIL); apply(i.getArgument(1)); }
                return null;
            }
            Document before = cloneDoc(database); apply(i.getArgument(1)); return before;
        });
    }
    private Document cloneDoc(Document value) { return value == null ? null : Document.parse(value.toJson()); }
    private Object field(Document document, String path) {
        Object value = document; for (String part : path.split("\\.")) { if (!(value instanceof Document doc)) return null; value = doc.get(part); } return value;
    }
    private boolean matches(Query query) {
        if (database == null) return false;
        return query.getQueryObject().entrySet().stream().allMatch(entry -> {
            Object actual = field(database, entry.getKey()), expected = entry.getValue();
            if (expected instanceof Document op && op.containsKey("$gt")) return actual instanceof Date d && d.after((Date)op.get("$gt"));
            return Objects.equals(actual, expected);
        });
    }
    private void apply(Update update) {
        for (var operation : update.getUpdateObject().entrySet()) {
            for (var entry : ((Document)operation.getValue()).entrySet()) {
                String[] parts = entry.getKey().split("\\."); Document target = database;
                for (int j=0; j<parts.length-1; j++) { if (!(target.get(parts[j]) instanceof Document)) target.put(parts[j], new Document()); target=target.get(parts[j], Document.class); }
                if (operation.getKey().equals("$unset")) target.remove(parts[parts.length-1]); else target.put(parts[parts.length-1], entry.getValue());
            }
        }
    }
    private String begin() { return UriComponentsBuilder.fromUriString(store.beginConnection(EMAIL, PROOF)).build().getQueryParams().getFirst("state"); }
    private void expectConnection(String email, boolean verified, String scope, int expires) {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(content().string(containsString("code_verifier=")))
                .andRespond(withSuccess("{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"token_type\":\"Bearer\",\"expires_in\":"+expires+",\"scope\":\""+scope+"\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(header("Authorization", "Bearer synthetic-access"))
                .andRespond(withSuccess("{\"sub\":\"google-subject\",\"email\":\""+email+"\",\"email_verified\":"+verified+"}", MediaType.APPLICATION_JSON));
    }
    private String connect() { String state=begin(); expectConnection(EMAIL,true,"openid email "+GoogleOAuthTokenStore.GMAIL_SCOPE,3600); store.completeConnection(EMAIL,state,"synthetic-code",PROOF); server.verify(); server.reset(); return state; }
    private void expireAccess() throws Exception {
        Document record=database.get("gmailAuthorization",Document.class); String[] pieces=record.getString("cipher").split("\\.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(pieces[0])));
        cipher.updateAAD(EMAIL.getBytes(StandardCharsets.UTF_8));
        Map<String,Object> payload=new ObjectMapper().readValue(cipher.doFinal(Base64.getDecoder().decode(pieces[1])),Map.class);
        payload.put("issued",Instant.now().minusSeconds(7200).toString()); payload.put("expires",Instant.now().minusSeconds(10).toString());
        byte[] nonce=new byte[12]; new java.security.SecureRandom().nextBytes(nonce);
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce)); cipher.updateAAD(EMAIL.getBytes(StandardCharsets.UTF_8));
        record.put("cipher",Base64.getEncoder().encodeToString(nonce)+"."+Base64.getEncoder().encodeToString(cipher.doFinal(new ObjectMapper().writeValueAsBytes(payload))));
    }
    @Test void encryptedGrantSurvivesServiceRestartWithoutCompetingMemoryAuthority() {
        connect(); String stored=database.toJson();
        assertFalse(stored.contains("synthetic-access")); assertFalse(stored.contains("synthetic-refresh")); assertFalse(stored.contains("synthetic-secret"));
        var restarted=new GoogleOAuthTokenStore(env,mongo,registrations,http);
        assertEquals("synthetic-access",restarted.getToken(EMAIL).orElseThrow().getAccessToken());
        assertEquals(GoogleOAuthTokenStore.ConnectionState.CONNECTED,restarted.getConnectionState(EMAIL));
        assertNotNull(restarted.loadAuthorizedClient("reps-gmail",EMAIL)); assertNull(restarted.loadAuthorizedClient("google",EMAIL));
        assertFalse(new ObjectMapper().valueToTree(restarted.status(EMAIL)).toString().contains("synthetic-"));
        assertFalse(restarted.getToken(EMAIL).orElseThrow().toString().contains("synthetic-"));
    }
    @Test void expiredTokenRefreshesAndRetainsRefreshTokenWhenGoogleOmitsReplacement() throws Exception {
        connect(); expireAccess();
        server.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(content().string(containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("{\"access_token\":\"renewed-access\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\""+GoogleOAuthTokenStore.GMAIL_SCOPE+"\"}",MediaType.APPLICATION_JSON));
        var token=store.getToken(EMAIL).orElseThrow(); assertEquals("renewed-access",token.getAccessToken()); assertEquals("synthetic-refresh",token.getRefreshToken());
        assertEquals("renewed-access",new GoogleOAuthTokenStore(env,mongo,registrations,http).getToken(EMAIL).orElseThrow().getAccessToken()); server.verify();
    }
    @Test void refreshFailureRequiresReconnectAndNeverTriesSmtp() throws Exception {
        connect(); expireAccess(); server.expect(anything()).andRespond(withBadRequest().body("{\"error\":\"invalid_grant\",\"error_description\":\"sensitive-provider-body\"}").contentType(MediaType.APPLICATION_JSON));
        assertTrue(store.getToken(EMAIL).isEmpty()); assertEquals(GoogleOAuthTokenStore.ConnectionState.RECONNECT_REQUIRED,store.getConnectionState(EMAIL));
        assertFalse(store.status(EMAIL).toString().contains("sensitive")); assertTrue(store.getToken(EMAIL).isEmpty()); server.verify();
    }
    @Test void disconnectDeletesCredentialsBeforeRevocationAndReconnectionWorks() {
        connect(); server.expect(requestTo("https://oauth2.googleapis.com/revoke")).andExpect(request -> {
            assertEquals("DISCONNECTED",database.get("gmailAuthorization",Document.class).getString("state"));
            assertFalse(database.toJson().contains("cipher"));
        }).andRespond(withSuccess("",MediaType.TEXT_PLAIN));
        assertTrue(store.disconnect(EMAIL)); assertTrue(store.getToken(EMAIL).isEmpty()); server.verify(); server.reset();
        connect(); assertTrue(store.hasUsableGmailSendToken(EMAIL));
    }
    @Test void failedRevocationDoesNotRestoreLocalCredentials() {
        connect(); server.expect(anything()).andRespond(withServerError());
        assertFalse(store.disconnect(EMAIL)); assertTrue(store.getToken(EMAIL).isEmpty());
        assertEquals(GoogleOAuthTokenStore.ConnectionState.DISCONNECTED,store.getConnectionState(EMAIL)); server.verify();
    }
    @Test void incorrectOrUnverifiedGoogleIdentityCannotLink() {
        String wrongState=begin(); expectConnection("attacker@example.test",true,GoogleOAuthTokenStore.GMAIL_SCOPE,3600);
        assertThrows(AccessDeniedException.class,()->store.completeConnection(EMAIL,wrongState,"code",PROOF)); assertTrue(store.getToken(EMAIL).isEmpty()); server.verify(); server.reset();
        final String unverifiedState=begin(); expectConnection(EMAIL,false,GoogleOAuthTokenStore.GMAIL_SCOPE,3600);
        assertThrows(AccessDeniedException.class,()->store.completeConnection(EMAIL,unverifiedState,"code",PROOF)); server.verify();
    }
    @Test void missingSendScopeCannotBecomeConnected() {
        String state=begin(); expectConnection(EMAIL,true,"openid email",3600);
        assertThrows(IllegalStateException.class,()->store.completeConnection(EMAIL,state,"code",PROOF)); assertTrue(store.getToken(EMAIL).isEmpty()); server.verify();
    }
    @Test void tamperedReplayedExpiredAndOtherAccountCallbacksMakeNoGoogleRequest() {
        String state=begin();
        assertThrows(AccessDeniedException.class,()->store.completeConnection(EMAIL,"tampered","code",PROOF));
        assertThrows(AccessDeniedException.class,()->store.completeConnection(EMAIL,state,"code","x".repeat(43)));
        assertThrows(AccessDeniedException.class,()->store.completeConnection("other@example.test",state,"code",PROOF));
        database.get("gmailAuthorization",Document.class).get("pending",Document.class).put("expiresAt",Date.from(Instant.now().minusSeconds(1)));
        assertThrows(AccessDeniedException.class,()->store.completeConnection(EMAIL,state,"code",PROOF));
        server.verify(); server.reset(); String completed=connect();
        assertThrows(AccessDeniedException.class,()->store.completeConnection(EMAIL,completed,"code",PROOF)); server.verify();
    }
    @Test void noRecordMeansNeverConnectedAndDisabledFeatureDoesNotTouchPersistence() {
        assertEquals(GoogleOAuthTokenStore.ConnectionState.NEVER_CONNECTED,store.getConnectionState(EMAIL));
        assertFalse(store.hasUsableGmailSendToken(EMAIL));
        clearInvocations(mongo); env.setProperty("reps.email.gmail.connection-enabled","false");
        assertTrue(store.getToken(EMAIL).isEmpty()); assertEquals(false,store.status(EMAIL).get("available")); verifyNoInteractions(mongo);
    }
    @Test void missingEncryptionKeyStopsBeforePersistenceOrHttp() {
        env.setProperty("reps.email.gmail.encryption-key",""); clearInvocations(mongo);
        assertThrows(IllegalStateException.class,()->store.beginConnection(EMAIL,PROOF)); verifyNoInteractions(mongo); server.verify();
    }
    @Test void copiedCiphertextCannotBeUsedForAnotherRepsAccount() {
        connect(); database.put("_id","other@example.test");
        assertTrue(store.getToken("other@example.test").isEmpty()); server.verify();
    }
    @Test void disconnectedStateCannotBeResurrectedByRefreshRace() throws Exception {
        connect(); expireAccess(); server.expect(anything()).andRespond(request -> {
            Document record=database.get("gmailAuthorization",Document.class); record.put("state","DISCONNECTED"); record.put("version","disconnect-version"); record.remove("cipher");
            return withSuccess("{\"access_token\":\"renewed-access\",\"token_type\":\"Bearer\",\"expires_in\":3600}",MediaType.APPLICATION_JSON).createResponse(request);
        });
        assertTrue(store.getToken(EMAIL).isEmpty()); assertEquals(GoogleOAuthTokenStore.ConnectionState.DISCONNECTED,store.getConnectionState(EMAIL)); server.verify();
    }
    @Test void inboxReadingGrantIsRejectedEvenWhenItIncludesSendingScope() {
        String state=begin(); expectConnection(EMAIL,true,GoogleOAuthTokenStore.GMAIL_SCOPE+" https://www.googleapis.com/auth/gmail.readonly",3600);
        assertThrows(IllegalStateException.class,()->store.completeConnection(EMAIL,state,"code",PROOF)); assertTrue(store.getToken(EMAIL).isEmpty()); server.verify();
    }
    @Test void providerAuthorizationRejectionBlocksSubsequentGmailUse() {
        connect(); store.providerAuthorizationRejected(EMAIL,"synthetic-access");
        assertEquals(GoogleOAuthTokenStore.ConnectionState.RECONNECT_REQUIRED,store.getConnectionState(EMAIL)); assertTrue(store.getToken(EMAIL).isEmpty());
    }
}
