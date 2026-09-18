package com.orthiva.core.order;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST/PUT /api/orders. */
public record OrderInput(
        @NotNull UUID patientId,
        UUID clinicId,
        @NotNull Arch arch,
        Boolean firstTime,
        Boolean reevaluation,
        @Size(max = 4000) String treatmentGoal,
        @Valid List<Movement> movements) {

    public record Movement(
            @Min(11) @Max(48) Short toothFdi,
            @Size(max = 80) String torque,
            @Size(max = 80) String rotation,
            @Size(max = 80) String buccolingual,
            @Size(max = 80) String mesiodistal,
            @Size(max = 80) String intrusionExtrusion,
            @Size(max = 2000) String notes) {
    }
}
