package com.orthiva.core.identity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.orthiva.core.shared.persistence.Address;
import com.orthiva.core.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Any human in the system: doctors, patients, lab staff, admins. Identity (login,
 * password) lives in Keycloak and is linked through {@code keycloakUserId}.
 */
@Entity
@Table(name = "person")
public class Person extends BaseEntity {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "keycloak_user_id", unique = true)
    private UUID keycloakUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "person_type", nullable = false)
    private PersonType type;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Gender gender;

    @Column(name = "document_id", length = 30)
    private String documentId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 160)
    private String email;

    @Column(name = "phone_country", length = 6)
    private String phoneCountry;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(length = 80)
    private String specialty;

    @Column(name = "license_number", length = 40)
    private String licenseNumber;

    @Column(name = "license_country", length = 2)
    private String licenseCountry;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(nullable = false, length = 5)
    private String locale = "es";

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Person() {
    }

    public static Person provision(UUID tenantId, UUID keycloakUserId, PersonType type,
                                   String firstName, String lastName, String email) {
        var p = new Person();
        p.tenantId = tenantId;
        p.keycloakUserId = keycloakUserId;
        p.type = type;
        p.firstName = firstName == null || firstName.isBlank() ? "-" : firstName;
        p.lastName = lastName == null || lastName.isBlank() ? "-" : lastName;
        p.email = email;
        return p;
    }

    /** Patient record created by a doctor; may be linked to a Keycloak user later by email. */
    public static Person newPatient(UUID tenantId, String firstName, String lastName, String email,
                                    String documentId, LocalDate birthDate, Gender gender) {
        var p = new Person();
        p.tenantId = tenantId;
        p.type = PersonType.PATIENT;
        p.firstName = firstName;
        p.lastName = lastName;
        p.email = email;
        p.documentId = documentId;
        p.birthDate = birthDate;
        p.gender = gender;
        p.profileCompleted = true;
        return p;
    }

    public void linkKeycloak(UUID keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public void completeProfile(ProfileInput in) {
        this.firstName = in.firstName();
        this.lastName = in.lastName();
        this.phoneCountry = in.phoneCountry();
        this.phoneNumber = in.phoneNumber();
        this.locale = in.locale() == null ? this.locale : in.locale();
        this.gender = in.gender();
        this.documentId = in.documentId();
        this.birthDate = in.birthDate();
        if (type == PersonType.DOCTOR) {
            this.specialty = in.specialty();
            this.licenseNumber = in.licenseNumber();
            this.licenseCountry = in.licenseCountry();
        }
        this.profileCompleted = true;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void markVerified(UUID byPersonId) {
        this.verifiedAt = Instant.now();
        this.verifiedBy = byPersonId;
    }

    public void deactivate() {
        this.active = false;
        this.deletedAt = Instant.now();
    }

    // --- getters -------------------------------------------------------------

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public PersonType getType() {
        return type;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Gender getGender() {
        return gender;
    }

    public String getDocumentId() {
        return documentId;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getEmail() {
        return email;
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

    public String getSpecialty() {
        return specialty;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public String getLicenseCountry() {
        return licenseCountry;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getLocale() {
        return locale;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public boolean isActive() {
        return active;
    }
}
