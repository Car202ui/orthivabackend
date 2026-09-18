package com.orthiva.core.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDto(UUID id, UUID orderId, UUID planId, Payment.Purpose purpose, Payment.Status status,
                         BigDecimal amount, String currency, String gateway, String gatewayReference,
                         Instant paidAt, Instant createdAt) {

    public static PaymentDto from(Payment p) {
        return new PaymentDto(p.getId(), p.getOrderId(), p.getPlanId(), p.getPurpose(), p.getStatus(), p.getAmount(),
                p.getCurrency(), p.getGateway(), p.getGatewayReference(), p.getPaidAt(), p.getCreatedAt());
    }
}
