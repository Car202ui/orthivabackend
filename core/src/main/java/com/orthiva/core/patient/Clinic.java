package com.orthiva.core.patient;

import java.time.Instant;
import java.util.UUID;

import com.orthiva.core.shared.persistence.Address;
import com.orthiva.core.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A doctor's practice: where treatments are shipped to. */
@Entity
@Table(name = "clinic")
public class Clinic extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 160)
    private String website;

    @Column(name = "phone_country", length = 6)
    private String phoneCountry;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Clinic() {
    }

    public Clinic(UUID tenantId, UUID doctorId, ClinicInput in, Address address) {
        this.tenantId = tenantId;
        this.doctorId = doctorId;
        this.address = address;
        apply(in);
    }

    public void apply(ClinicInput in) {
        this.name = in.name();
        this.website = in.website();
        this.phoneCountry = in.phoneCountry();
        this.phoneNumber = in.phoneNumber();
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public String getName() {
        return name;
    }

    public String getWebsite() {
        return website;
    }

    public String getPhoneCountry() {
        return phoneCountry;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Address getAddress() {
        return address;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
