package com.orthiva.core.followup;

import java.time.LocalDate;
import java.util.UUID;

/** Published when the doctor records a check-up (the patient gets notified). */
public record FollowUpRecorded(UUID followUpId, UUID orderId, Long orderNumber, UUID tenantId, UUID doctorId, UUID patientId,
                               int treatmentMonth, LocalDate visitDate) {
}
