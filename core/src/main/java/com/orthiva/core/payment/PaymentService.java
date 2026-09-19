package com.orthiva.core.payment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public API of the payment module: charges attached to orders and how they get approved. */
public interface PaymentService {

    List<PaymentDto> forOrder(UUID orderId);

    /** One payment; visible to its payer, the laboratory roles and ADMIN. */
    PaymentDto get(UUID paymentId);

    /**
     * Opens a checkout at the active gateway (Wompi when configured, otherwise the dev Mock)
     * and returns where to send the payer. {@code returnUrl} is where the provider redirects afterwards.
     */
    CheckoutSessionDto checkout(UUID paymentId, String returnUrl);

    /** Entry point of the provider webhooks (no user in context); idempotent per reference. */
    PaymentDto handleWebhook(String gateway, Map<String, String> headers, String rawBody);

    /** Development-only gateway (orthiva.payments.mock-enabled): approves on the spot and advances the order. */
    PaymentDto mockApprove(UUID paymentId);
}
