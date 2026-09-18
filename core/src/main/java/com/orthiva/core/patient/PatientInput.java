package com.orthiva.core.patient;

import java.time.LocalDate;

import com.orthiva.core.identity.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientInput(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Email @Size(max = 160) String email,
        @Size(max = 30) String documentId,
        LocalDate birthDate,
        Gender gender,
        @Size(max = 6) String phoneCountry,
        @Size(max = 20) String phoneNumber) {
}
