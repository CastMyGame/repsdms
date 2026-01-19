package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.services.stripe.StripeBillingService;
import com.reps.demogcloud.security.services.stripe.StripeWebhookService;
import com.stripe.exception.StripeException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stripe/v1")
public class StripeController {

    private final StripeBillingService stripeBillingService;
    private final StripeWebhookService stripeWebhookService;

    @PostMapping("/create-checkout-session")
    public ResponseEntity<CreateCheckoutSessionResponse> createCheckoutSession(
            @RequestBody CreateCheckoutSessionRequest req
    ) throws StripeException {

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
            return ResponseEntity.ok(new CreateCheckoutSessionResponse(url, ""));
        } catch (com.stripe.exception.StripeException se) {
            se.printStackTrace();
            String msg = "Stripe error: " + se.getMessage();
            return ResponseEntity.status(400).body(new CreateCheckoutSessionResponse(null, msg));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new CreateCheckoutSessionResponse(null, "Server error creating checkout session"));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(value="Stripe-Signature", required=false) String sigHeader
    ) {
        System.out.println(">>> HIT StripeController /stripe/v1/webhook");
        try {
            stripeWebhookService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("ok");
        } catch (Throwable t) { // YES Throwable for debugging
            t.printStackTrace();
            return ResponseEntity.ok("ok"); // always 200 to stop retries while debugging
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
