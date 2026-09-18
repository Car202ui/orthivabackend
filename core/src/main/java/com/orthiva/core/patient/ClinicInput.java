package com.orthiva.core.patient;

import com.orthiva.core.shared.persistence.Address;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClinicInput(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 160) String website,
        @Size(max = 6) String phoneCountry,
        @Size(max = 20) String phoneNumber,
        @NotNull @Valid Address.Input address) {
}
