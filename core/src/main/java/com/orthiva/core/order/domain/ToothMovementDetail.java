package com.orthiva.core.order.domain;

import com.orthiva.core.order.OrderInput;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Requested movement for one tooth (FDI notation) or a general instruction when toothFdi is null. */
@Entity
@Table(name = "tooth_movement_detail")
public class ToothMovementDetail {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private TreatmentOrder order;

    @Column(name = "tooth_fdi")
    private Short toothFdi;

    @Column(length = 80)
    private String torque;

    @Column(length = 80)
    private String rotation;

    @Column(length = 80)
    private String buccolingual;

    @Column(length = 80)
    private String mesiodistal;

    @Column(name = "intrusion_extrusion", length = 80)
    private String intrusionExtrusion;

    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ToothMovementDetail() {
    }

    public ToothMovementDetail(TreatmentOrder order, OrderInput.Movement in) {
        this.order = order;
        this.tenantId = order.getTenantId();
        this.toothFdi = in.toothFdi();
        this.torque = in.torque();
        this.rotation = in.rotation();
        this.buccolingual = in.buccolingual();
        this.mesiodistal = in.mesiodistal();
        this.intrusionExtrusion = in.intrusionExtrusion();
        this.notes = in.notes();
    }

    public UUID getId() {
        return id;
    }

    public Short getToothFdi() {
        return toothFdi;
    }

    public String getTorque() {
        return torque;
    }

    public String getRotation() {
        return rotation;
    }

    public String getBuccolingual() {
        return buccolingual;
    }

    public String getMesiodistal() {
        return mesiodistal;
    }

    public String getIntrusionExtrusion() {
        return intrusionExtrusion;
    }

    public String getNotes() {
        return notes;
    }
}
