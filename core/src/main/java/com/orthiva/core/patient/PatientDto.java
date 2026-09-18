package com.orthiva.core.patient;

import java.time.LocalDate;
import java.util.UUID;

import com.orthiva.core.identity.Gender;
import com.orthiva.core.identity.PersonDto;

public record PatientDto(UUID id, String firstName, String lastName, String email, String documentId,
                         LocalDate birthDate, Gender gender, String phoneCountry, String phoneNumber,
                         boolean hasLogin, boolean active) {

    public static PatientDto from(PersonDto p) {
        return new PatientDto(p.id(), p.firstName(), p.lastName(), p.email(), p.documentId(),
                p.birthDate(), p.gender(), p.phoneCountry(), p.phoneNumber(), p.hasLogin(), p.active());
    }
}
