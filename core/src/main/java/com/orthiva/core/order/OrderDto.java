package com.orthiva.core.order;

import com.orthiva.core.order.domain.ToothMovementDetail;
import com.orthiva.core.order.domain.TreatmentOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.orthiva.core.file.MediaDto;

/** Order as the API exposes it. {@code summary()} omits the heavy parts for lists. */
public record OrderDto(
        UUID id,
        Long orderNumber,
        OrderStatus status,
        UUID doctorId,
        String doctorName,
        UUID patientId,
        String patientName,
        UUID clinicId,
        Arch arch,
        boolean firstTime,
        boolean reevaluation,
        String treatmentGoal,
        BigDecimal diagnosisPrice,
        String currency,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt,
        List<MovementDto> movements,
        List<MediaDto> media,
        List<HistoryDto> history) {

    public record MovementDto(UUID id, Short toothFdi, String torque, String rotation, String buccolingual,
                              String mesiodistal, String intrusionExtrusion, String notes) {
        public static MovementDto from(ToothMovementDetail m) {
            return new MovementDto(m.getId(), m.getToothFdi(), m.getTorque(), m.getRotation(), m.getBuccolingual(),
                    m.getMesiodistal(), m.getIntrusionExtrusion(), m.getNotes());
        }
    }

    public record HistoryDto(OrderStatus fromStatus, OrderStatus toStatus, UUID changedBy, String changedByName,
                             String note, Instant changedAt) {
    }

    public static OrderDto of(TreatmentOrder o, String doctorName, String patientName, List<MediaDto> media,
                       List<HistoryDto> history) {
        return new OrderDto(o.getId(), o.getOrderNumber(), o.getStatus(), o.getDoctorId(), doctorName,
                o.getPatientId(), patientName, o.getClinicId(), o.getArch(), o.isFirstTime(), o.isReevaluation(),
                o.getTreatmentGoal(), o.getDiagnosisPrice(), o.getCurrency(), o.getSubmittedAt(),
                o.getCreatedAt(), o.getUpdatedAt(),
                o.getMovements().stream().map(MovementDto::from).toList(), media, history);
    }
}
