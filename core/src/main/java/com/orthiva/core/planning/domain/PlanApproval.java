package com.orthiva.core.planning.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.orthiva.core.shared.persistence.Address;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The doctor's acceptance of a plan version. Everything here is a snapshot: the shipping
 * address row is created for this approval only and the agreement text is copied from the
 * tenant at that moment, so later edits never change what was accepted.
 */
@Entity
@Table(name = "plan_approval")
public class PlanApproval {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "approved_by", nullable = false)
    private UUID approvedBy;

    @Column(name = "ship_to_clinic_name", nullable = false, length = 120)
    private String shipToClinicName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ship_address_id")
    private Address shipAddress;

    @Column(name = "shipping_instructions")
    private String shippingInstructions;

    @Column(name = "agreement_text", nullable = false)
    private String agreementText;

    @Column(name = "agreement_accepted", nullable = false)
    private boolean agreementAccepted;

    @CreationTimestamp
    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt;

    protected PlanApproval() {
    }

    public PlanApproval(UUID tenantId, UUID planId, UUID approvedBy, String shipToClinicName, Address shipAddress,
                        String shippingInstructions, String agreementText) {
        this.tenantId = tenantId;
        this.planId = planId;
        this.approvedBy = approvedBy;
        this.shipToClinicName = shipToClinicName;
        this.shipAddress = shipAddress;
        this.shippingInstructions = shippingInstructions;
        this.agreementText = agreementText;
        this.agreementAccepted = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public String getShipToClinicName() {
        return shipToClinicName;
    }

    public Address getShipAddress() {
        return shipAddress;
    }

    public String getShippingInstructions() {
        return shippingInstructions;
    }

    public String getAgreementText() {
        return agreementText;
    }

    public boolean isAgreementAccepted() {
        return agreementAccepted;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
