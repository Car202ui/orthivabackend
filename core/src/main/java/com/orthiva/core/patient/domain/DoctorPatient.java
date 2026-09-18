package com.orthiva.core.patient.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Doctor <-> patient relationship with history (a patient may change doctor). */
@Entity
@Table(name = "doctor_patient")
public class DoctorPatient {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private LocalDate since = LocalDate.now();

    private LocalDate until;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DoctorPatient() {
    }

    public DoctorPatient(UUID tenantId, UUID doctorId, UUID patientId) {
        this.tenantId = tenantId;
        this.doctorId = doctorId;
        this.patientId = patientId;
    }

    public void end() {
        this.active = false;
        this.until = LocalDate.now();
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public LocalDate getSince() {
        return since;
    }

    public boolean isActive() {
        return active;
    }
}
