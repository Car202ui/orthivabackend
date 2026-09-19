package com.orthiva.core.planning;

import com.orthiva.core.planning.domain.PlanApproval;
import com.orthiva.core.planning.domain.PlanComment;
import com.orthiva.core.planning.domain.TreatmentPlan;
import com.orthiva.core.planning.domain.TreatmentStage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.order.Arch;
import com.orthiva.core.shared.persistence.Address;

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
        List<MediaDto> media,
        List<CommentDto> comments,
        ApprovalDto approval) {

    public record StageDto(UUID id, int stageNumber, Arch arch, String description, BigDecimal cost) {
        public static StageDto from(TreatmentStage s) {
            return new StageDto(s.getId(), s.getStageNumber(), s.getArch(), s.getDescription(), s.getCost());
        }
    }

    public record CommentDto(UUID id, UUID authorId, String authorName, String body, Instant createdAt) {
        public static CommentDto from(PlanComment c, String authorName) {
            return new CommentDto(c.getId(), c.getAuthorId(), authorName, c.getBody(), c.getCreatedAt());
        }
    }

    public record ApprovalDto(UUID id, UUID approvedBy, String approvedByName, String shipToClinicName,
                              Address.Dto shipAddress, String shippingInstructions, String agreementText,
                              Instant approvedAt) {
        public static ApprovalDto from(PlanApproval a, String approvedByName) {
            return new ApprovalDto(a.getId(), a.getApprovedBy(), approvedByName, a.getShipToClinicName(),
                    Address.Dto.from(a.getShipAddress()), a.getShippingInstructions(), a.getAgreementText(),
                    a.getApprovedAt());
        }
    }

    public static PlanDto of(TreatmentPlan p, String plannerName, List<MediaDto> media, List<CommentDto> comments,
                             ApprovalDto approval) {
        return new PlanDto(p.getId(), p.getOrderId(), p.getVersion(), p.isSent(), p.getPlannerId(), plannerName,
                p.getDiagnosis(), p.getAdditionalInfo(), p.getUpperStages(), p.getLowerStages(),
                p.getPriceUpper(), p.getPriceLower(), p.getPriceTotal(), p.getCurrency(),
                p.getStlUploadedAt(), p.getSentAt(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getStages().stream().map(StageDto::from).toList(), media, comments, approval);
    }
}
