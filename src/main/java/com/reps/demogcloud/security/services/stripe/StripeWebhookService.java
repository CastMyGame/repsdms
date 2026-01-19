package com.reps.demogcloud.security.services.stripe;

import com.reps.demogcloud.security.models.stripe.StripeEventLog;
import com.reps.demogcloud.security.models.stripe.StripeEventLogRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final Environment env;
    private final StripeEventLogRepository stripeEventLogRepository;
    private final StripeProvisioningService provisioningService;

    public void handleWebhook(String payload, String sigHeader) {
        String endpointSecret = env.getProperty("stripe.webhook.secret");
        if (isBlank(endpointSecret)) throw new IllegalStateException("Missing stripe.webhook.secret");
        if (isBlank(sigHeader)) throw new IllegalArgumentException("Missing Stripe-Signature");

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid signature");
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload");
        }

        String eventId = event.getId();

        // Idempotency — if we’ve processed it, exit
        if (stripeEventLogRepository.existsById(eventId)) return;

        // Route events
        switch (event.getType()) {
            case "checkout.session.completed":
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session == null) return; // NOT processed, do not log

                try {
                    provisioningService.provisionTeacherFromCheckoutSession(session);
                } catch (Exception ex) {
                    ex.printStackTrace(); // swap to logger later
                    return; // NOT processed, do not log
                }
                break;

            default:
                // For now: ignore unhandled events AND don’t log them as processed
                return;
        }

        // ✅ Only log after successful processing
        StripeEventLog log = new StripeEventLog(eventId, Instant.now(), event.getType());
        try {
            stripeEventLogRepository.save(log);
        } catch (Exception ignore) {
            // If duplicate delivery race happens, ignore
        }
    }


    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

