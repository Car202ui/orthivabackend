package com.orthiva.core.shared.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Postal address shared by persons, clinics, tenants and shipping snapshots. */
@Entity
@Table(name = "address")
public class Address {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(name = "state_province", length = 80)
    private String stateProvince;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(length = 300)
    private String reference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Address() {
    }

    public Address(String country, String stateProvince, String city, String postalCode,
                   String line1, String line2, String reference) {
        this.country = country;
        this.stateProvince = stateProvince;
        this.city = city;
        this.postalCode = postalCode;
        this.line1 = line1;
        this.line2 = line2;
        this.reference = reference;
    }

    /** Immutable copy, used for shipping snapshots. */
    public Address copy() {
        return new Address(country, stateProvince, city, postalCode, line1, line2, reference);
    }

    public record Dto(UUID id, String country, String stateProvince, String city, String postalCode,
                      String line1, String line2, String reference) {
        public static Dto from(Address a) {
            return a == null ? null : new Dto(a.id, a.country, a.stateProvince, a.city, a.postalCode, a.line1, a.line2, a.reference);
        }
    }

    public record Input(String country, String stateProvince, String city, String postalCode,
                        String line1, String line2, String reference) {
        public Address toEntity() {
            return new Address(country, stateProvince, city, postalCode, line1, line2, reference);
        }

        public void applyTo(Address a) {
            a.country = country;
            a.stateProvince = stateProvince;
            a.city = city;
            a.postalCode = postalCode;
            a.line1 = line1;
            a.line2 = line2;
            a.reference = reference;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getLine1() {
        return line1;
    }
}
