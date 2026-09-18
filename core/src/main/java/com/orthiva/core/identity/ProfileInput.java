package com.orthiva.core.identity;

import java.time.LocalDate;

import com.orthiva.core.shared.persistence.Address;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of PUT /api/me/profile and POST /api/me/onboarding. Doctor-only fields are validated in the service. */
public record ProfileInput(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Size(max = 6) String phoneCountry,
        @Size(max = 20) String phoneNumber,
        @Pattern(regexp = "es|en|pt") String locale,
        Gender gender,
        @Size(max = 30) String documentId,
        LocalDate birthDate,
        @Size(max = 80) String specialty,
        @Size(max = 40) String licenseNumber,
        @Size(min = 2, max = 2) String licenseCountry,
        @Valid Address.Input address) {
}
