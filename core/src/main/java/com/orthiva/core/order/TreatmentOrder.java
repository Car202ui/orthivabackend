package com.orthiva.core.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import com.orthiva.core.shared.persistence.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** A prescription sent by a doctor to the lab; the aggregate the whole workflow revolves around. */
@Entity
@Table(name = "treatment_order")
public class TreatmentOrder extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** BIGSERIAL assigned by the database; Hibernate re-reads it right after the insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "order_number", insertable = false, updatable = false)
    private Long orderNumber;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "clinic_id")
    private UUID clinicId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.DRAFT;

    @Column(name = "first_time", nullable = false)
    private boolean firstTime = true;

    @Column(nullable = false)
    private boolean reevaluation;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Arch arch;

    @Column(name = "treatment_goal")
    private String treatmentGoal;

    @Column(name = "diagnosis_price")
    private BigDecimal diagnosisPrice;

    @Column(length = 3)
    private String currency;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("toothFdi asc")
    private List<ToothMovementDetail> movements = new ArrayList<>();

    protected TreatmentOrder() {
    }

    public TreatmentOrder(UUID tenantId, UUID doctorId, UUID patientId, OrderInput in) {
        this.tenantId = tenantId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        apply(in);
    }

    public void apply(OrderInput in) {
        this.clinicId = in.clinicId();
        this.arch = in.arch();
        this.firstTime = in.firstTime() == null || in.firstTime();
        this.reevaluation = in.reevaluation() != null && in.reevaluation();
        this.treatmentGoal = in.treatmentGoal();
        this.movements.clear();
        if (in.movements() != null) {
            in.movements().forEach(m -> this.movements.add(new ToothMovementDetail(this, m)));
        }
    }

    /** Called by the workflow after validating the transition. */
    void setStatus(OrderStatus status) {
        this.status = status;
    }

    void markSubmitted(BigDecimal diagnosisPrice, String currency) {
        this.submittedAt = Instant.now();
        this.diagnosisPrice = diagnosisPrice;
        this.currency = currency;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Long getOrderNumber() {
        return orderNumber;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean isFirstTime() {
        return firstTime;
    }

    public boolean isReevaluation() {
        return reevaluation;
    }

    public Arch getArch() {
        return arch;
    }

    public String getTreatmentGoal() {
        return treatmentGoal;
    }

    public BigDecimal getDiagnosisPrice() {
        return diagnosisPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public List<ToothMovementDetail> getMovements() {
        return movements;
    }
}
