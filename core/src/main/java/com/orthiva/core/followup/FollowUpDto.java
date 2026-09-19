package com.orthiva.core.followup;

import com.orthiva.core.followup.domain.FollowUp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.orthiva.core.file.MediaDto;

public record FollowUpDto(UUID id, UUID orderId, LocalDate visitDate, int treatmentMonth, String notes,
                          UUID recordedBy, String recordedByName, Instant createdAt, List<MediaDto> media) {

    public static FollowUpDto of(FollowUp f, String recordedByName, List<MediaDto> media) {
        return new FollowUpDto(f.getId(), f.getOrderId(), f.getVisitDate(), f.getTreatmentMonth(), f.getNotes(),
                f.getRecordedBy(), recordedByName, f.getCreatedAt(), media);
    }
}
