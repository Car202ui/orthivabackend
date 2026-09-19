package com.orthiva.core.planning;

import com.orthiva.core.shared.persistence.Address;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/plans/{id}/approve: where to ship the aligners and the accepted agreement. */
public record ApprovalInput(
        @NotBlank @Size(max = 120) String shipToClinicName,
        @NotNull @Valid Address.Input address,
        @Size(max = 2000) String shippingInstructions,
        @AssertTrue(message = "The responsibility agreement must be accepted") boolean agreementAccepted) {
}
