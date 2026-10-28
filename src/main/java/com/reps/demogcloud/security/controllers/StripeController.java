package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.services.stripe.StripeBillingService;
import com.reps.demogcloud.security.services.stripe.StripeWebhookService;
import com.stripe.exception.StripeException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stripe/v1")
@Slf4j
public class StripeController {

    private final StripeBillingService stripeBillingService;
    private final StripeWebhookService stripeWebhookService;

    @PostMapping("/create-checkout-session")
    public ResponseEntity<CreateCheckoutSessionResponse> createCheckoutSession(
            @RequestBody CreateCheckoutSessionRequest req
    ) {
        return ResponseEntity.status(503).body(new CreateCheckoutSessionResponse(null, StripeBillingService.CHECKOUT_UNAVAILABLE));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature") String sigHeader
    ) {
        log.info(">>> HIT StripeController /stripe/v1/webhook");
        log.info("Webhook hit. Stripe-Signature present? {}", sigHeader != null && !sigHeader.isBlank());

        try {
            stripeWebhookService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("ok");
        } catch (StripeWebhookService.BadWebhookRequestException bre) {
            log.warn("Stripe webhook bad request: {}", bre.getMessage());
            return ResponseEntity.badRequest().body("bad request");
        } catch (Exception e) {
            log.error("Stripe webhook server/provisioning failure", e);
            return ResponseEntity.internalServerError().body("server error");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Data
    @lombok.ToString
    public static class CreateCheckoutSessionRequest {
        private String priceId;
        private String schoolIdNumber;
        private String schoolName;
        private String currencyName;
        private String firstName;
        private String lastName;
        private String email;
    }

    @Data
    public static class CreateCheckoutSessionResponse {
        private final String url;
        private final String error;
    }
}