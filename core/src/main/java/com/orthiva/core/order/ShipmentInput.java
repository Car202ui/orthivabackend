package com.orthiva.core.order;

import jakarta.validation.constraints.Size;

/** Body of POST /api/orders/{id}/ship. Everything optional: some labs deliver by hand. */
public record ShipmentInput(
        @Size(max = 80) String carrier,
        @Size(max = 120) String trackingNumber,
        @Size(max = 2000) String notes) {
}
