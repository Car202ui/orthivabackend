package com.orthiva.core.followup;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/orders/{id}/follow-ups. */
public record FollowUpInput(
        @NotNull LocalDate visitDate,
        @Min(1) int treatmentMonth,
        @Size(max = 8000) String notes) {
}
