package com.orthiva.core.payment.infrastructure.gateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.orthiva.core.payment.PaymentDto;
import com.orthiva.core.payment.PaymentStatus;
import com.orthiva.core.payment.application.gateway.PaymentGateway;
import com.orthiva.core.shared.web.DomainException;

/**
 * Development gateway: the "checkout" is a page of the web app where the tester approves or
 * declines, and that page posts the outcome back as an (unsigned) webhook. Enabled only
 * with {@code orthiva.payments.mock-enabled=true}; never in production.
 */
@Component
class MockGateway implements PaymentGateway {

    static final String NAME = "MOCK";

    private final boolean enabled;
    private final String appUrl;
    private final ObjectMapper json;

    MockGateway(@Value("${orthiva.payments.mock-enabled:false}") boolean enabled,
                @Value("${orthiva.app-url:http://localhost:3000}") String appUrl, ObjectMapper json) {
        this.enabled = enabled;
        this.appUrl = appUrl;
        this.json = json;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public CheckoutSession createCheckout(PaymentDto payment, String returnUrl) {
        String reference = "mock-" + UUID.randomUUID();
        String url = appUrl + "/pay/mock/" + reference + "?return=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
        return new CheckoutSession(reference, url);
    }

    /** Body: {"reference": "...", "status": "APPROVED" | "DECLINED"}. */
    @Override
    public WebhookResult handleWebhook(Map<String, String> headers, String rawBody) {
        if (!enabled) {
            throw DomainException.forbidden("Mock payments are disabled");
        }
        Map<String, Object> body;
        try {
            body = json.readValue(rawBody, new TypeReference<>() { });
        } catch (Exception e) {
            throw DomainException.badRequest("invalid_webhook", "Malformed mock webhook body");
        }
        String reference = String.valueOf(body.get("reference"));
        PaymentStatus status = "APPROVED".equalsIgnoreCase(String.valueOf(body.get("status")))
                ? PaymentStatus.APPROVED : PaymentStatus.DECLINED;
        return new WebhookResult(reference, status, Map.of("gateway", NAME, "status", status.name()));
    }
}
