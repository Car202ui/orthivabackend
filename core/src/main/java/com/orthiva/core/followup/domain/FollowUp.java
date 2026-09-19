package com.orthiva.core.followup.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.orthiva.core.followup.FollowUpInput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One control visit during the treatment (soft-deletable). Photos hang off media_asset.follow_up_id. */
@Entity
@Table(name = "follow_up")
public class FollowUp {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "treatment_month", nullable = false)
    private int treatmentMonth;

    private String notes;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FollowUp() {
    }

    public FollowUp(UUID tenantId, UUID orderId, UUID recordedBy, FollowUpInput in) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.recordedBy = recordedBy;
        apply(in);
    }

    public void apply(FollowUpInput in) {
        this.visitDate = in.visitDate();
        this.treatmentMonth = in.treatmentMonth();
        this.notes = in.notes() == null || in.notes().isBlank() ? null : in.notes().trim();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public int getTreatmentMonth() {
        return treatmentMonth;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
