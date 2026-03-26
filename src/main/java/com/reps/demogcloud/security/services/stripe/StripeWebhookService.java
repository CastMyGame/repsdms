package com.reps.demogcloud.security.services.stripe;

import com.reps.demogcloud.security.models.stripe.RegistrationIntent;
import com.reps.demogcloud.security.models.stripe.RegistrationIntentRepository;
import com.reps.demogcloud.security.models.stripe.StripeEventLog;
import com.reps.demogcloud.security.models.stripe.StripeEventLogRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.json.JSONObject;

import javax.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final Environment env;
    private final StripeEventLogRepository stripeEventLogRepository;
    private final StripeProvisioningService provisioningService;

    // ✅ ADD: we need to verify intent status before marking event processed
    private final RegistrationIntentRepository registrationIntentRepository;

    @PostConstruct
    public void logWebhookSecretLoaded() {
        String endpointSecret = env.getProperty("stripe.webhook.secret");
        log.info("Stripe webhook secret loaded? {}", endpointSecret != null && endpointSecret.startsWith("whsec_"));
    }

    public void handleWebhook(String payload, String sigHeader) {
        String endpointSecret = env.getProperty("stripe.webhook.secret");
        if (isBlank(endpointSecret)) {
            log.error("Missing stripe.webhook.secret");
            // This is a server misconfig -> should be 500 (Stripe retries)
            throw new IllegalStateException("Missing stripe.webhook.secret");
        }
        if (isBlank(sigHeader)) {
            throw new BadWebhookRequestException("Missing Stripe-Signature header");
        }

        final Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            log.info("Stripe event received: type={}, id={}", event.getType(), event.getId());
        } catch (SignatureVerificationException e) {
            // bad signature -> do not retry
            throw new BadWebhookRequestException("Invalid Stripe signature");
        } catch (Exception e) {
            // bad payload -> do not retry
            throw new BadWebhookRequestException("Invalid Stripe payload");
        }

        final String eventId = event.getId();
        final String eventType = event.getType();

        // If already processed successfully, exit (idempotency)
        if (stripeEventLogRepository.existsById(eventId)) {
            return;
        }

        // Only handle what you support
        if (!"checkout.session.completed".equals(eventType)) {
            return; // ignored, controller returns 200
        }

        // Try the official deserializer first
        // Try the official deserializer first
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

// Fallback: parse payload for cs_... then retrieve from Stripe
        if (session == null) {
            String sessionId = extractCheckoutSessionIdFromPayload(payload);
            if (isBlank(sessionId)) {
                throw new IllegalStateException("Missing checkout.session id in payload (cannot process webhook)");
            }
            session = retrieveCheckoutSession(sessionId); // use Session.retrieve(sessionId)
        }

        // Resolve intentId (session + payload)
        String intentId = resolveIntentId(payload, session);

        // If we still don't have it, the Session object we got might be "partial".
        // Retrieve the full session from Stripe using its ID, then try again.
        if (isBlank(intentId) && session != null && !isBlank(session.getId())) {
            Session full = retrieveCheckoutSession(session.getId());
            session = full; // replace with full object
            intentId = resolveIntentId(payload, session);
        }

        if (isBlank(intentId)) {
            throw new IllegalStateException("Missing registrationIntentId (session + payload + retrieve)");

        }

        log.info("Checkout session parsed: id={}, customer={}, subscription={}, hasMetadata={}",
                session.getId(),
                session.getCustomer(),
                session.getSubscription(),
                session.getMetadata() != null && !session.getMetadata().isEmpty());

        log.info("Resolved intentId={}", intentId);


        // Provisioning failures should trigger retry (500)
        provisioningService.provisionTeacherFromCheckoutSession(session);

        // ✅ CRITICAL: only mark Stripe event processed if the intent is COMPLETED now
        RegistrationIntent intent = registrationIntentRepository.findById(intentId).orElse(null);
        if (intent == null) {
            // this is server-side inconsistency -> retry
            throw new IllegalStateException("RegistrationIntent not found after provisioning: " + intentId);
        }
        if (!"COMPLETED".equalsIgnoreCase(intent.getStatus())) {
            // Someone else may still be processing, or provisioning failed silently.
            // If we logged the event here we'd kill Stripe retries.
            throw new IllegalStateException("Provisioning did not complete. Intent status=" + intent.getStatus());
        }

        // Only mark processed after successful provisioning + confirmed completed
        try {
            stripeEventLogRepository.insert(StripeEventLog.processed(eventId, eventType));
        } catch (Exception ignore) {
            // race condition: another delivery already saved it
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String extractCheckoutSessionIdFromPayload(String payload) {
        try {
            JSONObject root = new JSONObject(payload);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;

            JSONObject obj = data.optJSONObject("object");
            if (obj == null) return null;

            return obj.optString("id", null); // "cs_..."
        } catch (Exception e) {
            return null;
        }
    }

    private Session retrieveCheckoutSession(String sessionId) {
        try {
            return Session.retrieve(sessionId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve checkout session from Stripe: " + sessionId, e);
        }
    }

    private String resolveIntentId(String payload, Session session) {
        // 1) session.metadata.registrationIntentId
        if (session != null && session.getMetadata() != null) {
            String v = session.getMetadata().get("registrationIntentId");
            if (!isBlank(v)) return v;
        }

        // 2) payload fallback
        String fromPayload = extractRegistrationIntentIdFromPayload(payload);
        if (!isBlank(fromPayload)) return fromPayload;

        // 3) client_reference_id fallback (ONLY if you store it as intentId)
        if (session != null && !isBlank(session.getClientReferenceId())) {
            return session.getClientReferenceId();
        }

        return null;
    }

    private String extractRegistrationIntentIdFromPayload(String payload) {
        try {
            JSONObject root = new JSONObject(payload);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            JSONObject obj = data.optJSONObject("object");
            if (obj == null) return null;

            JSONObject metadata = obj.optJSONObject("metadata");
            if (metadata == null) return null;

            return metadata.optString("registrationIntentId", null);
        } catch (Exception e) {
            return null;
        }
    }

    public static class BadWebhookRequestException extends RuntimeException {
        public BadWebhookRequestException(String message) {
            super(message);
        }
    }
}