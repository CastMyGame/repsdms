package com.reps.demogcloud.security.services.stripe;

import com.reps.demogcloud.security.controllers.StripeController;
import com.reps.demogcloud.security.models.stripe.RegistrationIntent;
import com.reps.demogcloud.security.models.stripe.RegistrationIntentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StripeBillingService {

    private final Environment env;
    private final RegistrationIntentRepository registrationIntentRepository;

    @PostConstruct
    public void init() {
        String stripeSecretKey = env.getProperty("stripe.secret.key");
        if (isBlank(stripeSecretKey)) {
            // Don't crash the whole app on boot; fail when the endpoint is called.
            // This makes local dev + tests much smoother.
            System.err.println("Stripe secret key missing: stripe.secret.key");
            return;
        }
        Stripe.apiKey = stripeSecretKey;
    }


    public String createCheckoutSessionUrl(StripeController.CreateCheckoutSessionRequest req) throws StripeException {
        String stripeSecretKey = env.getProperty("stripe.secret.key");
        if (isBlank(stripeSecretKey)) {
            throw new IllegalStateException("Missing stripe.secret.key");
        }
        Stripe.apiKey = stripeSecretKey;

        RegistrationIntent intent = RegistrationIntent.builder()
                .createdAt(Instant.now())
                .status("PENDING")
                .schoolIdNumber(req.getSchoolIdNumber().trim())
                .schoolName(req.getSchoolName().trim())
                .currencyName(req.getCurrencyName().trim())
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .email(req.getEmail().trim().toLowerCase())
                .priceId(req.getPriceId().trim())
                .build();

        intent = registrationIntentRepository.save(intent);

        String successBase = env.getProperty("stripe.checkout.success-url", "https://repsdev.vercel.app");
        String successUrl = successBase + (successBase.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";

        String cancelBase = env.getProperty("stripe.checkout.cancel-url", "https://repsdev.vercel.app");
        String cancelUrl = cancelBase;

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setCustomerEmail(req.getEmail().trim().toLowerCase())
                        .setClientReferenceId(req.getSchoolIdNumber().trim())
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setPrice(req.getPriceId().trim())
                                        .setQuantity(1L)
                                        .build()
                        )
                        .putMetadata("registrationIntentId", intent.getId())
                        .putMetadata("schoolIdNumber", req.getSchoolIdNumber().trim())
                        .putMetadata("schoolName", req.getSchoolName().trim())
                        .putMetadata("userType", "TEACHER")
                        .putMetadata("priceId", req.getPriceId().trim())
                        .putMetadata("email", req.getEmail().trim().toLowerCase())
                        .putMetadata("currencyName", req.getCurrencyName().trim())
                        .build();

        Session session = Session.create(params);

        if (session == null || isBlank(session.getUrl())) {
            throw new IllegalStateException("Stripe session created but no URL was returned");
        }

        intent.setStatus("CHECKOUT_CREATED");
        intent.setStripeCheckoutSessionId(session.getId());
        registrationIntentRepository.save(intent);

        return session.getUrl();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}