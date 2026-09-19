package com.orthiva.core.payment;

import java.util.UUID;

/** Response of POST /api/payments/{id}/checkout: send the payer to {@code checkoutUrl}. */
public record CheckoutSessionDto(UUID paymentId, String gateway, String reference, String checkoutUrl) {
}
