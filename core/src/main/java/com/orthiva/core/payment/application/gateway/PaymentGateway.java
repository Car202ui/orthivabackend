package com.orthiva.core.payment.application.gateway;

import java.util.Map;

import com.orthiva.core.payment.PaymentDto;
import com.orthiva.core.payment.PaymentStatus;

/**
 * Port to an external payment provider. Adapters live in {@code infrastructure.gateway}.
 * The core never talks to a provider directly: it asks for a checkout URL to send the
 * payer to, and later interprets the provider's webhook through {@link #handleWebhook}.
 */
public interface PaymentGateway {

    /** Identifier stored in {@code payment.gateway} (WOMPI, MOCK...). */
    String name();

    /** True when the adapter has everything it needs (keys, secrets) to be used. */
    boolean enabled();

    /** Registers the charge at the provider (or builds the hosted-checkout URL) for a payment. */
    CheckoutSession createCheckout(PaymentDto payment, String returnUrl);

    /**
     * Validates and interprets a webhook call. Implementations must verify the provider's
     * signature and throw {@code DomainException.badRequest} when it does not match.
     */
    WebhookResult handleWebhook(Map<String, String> headers, String rawBody);

    /** Where to send the payer, and the reference the provider will echo back. */
    record CheckoutSession(String reference, String checkoutUrl) {
    }

    /** What a webhook says about one payment; {@code payload} is stored for audit. */
    record WebhookResult(String reference, PaymentStatus status, Map<String, Object> payload) {
    }
}
