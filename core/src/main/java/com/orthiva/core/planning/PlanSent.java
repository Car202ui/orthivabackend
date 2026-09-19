package com.orthiva.core.planning;

import java.util.UUID;

/** Published when the lab sends a plan version to the doctor (notifications listen to it). */
public record PlanSent(UUID planId, UUID orderId, UUID tenantId, UUID doctorId, Long orderNumber, int version) {
}
