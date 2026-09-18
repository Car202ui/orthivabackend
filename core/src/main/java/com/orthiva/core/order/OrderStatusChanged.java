package com.orthiva.core.order;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain event published after every successful transition. Other modules (payment,
 * notification, planning) react to it through Spring Modulith's event publication
 * registry, so a crash between the commit and the listener never loses the event.
 */
public record OrderStatusChanged(
        UUID orderId,
        UUID tenantId,
        UUID doctorId,
        UUID patientId,
        Long orderNumber,
        OrderStatus from,
        OrderStatus to,
        UUID actorId,
        BigDecimal diagnosisPrice,
        String currency) {
}
