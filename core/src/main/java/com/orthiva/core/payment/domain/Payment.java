package com.orthiva.core.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.orthiva.core.payment.PaymentPurpose;
import com.orthiva.core.payment.PaymentStatus;
import com.orthiva.core.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A charge to the doctor: the digital diagnosis (on submit) or the treatment (on approval). */
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "plan_id")
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private PaymentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 40)
    private String gateway = "NONE";

    @Column(name = "gateway_reference", length = 120)
    private String gatewayReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_payload")
    private Map<String, Object> gatewayPayload;

    @Column(name = "payer_id")
    private UUID payerId;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Payment() {
    }

    public static Payment pending(UUID tenantId, UUID orderId, UUID planId, PaymentPurpose purpose, BigDecimal amount,
                                  String currency, UUID payerId) {
        var p = new Payment();
        p.tenantId = tenantId;
        p.orderId = orderId;
        p.planId = planId;
        p.purpose = purpose;
        p.amount = amount;
        p.currency = currency;
        p.payerId = payerId;
        return p;
    }

    /** Set once a checkout session exists at the gateway. */
    public void attachGateway(String gateway, String reference) {
        this.gateway = gateway;
        this.gatewayReference = reference;
    }

    public void approve(Map<String, Object> payload) {
        this.status = PaymentStatus.APPROVED;
        this.paidAt = Instant.now();
        this.gatewayPayload = payload;
    }

    public void decline(Map<String, Object> payload) {
        this.status = PaymentStatus.DECLINED;
        this.gatewayPayload = payload;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getGateway() {
        return gateway;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public UUID getPayerId() {
        return payerId;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
