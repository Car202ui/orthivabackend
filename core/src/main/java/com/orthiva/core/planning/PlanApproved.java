package com.orthiva.core.planning;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when the doctor approves a plan version; payment creates the TREATMENT charge from it. */
public record PlanApproved(UUID planId, UUID orderId, UUID tenantId, UUID doctorId, int version,
                           BigDecimal priceTotal, String currency) {
}
