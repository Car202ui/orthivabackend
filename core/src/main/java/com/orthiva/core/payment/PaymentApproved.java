package com.orthiva.core.payment;

import java.math.BigDecimal;
import java.util.UUID;

/** Published once a gateway confirms a payment (notifications listen to it). */
public record PaymentApproved(UUID paymentId, UUID orderId, UUID tenantId, UUID payerId, PaymentPurpose purpose,
                              BigDecimal amount, String currency, String gateway) {
}
