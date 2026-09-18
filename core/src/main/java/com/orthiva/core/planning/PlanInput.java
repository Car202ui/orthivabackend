package com.orthiva.core.planning;

import java.math.BigDecimal;
import java.util.List;

import com.orthiva.core.order.Arch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body of PUT /api/plans/{id}. */
public record PlanInput(
        @Size(max = 8000) String diagnosis,
        @Size(max = 8000) String additionalInfo,
        @Min(0) Integer upperStages,
        @Min(0) Integer lowerStages,
        @PositiveOrZero BigDecimal priceUpper,
        @PositiveOrZero BigDecimal priceLower,
        @PositiveOrZero BigDecimal priceTotal,
        @Valid List<Stage> stages) {

    public record Stage(
            @Min(1) int stageNumber,
            @NotNull Arch arch,
            @Size(max = 2000) String description,
            @PositiveOrZero BigDecimal cost) {
    }
}
