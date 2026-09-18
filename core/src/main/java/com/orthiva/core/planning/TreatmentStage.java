package com.orthiva.core.planning;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.orthiva.core.order.Arch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One aligner step for one arch. */
@Entity
@Table(name = "treatment_stage")
public class TreatmentStage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private TreatmentPlan plan;

    @Column(name = "stage_number", nullable = false)
    private int stageNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Arch arch;

    private String description;

    private BigDecimal cost;

    protected TreatmentStage() {
    }

    TreatmentStage(TreatmentPlan plan, PlanInput.Stage in) {
        this.plan = plan;
        this.tenantId = plan.getTenantId();
        this.stageNumber = in.stageNumber();
        this.arch = in.arch();
        this.description = in.description();
        this.cost = in.cost();
    }

    public UUID getId() {
        return id;
    }

    public int getStageNumber() {
        return stageNumber;
    }

    public Arch getArch() {
        return arch;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
