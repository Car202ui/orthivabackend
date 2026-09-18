package com.orthiva.core.identity;

import java.math.BigDecimal;

import com.orthiva.core.shared.persistence.Address;
import com.orthiva.core.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A laboratory as a SaaS customer. Everything clinical hangs from a tenant. */
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 60, unique = true)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 3)
    private String currency = "COP";

    @Column(name = "diagnosis_price", nullable = false)
    private BigDecimal diagnosisPrice = BigDecimal.ZERO;

    @Column(name = "agreement_text")
    private String agreementText;

    protected Tenant() {
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getDiagnosisPrice() {
        return diagnosisPrice;
    }

    public String getAgreementText() {
        return agreementText;
    }

    public boolean isActive() {
        return active;
    }
}
