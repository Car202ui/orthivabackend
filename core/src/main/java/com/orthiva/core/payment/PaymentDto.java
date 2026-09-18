package com.orthiva.core.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDto(UUID id, UUID orderId, UUID planId, PaymentPurpose purpose, PaymentStatus status,
                         BigDecimal amount, String currency, String gateway, String gatewayReference,
                         Instant paidAt, Instant createdAt) {
}
