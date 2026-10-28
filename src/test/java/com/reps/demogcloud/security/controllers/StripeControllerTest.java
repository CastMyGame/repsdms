package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.services.stripe.StripeBillingService;
import com.reps.demogcloud.security.services.stripe.StripeWebhookService;
import com.stripe.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeControllerTest {

    @Mock
    private StripeBillingService stripeBillingService;

    @Mock
    private StripeWebhookService stripeWebhookService;

    private StripeController controller;

    @BeforeEach
    void setUp() {
        controller = new StripeController(stripeBillingService, stripeWebhookService);
    }

    private StripeController.CreateCheckoutSessionRequest validRequest() {
        StripeController.CreateCheckoutSessionRequest req = new StripeController.CreateCheckoutSessionRequest();
        req.setPriceId("price_123");
        req.setSchoolIdNumber("school-1");
        req.setSchoolName("Test School");
        req.setCurrencyName("Points");
        req.setFirstName("Chris");
        req.setLastName("Coach");
        req.setEmail("chris@test.com");
        return req;
    }

    @Test
    void checkoutIsUnavailableWithoutCallingBillingEvenForNullOrInvalidPayloads() {
        for (var request : java.util.Arrays.asList(validRequest(), null, new StripeController.CreateCheckoutSessionRequest())) {
            var response=controller.createCheckoutSession(request);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,response.getStatusCode());
            assertNull(response.getBody().getUrl());
            assertEquals(StripeBillingService.CHECKOUT_UNAVAILABLE,response.getBody().getError());
        }
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void webhook_shouldReturnOk_whenWebhookHandledSuccessfully() throws Exception {
        ResponseEntity<String> response = controller.webhook("{\"type\":\"test\"}", "sig_123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ok", response.getBody());
        verify(stripeWebhookService).handleWebhook("{\"type\":\"test\"}", "sig_123");
    }

    @Test
    void webhook_shouldReturnBadRequest_whenBadWebhookRequestExceptionThrown() throws Exception {
        doThrow(new StripeWebhookService.BadWebhookRequestException("bad signature"))
                .when(stripeWebhookService).handleWebhook("{\"type\":\"test\"}", "sig_123");

        ResponseEntity<String> response = controller.webhook("{\"type\":\"test\"}", "sig_123");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad request", response.getBody());
        verify(stripeWebhookService).handleWebhook("{\"type\":\"test\"}", "sig_123");
    }

    @Test
    void webhook_shouldReturnServerError_whenUnexpectedExceptionThrown() throws Exception {
        doThrow(new RuntimeException("server boom"))
                .when(stripeWebhookService).handleWebhook("{\"type\":\"test\"}", "sig_123");

        ResponseEntity<String> response = controller.webhook("{\"type\":\"test\"}", "sig_123");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("server error", response.getBody());
        verify(stripeWebhookService).handleWebhook("{\"type\":\"test\"}", "sig_123");
    }
}