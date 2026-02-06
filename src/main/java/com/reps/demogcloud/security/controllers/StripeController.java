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

        try {

            if (isBlank(req.priceId)) {
                return ResponseEntity.badRequest().body(new CreateCheckoutSessionResponse(null, "priceId is required"));
            }
            if (isBlank(req.schoolIdNumber)) {
                return ResponseEntity.badRequest().body(new CreateCheckoutSessionResponse(null, "schoolIdNumber is required"));
            }
            if (isBlank(req.schoolName)) {
                return ResponseEntity.badRequest().body(new CreateCheckoutSessionResponse(null, "schoolName is required"));
            }
            if (isBlank(req.currencyName)) {
                return ResponseEntity.badRequest().body(new CreateCheckoutSessionResponse(null, "currencyName is required"));
            }
            if (isBlank(req.firstName) || isBlank(req.lastName)) {
                return ResponseEntity.badRequest().body(new CreateCheckoutSessionResponse(null, "firstName and lastName are required"));
            }
            if (isBlank(req.email)) {
                return ResponseEntity.badRequest().body(new CreateCheckoutSessionResponse(null, "email is required"));
            }

            String url = stripeBillingService.createCheckoutSessionUrl(req);
            return ResponseEntity.ok(new CreateCheckoutSessionResponse(url, null));
        } catch (com.stripe.exception.StripeException se) {
            log.warn("Stripe error creating checkout session: {}", se.getMessage(), se);
            String msg = "Stripe error: " + se.getMessage();
            return ResponseEntity.status(400).body(new CreateCheckoutSessionResponse(null, msg));
        } catch (Exception e) {
            log.error("Server error creating checkout session", e);
            return ResponseEntity.status(500).body(new CreateCheckoutSessionResponse(null, "Server error creating checkout session"));
        }
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
            // Stripe sent something invalid (bad signature/payload/missing header)
            // 400 is correct and SHOULD NOT be retried in normal operation.
            log.warn("Stripe webhook bad request: {}", bre.getMessage());
            return ResponseEntity.badRequest().body("bad request");
        } catch (Exception e) {
            // Any provisioning / server failure => Stripe SHOULD retry
            log.error("Stripe webhook server/provisioning failure", e);
            return ResponseEntity.status(500).body("server error");
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
