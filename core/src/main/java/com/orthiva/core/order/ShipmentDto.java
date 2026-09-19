package com.orthiva.core.order;

import com.orthiva.core.order.domain.Shipment;

import java.time.Instant;
import java.util.UUID;

public record ShipmentDto(UUID id, String carrier, String trackingNumber, String notes, UUID shippedBy,
                          String shippedByName, Instant shippedAt) {

    public static ShipmentDto from(Shipment s, String shippedByName) {
        return new ShipmentDto(s.getId(), s.getCarrier(), s.getTrackingNumber(), s.getNotes(), s.getShippedBy(),
                shippedByName, s.getShippedAt());
    }
}
