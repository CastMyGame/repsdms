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

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final Environment env;
    private final StripeEventLogRepository stripeEventLogRepository;
    private final StripeProvisioningService provisioningService;

    // ✅ ADD: we need to verify intent status before marking event processed
    private final RegistrationIntentRepository registrationIntentRepository;

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

        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        if (session == null) {
            // do not retry (Stripe sent malformed/unexpected payload)
            throw new BadWebhookRequestException("Missing checkout session object");
        }

        // ✅ We must have the intent id to verify completion before we log the Stripe event
        String intentId = (session.getMetadata() != null)
                ? session.getMetadata().get("registrationIntentId")
                : null;

        if (isBlank(intentId)) {
            throw new BadWebhookRequestException("Missing registrationIntentId in session metadata");
        }

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

    public static class BadWebhookRequestException extends RuntimeException {
        public BadWebhookRequestException(String message) {
            super(message);
        }
    }
}