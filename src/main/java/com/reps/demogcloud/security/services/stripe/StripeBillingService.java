package com.reps.demogcloud.security.services.stripe;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StripeBillingService {

    private final Environment env;

    public String createCheckoutSessionUrl(String priceId, String schoolName) throws StripeException {
        String stripeSecretKey = env.getProperty("stripe.secret.key");
        if (isBlank(stripeSecretKey)) {
            throw new IllegalStateException("Missing stripe.secret.key");
        }
        Stripe.apiKey = stripeSecretKey;

        String successUrl = env.getProperty(
                "stripe.checkout.success-url",
                "https://repsdev.vercel.app"
        ) + "?session_id={CHECKOUT_SESSION_ID}";

        String cancelUrl  = env.getProperty("stripe.checkout.cancel-url", "https://repsdev.vercel.app");

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setCustomerCreation(SessionCreateParams.CustomerCreation.ALWAYS)
                        .setClientReferenceId(schoolName.trim())
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setPrice(priceId)
                                        .setQuantity(1L)
                                        .build()
                        )
                        .putMetadata("schoolName", schoolName.trim())
                        .putMetadata("userType", "TEACHER")
                        .putMetadata("priceId", priceId.trim())
                        .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}