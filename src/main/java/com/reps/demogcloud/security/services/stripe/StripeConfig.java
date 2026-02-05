package com.reps.demogcloud.security.services.stripe;

import com.stripe.Stripe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    @PostConstruct
    public void init() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException("Missing stripe.secret.key");
        }

        Stripe.apiKey = stripeSecretKey;

        // Don’t log the full key
        log.info("Stripe API key loaded? {}", stripeSecretKey.startsWith("sk_"));
    }
}
