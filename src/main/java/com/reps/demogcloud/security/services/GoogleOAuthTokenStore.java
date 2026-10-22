package com.reps.demogcloud.security.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import lombok.Value;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Single Gmail credential authority; separate from replace-on-save REPS user documents. */
@Component
public class GoogleOAuthTokenStore implements OAuth2AuthorizedClientService {
    public static final String GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final String REGISTRATION = "reps-gmail", FIELD = "gmailAuthorization";
    private static final String COLLECTION = "oauth2_authorized_clients";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Environment env;
    private final MongoTemplate mongo;
    private final Optional<ClientRegistrationRepository> registrations;
    private final RestTemplate http;
    private final ObjectMapper json = new ObjectMapper();
    public enum ConnectionState { UNKNOWN, NEVER_CONNECTED, DISCONNECTED, CONNECTED, RECONNECT_REQUIRED }

    @Autowired
    public GoogleOAuthTokenStore(Environment env, MongoTemplate mongo, Optional<ClientRegistrationRepository> registrations) {
        this(env, mongo, registrations, googleHttp());
    }
    public GoogleOAuthTokenStore(Environment env, MongoTemplate mongo, Optional<ClientRegistrationRepository> registrations, RestTemplate http) {
        this.env = env; this.mongo = mongo; this.registrations = registrations; this.http = http;
    }
    public static RestTemplate googleHttp() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); factory.setReadTimeout(15000);
        var client = new RestTemplate(List.of(new FormHttpMessageConverter(),
                new OAuth2AccessTokenResponseHttpMessageConverter(), new MappingJackson2HttpMessageConverter(),
                new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8)));
        client.setRequestFactory(factory); client.setErrorHandler(new OAuth2ErrorResponseErrorHandler()); return client;
    }
    public boolean isConnectionEnabled() {
        return Boolean.parseBoolean(env.getProperty("reps.email.gmail.connection-enabled", "false"));
    }
    private void requireEnabled() {
        if (!isConnectionEnabled()) throw new IllegalStateException("Gmail connection is not enabled");
        encryptionKey();
    }
    private String account(String username) {
        if (username == null || username.isBlank()) throw new AccessDeniedException("A REPS account is required");
        return username.trim().toLowerCase(Locale.ROOT);
    }
    private Query user(String username) { return Query.query(Criteria.where("_id").is(account(username))); }
    private Document connection(String username) {
        Query query = user(username); query.fields().include(FIELD);
        Document document = mongo.findOne(query, Document.class, COLLECTION);
        if (document == null) return new Document();
        Document record = document.get(FIELD, Document.class); return record == null ? new Document() : record;
    }
    public ConnectionState getConnectionState(String username) {
        if (!isConnectionEnabled()) return ConnectionState.UNKNOWN;
        String state = connection(username).getString("state");
        return state == null ? ConnectionState.NEVER_CONNECTED : ConnectionState.valueOf(state);
    }
    public Map<String, Object> status(String username) {
        if (!isConnectionEnabled()) return Map.of("available", false, "state", "UNAVAILABLE", "connectedEmail", "", "authorized", false);
        boolean usable = hasUsableGmailSendToken(username);
        Document record = connection(username);
        return Map.of("available", true, "state", Objects.toString(record.getString("state"), "NEVER_CONNECTED"),
                "connectedEmail", Objects.toString(record.getString("email"), ""), "authorized", usable);
    }
    private ClientRegistration registration() {
        ClientRegistration google = registrations.orElseThrow(() -> new IllegalStateException("Google client configuration is required")).findByRegistrationId("google");
        if (google == null) throw new IllegalStateException("Google client configuration is required");
        String callback = env.getProperty("reps.email.gmail.redirect-uri", "");
        URI uri;
        try { uri = URI.create(callback); } catch (Exception ex) { throw new IllegalStateException("A fixed Gmail callback URI is required"); }
        boolean local = "http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!local && !"https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !"/account/gmail/callback".equals(uri.getPath()))
            throw new IllegalStateException("A fixed HTTPS (or loopback HTTP) Gmail callback URI is required");
        return ClientRegistration.withRegistrationId(REGISTRATION).clientId(google.getClientId()).clientSecret(google.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(callback).authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo").userNameAttributeName("sub")
                .scope("openid", "email", GMAIL_SCOPE).clientName("REPS Gmail").build();
    }
    /** Caller must verify the authenticated REPS staff account; proof stays in the initiating browser tab. */
    public String beginConnection(String username, String browserProof) {
        requireEnabled(); validateProof(browserProof);
        ClientRegistration client = registration();
        String state = random(), verifier = random(), version = random();
        Document pending = new Document("state", hash(state)).append("proof", hash(browserProof))
                .append("verifier", encrypt(username, verifier)).append("expiresAt", Date.from(Instant.now().plusSeconds(300)))
                .append("redirect", client.getRedirectUri());
        mongo.upsert(user(username), new Update().set(FIELD + ".pending", pending).set(FIELD + ".version", version), COLLECTION);
        return OAuth2AuthorizationRequest.authorizationCode().authorizationUri(client.getProviderDetails().getAuthorizationUri())
                .clientId(client.getClientId()).redirectUri(client.getRedirectUri()).scopes(client.getScopes()).state(state)
                .additionalParameters(Map.of("access_type", "offline", "prompt", "consent", "code_challenge", hash(verifier),
                        "code_challenge_method", "S256", "include_granted_scopes", "false")).build().getAuthorizationRequestUri();
    }
    /** State is consumed atomically before HTTP; both bearer identity and browser proof are required. */
    public void completeConnection(String username, String state, String code, String browserProof) {
        requireEnabled(); validateProof(browserProof);
        if (state == null || code == null || state.length() > 200 || code.isBlank() || code.length() > 4096)
            throw new AccessDeniedException("Invalid Gmail callback");
        Query query = user(username).addCriteria(Criteria.where(FIELD + ".pending.state").is(hash(state)))
                .addCriteria(Criteria.where(FIELD + ".pending.proof").is(hash(browserProof)))
                .addCriteria(Criteria.where(FIELD + ".pending.expiresAt").gt(Date.from(Instant.now())));
        Document before = mongo.findAndModify(query, new Update().unset(FIELD + ".pending"),
                FindAndModifyOptions.options().returnNew(false), Document.class, COLLECTION);
        if (before == null) throw new AccessDeniedException("Gmail callback expired, replayed, or not bound to this account/browser");
        Document record = before.get(FIELD, Document.class), pending = record.get("pending", Document.class);
        try {
            ClientRegistration client = registration();
            if (!client.getRedirectUri().equals(pending.getString("redirect"))) throw new IllegalStateException();
            OAuth2AuthorizationRequest authorization = OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(client.getProviderDetails().getAuthorizationUri()).clientId(client.getClientId())
                    .redirectUri(client.getRedirectUri()).scopes(client.getScopes()).state(state)
                    .attributes(Map.of("code_verifier", decrypt(username, pending.getString("verifier")))).build();
            var response = OAuth2AuthorizationResponse.success(code).state(state).redirectUri(client.getRedirectUri()).build();
            // Use the installed Spring Security RestOperations clients with the explicitly intercepted transport.
            var tokenClient = new DefaultAuthorizationCodeTokenResponseClient(); tokenClient.setRestOperations(http);
            OAuth2AccessTokenResponse tokens = tokenClient.getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(client,
                    new OAuth2AuthorizationExchange(authorization, response)));
            HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(tokens.getAccessToken().getTokenValue());
            Map<?, ?> identity = http.exchange(client.getProviderDetails().getUserInfoEndpoint().getUri(), HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            if (identity == null || !Boolean.TRUE.equals(identity.get("email_verified"))
                    || !account(username).equalsIgnoreCase(Objects.toString(identity.get("email"), ""))
                    || !(identity.get("sub") instanceof String subject) || subject.isBlank()
                    || (record.getString("subject") != null && !record.getString("subject").equals(subject)))
                throw new AccessDeniedException("Google identity does not match the verified REPS account");
            OAuth2RefreshToken refresh = tokens.getRefreshToken();
            if (refresh == null && record.getString("cipher") != null) refresh = decode(username, record).getRefreshToken();
            if (refresh == null) throw new IllegalStateException("Offline Gmail authorization is required");
            persist(username, subject, new OAuth2AuthorizedClient(client, account(username), tokens.getAccessToken(), refresh), record.getString("version"));
        } catch (AccessDeniedException ex) { throw ex;
        } catch (Exception ex) { throw new IllegalStateException("Gmail connection could not be completed; reconnect and try again"); }
    }
    @Override @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId, String principalName) {
        // SSO authenticates only; its login tokens are never a second Gmail authority.
        if (!REGISTRATION.equals(registrationId) || !isConnectionEnabled()) return null;
        Document record = connection(principalName);
        return "CONNECTED".equals(record.getString("state")) ? (T) decode(principalName, record) : null;
    }
    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal) {
        if (!REGISTRATION.equals(client.getClientRegistration().getRegistrationId())) return;
        requireEnabled();
        if (principal == null || !principal.isAuthenticated() || !account(principal.getName()).equals(account(client.getPrincipalName())))
            throw new AccessDeniedException("Gmail account binding is required");
        Document record = connection(principal.getName());
        if (record.getString("subject") == null || !"CONNECTED".equals(record.getString("state")))
            throw new AccessDeniedException("Verified Gmail connection is required");
        persist(principal.getName(), record.getString("subject"), client, record.getString("version"));
    }
    @Override
    public void removeAuthorizedClient(String registrationId, String principalName) {
        if (REGISTRATION.equals(registrationId)) disconnect(principalName);
    }
    public synchronized Optional<GoogleOAuthToken> getToken(String username) {
        if (!isConnectionEnabled()) return Optional.empty();
        Document record = connection(username);
        if (!"CONNECTED".equals(record.getString("state"))) return Optional.empty();
        try {
            OAuth2AuthorizedClient client = decode(username, record);
            if (!sendingScopes(client.getAccessToken().getScopes())) throw new IllegalStateException();
            if (client.getAccessToken().getExpiresAt() == null || !client.getAccessToken().getExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
                if (client.getRefreshToken() == null) throw new IllegalStateException();
                var refreshClient = new DefaultRefreshTokenTokenResponseClient(); refreshClient.setRestOperations(http);
                OAuth2AccessTokenResponse fresh = refreshClient.getTokenResponse(new OAuth2RefreshTokenGrantRequest(
                        client.getClientRegistration(), client.getAccessToken(), client.getRefreshToken(), client.getAccessToken().getScopes()));
                client = new OAuth2AuthorizedClient(client.getClientRegistration(), account(username), fresh.getAccessToken(),
                        fresh.getRefreshToken() == null ? client.getRefreshToken() : fresh.getRefreshToken());
                persist(username, record.getString("subject"), client, record.getString("version"));
            }
            Document current = connection(username);
            if (!"CONNECTED".equals(current.getString("state")) || !Objects.equals(record.getString("subject"), current.getString("subject"))) return Optional.empty();
            client = decode(username, current);
            if (!sendingScopes(client.getAccessToken().getScopes()) || client.getAccessToken().getExpiresAt() == null
                    || !client.getAccessToken().getExpiresAt().isAfter(Instant.now())) return Optional.empty();
            return Optional.of(new GoogleOAuthToken(client.getAccessToken().getTokenValue(), client.getAccessToken().getExpiresAt(),
                    client.getRefreshToken() == null ? null : client.getRefreshToken().getTokenValue(), client.getAccessToken().getScopes()));
        } catch (Exception ex) { markReconnectRequired(username, record.getString("version")); return Optional.empty(); }
    }
    public boolean hasUsableGmailSendToken(String username) { return getToken(username).isPresent(); }
    public void providerAuthorizationRejected(String username, String rejectedAccessToken) {
        if (!isConnectionEnabled()) return;
        Document record = connection(username);
        if ("CONNECTED".equals(record.getString("state"))
                && decode(username, record).getAccessToken().getTokenValue().equals(rejectedAccessToken))
            markReconnectRequired(username, record.getString("version"));
    }
    private void markReconnectRequired(String username, String version) {
        mongo.updateFirst(user(username).addCriteria(Criteria.where(FIELD + ".version").is(version))
                        .addCriteria(Criteria.where(FIELD + ".state").is("CONNECTED")),
                new Update().set(FIELD + ".state", "RECONNECT_REQUIRED").set(FIELD + ".version", random()), COLLECTION);
    }
    public boolean disconnect(String username) {
        requireEnabled();
        Document before = mongo.findAndModify(user(username), new Update().set(FIELD + ".state", "DISCONNECTED")
                        .set(FIELD + ".version", random()).unset(FIELD + ".cipher").unset(FIELD + ".pending").unset(FIELD + ".email"),
                FindAndModifyOptions.options().returnNew(false).upsert(true), Document.class, COLLECTION);
        if (before == null) return true;
        Document record = before.get(FIELD, Document.class);
        if (record == null || record.getString("cipher") == null) return true;
        // Credentials are invalidated durably BEFORE revocation; failures never restore them.
        try {
            OAuth2AuthorizedClient previous = decode(username, record);
            var form = new LinkedMultiValueMap<String, String>();
            form.add("token", previous.getRefreshToken() == null ? previous.getAccessToken().getTokenValue() : previous.getRefreshToken().getTokenValue());
            HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            http.postForEntity("https://oauth2.googleapis.com/revoke", new HttpEntity<>(form, headers), String.class); return true;
        } catch (Exception ex) { return false; }
    }
    private void persist(String username, String subject, OAuth2AuthorizedClient client, String version) {
        OAuth2AccessToken access = client.getAccessToken();
        if (!sendingScopes(access.getScopes()) || access.getExpiresAt() == null || !access.getExpiresAt().isAfter(Instant.now())
                || client.getRefreshToken() == null) throw new IllegalStateException("Gmail sending grant is incomplete");
        try {
            String payload = json.writeValueAsString(Map.of("access", access.getTokenValue(), "refresh", client.getRefreshToken().getTokenValue(),
                    "issued", access.getIssuedAt().toString(), "expires", access.getExpiresAt().toString(), "scopes", access.getScopes()));
            requireUpdated(mongo.updateFirst(user(username).addCriteria(Criteria.where(FIELD + ".version").is(version)),
                    new Update().set(FIELD + ".state", "CONNECTED").set(FIELD + ".email", account(username)).set(FIELD + ".subject", subject)
                            .set(FIELD + ".cipher", encrypt(username, payload)).set(FIELD + ".version", random()), COLLECTION));
        } catch (Exception ex) { throw new IllegalStateException("Gmail authorization could not be saved"); }
    }
    private OAuth2AuthorizedClient decode(String username, Document record) {
        try {
            Map<?, ?> payload = json.readValue(decrypt(username, record.getString("cipher")), Map.class);
            Set<String> scopes = new HashSet<>(); for (Object scope : (Collection<?>) payload.get("scopes")) scopes.add(scope.toString());
            Instant issued = Instant.parse(payload.get("issued").toString());
            return new OAuth2AuthorizedClient(registration(), account(username), new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                    payload.get("access").toString(), issued, Instant.parse(payload.get("expires").toString()), scopes),
                    new OAuth2RefreshToken(payload.get("refresh").toString(), issued));
        } catch (Exception ex) { throw new IllegalStateException("Gmail credentials are unavailable; reconnect required"); }
    }
    private byte[] encryptionKey() {
        try {
            byte[] key = Base64.getDecoder().decode(env.getProperty("reps.email.gmail.encryption-key", ""));
            if (key.length != 32) throw new IllegalArgumentException(); return key;
        } catch (Exception ex) { throw new IllegalStateException("A securely supplied 256-bit Gmail encryption key is required"); }
    }
    private String encrypt(String username, String value) {
        try {
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(account(username).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Gmail credential encryption failed"); }
    }
    private String decrypt(String username, String value) {
        try {
            String[] parts = value.split("\\."); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            cipher.updateAAD(account(username).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Gmail credential decryption failed"); }
    }
    private static void requireUpdated(UpdateResult result) {
        if (result.getMatchedCount() != 1) throw new IllegalStateException("Gmail connection changed; restart the connection flow");
    }
    private static void validateProof(String proof) {
        if (proof == null || !proof.matches("[A-Za-z0-9_-]{43,128}")) throw new AccessDeniedException("Browser connection proof is required");
    }
    private static boolean sendingScopes(Set<String> scopes) {
        return scopes.contains(GMAIL_SCOPE) && Set.of("openid", "email", "profile", GMAIL_SCOPE,
                "https://www.googleapis.com/auth/userinfo.email", "https://www.googleapis.com/auth/userinfo.profile").containsAll(scopes);
    }
    private static String random() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Gmail connection state is unavailable"); }
    }
    @Value @lombok.ToString(onlyExplicitlyIncluded = true)
    public static class GoogleOAuthToken {
        String accessToken; Instant accessTokenExpiresAt; String refreshToken; Set<String> scopes;
    }
}
