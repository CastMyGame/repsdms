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
    void createCheckoutSession_shouldReturnBadRequest_whenPriceIdMissing() {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        req.setPriceId(" ");

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getUrl());
        assertEquals("priceId is required", response.getBody().getError());
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void createCheckoutSession_shouldReturnBadRequest_whenSchoolIdNumberMissing() {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        req.setSchoolIdNumber(null);

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("schoolIdNumber is required", response.getBody().getError());
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void createCheckoutSession_shouldReturnBadRequest_whenSchoolNameMissing() {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        req.setSchoolName("   ");

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("schoolName is required", response.getBody().getError());
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void createCheckoutSession_shouldReturnBadRequest_whenCurrencyNameMissing() {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        req.setCurrencyName("");

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("currencyName is required", response.getBody().getError());
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void createCheckoutSession_shouldReturnBadRequest_whenFirstNameMissing() {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        req.setFirstName(" ");

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("firstName and lastName are required", response.getBody().getError());
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void createCheckoutSession_shouldReturnBadRequest_whenLastNameMissing() {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        req.setLastName(null);

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("firstName and lastName are required", response.getBody().getError());
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void createCheckoutSession_shouldReturnBadRequest_whenEmailMissing() {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        req.setEmail("   ");

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("email is required", response.getBody().getError());
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void createCheckoutSession_shouldReturnOk_whenRequestIsValid() throws Exception {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        when(stripeBillingService.createCheckoutSessionUrl(req)).thenReturn("https://checkout.stripe.com/test");

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("https://checkout.stripe.com/test", response.getBody().getUrl());
        assertNull(response.getBody().getError());
        verify(stripeBillingService).createCheckoutSessionUrl(req);
    }

    @Test
    void createCheckoutSession_shouldReturnBadRequest_whenStripeExceptionThrown() throws Exception {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        when(stripeBillingService.createCheckoutSessionUrl(req))
                .thenThrow(new ApiException("stripe failed", null, null, 400, null));

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getUrl());
        assertEquals("Stripe error: stripe failed", response.getBody().getError());
        verify(stripeBillingService).createCheckoutSessionUrl(req);
    }

    @Test
    void createCheckoutSession_shouldReturnServerError_whenUnexpectedExceptionThrown() throws Exception {
        StripeController.CreateCheckoutSessionRequest req = validRequest();
        when(stripeBillingService.createCheckoutSessionUrl(req))
                .thenThrow(new RuntimeException("boom"));

        ResponseEntity<StripeController.CreateCheckoutSessionResponse> response =
                controller.createCheckoutSession(req);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getUrl());
        assertEquals("Server error creating checkout session", response.getBody().getError());
        verify(stripeBillingService).createCheckoutSessionUrl(req);
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