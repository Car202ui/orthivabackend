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
        /** Uses getters on purpose: {@code a} may be a lazy Hibernate proxy whose fields are empty. */
        public static Dto from(Address a) {
            return a == null ? null : new Dto(a.getId(), a.getCountry(), a.getStateProvince(), a.getCity(),
                    a.getPostalCode(), a.getLine1(), a.getLine2(), a.getReference());
        }
    }

    public record Input(String country, String stateProvince, String city, String postalCode,
                        String line1, String line2, String reference) {
        public Address toEntity() {
            return new Address(country, stateProvince, city, postalCode, line1, line2, reference);
        }

        public void applyTo(Address a) {
            a.update(this);   // method call so a lazy proxy forwards to the real entity
        }
    }

    public void update(Input in) {
        this.country = in.country();
        this.stateProvince = in.stateProvince();
        this.city = in.city();
        this.postalCode = in.postalCode();
        this.line1 = in.line1();
        this.line2 = in.line2();
        this.reference = in.reference();
    }

    public UUID getId() {
        return id;
    }

    public String getCountry() {
        return country;
    }

    public String getStateProvince() {
        return stateProvince;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getReference() {
        return reference;
    }
}
