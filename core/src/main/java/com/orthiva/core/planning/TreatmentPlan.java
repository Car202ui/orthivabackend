package com.orthiva.core.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orthiva.core.shared.persistence.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * The lab's proposal for an order. A new version is created every time the doctor asks
 * for changes; only one version can be unsent (draft) at a time.
 */
@Entity
@Table(name = "treatment_plan")
public class TreatmentPlan extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private int version;

    @Column(name = "planner_id")
    private UUID plannerId;

    private String diagnosis;

    @Column(name = "additional_info")
    private String additionalInfo;

    @Column(name = "upper_stages")
    private Integer upperStages;

    @Column(name = "lower_stages")
    private Integer lowerStages;

    @Column(name = "price_upper")
    private BigDecimal priceUpper;

    @Column(name = "price_lower")
    private BigDecimal priceLower;

    @Column(name = "price_total")
    private BigDecimal priceTotal;

    @Column(nullable = false, length = 3)
    private String currency = "COP";

    @Column(name = "stl_uploaded_at")
    private Instant stlUploadedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("arch asc, stageNumber asc")
    private List<TreatmentStage> stages = new ArrayList<>();

    protected TreatmentPlan() {
    }

    TreatmentPlan(UUID tenantId, UUID orderId, int version, UUID plannerId, String currency) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.version = version;
        this.plannerId = plannerId;
        this.currency = currency;
    }

    void apply(PlanInput in) {
        this.diagnosis = in.diagnosis();
        this.additionalInfo = in.additionalInfo();
        this.upperStages = in.upperStages();
        this.lowerStages = in.lowerStages();
        this.priceUpper = in.priceUpper();
        this.priceLower = in.priceLower();
        this.priceTotal = in.priceTotal() != null ? in.priceTotal()
                : nz(in.priceUpper()).add(nz(in.priceLower()));
        this.stages.clear();
        if (in.stages() != null) {
            in.stages().forEach(s -> this.stages.add(new TreatmentStage(this, s)));
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    void markSent() {
        this.sentAt = Instant.now();
    }

    void markStlUploaded() {
        this.stlUploadedAt = Instant.now();
    }

    public boolean isSent() {
        return sentAt != null;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public int getVersion() {
        return version;
    }

    public UUID getPlannerId() {
        return plannerId;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public Integer getUpperStages() {
        return upperStages;
    }

    public Integer getLowerStages() {
        return lowerStages;
    }

    public BigDecimal getPriceUpper() {
        return priceUpper;
    }

    public BigDecimal getPriceLower() {
        return priceLower;
    }

    public BigDecimal getPriceTotal() {
        return priceTotal;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getStlUploadedAt() {
        return stlUploadedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public List<TreatmentStage> getStages() {
        return stages;
    }
}
