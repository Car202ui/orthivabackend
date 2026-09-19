package com.orthiva.core.order.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.orthiva.core.order.ShipmentInput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The aligners left the laboratory. Carrier and tracking are optional (hand delivery, courier without tracking). */
@Entity
@Table(name = "shipment")
public class Shipment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(length = 80)
    private String carrier;

    @Column(name = "tracking_number", length = 120)
    private String trackingNumber;

    private String notes;

    @Column(name = "shipped_by")
    private UUID shippedBy;

    @CreationTimestamp
    @Column(name = "shipped_at", nullable = false, updatable = false)
    private Instant shippedAt;

    protected Shipment() {
    }

    public Shipment(UUID tenantId, UUID orderId, UUID shippedBy, ShipmentInput in) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.shippedBy = shippedBy;
        this.carrier = blankToNull(in.carrier());
        this.trackingNumber = blankToNull(in.trackingNumber());
        this.notes = blankToNull(in.notes());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getShippedBy() {
        return shippedBy;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }
}
