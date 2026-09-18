package com.orthiva.core.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.order.Arch;

public record PlanDto(
        UUID id,
        UUID orderId,
        int version,
        boolean sent,
        UUID plannerId,
        String plannerName,
        String diagnosis,
        String additionalInfo,
        Integer upperStages,
        Integer lowerStages,
        BigDecimal priceUpper,
        BigDecimal priceLower,
        BigDecimal priceTotal,
        String currency,
        Instant stlUploadedAt,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt,
        List<StageDto> stages,
        List<MediaDto> media) {

    public record StageDto(UUID id, int stageNumber, Arch arch, String description, BigDecimal cost) {
        static StageDto from(TreatmentStage s) {
            return new StageDto(s.getId(), s.getStageNumber(), s.getArch(), s.getDescription(), s.getCost());
        }
    }

    static PlanDto of(TreatmentPlan p, String plannerName, List<MediaDto> media) {
        return new PlanDto(p.getId(), p.getOrderId(), p.getVersion(), p.isSent(), p.getPlannerId(), plannerName,
                p.getDiagnosis(), p.getAdditionalInfo(), p.getUpperStages(), p.getLowerStages(),
                p.getPriceUpper(), p.getPriceLower(), p.getPriceTotal(), p.getCurrency(),
                p.getStlUploadedAt(), p.getSentAt(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getStages().stream().map(StageDto::from).toList(), media);
    }
}
