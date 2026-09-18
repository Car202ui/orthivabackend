package com.orthiva.core.patient;

import java.time.LocalDate;
import java.util.UUID;

import com.orthiva.core.identity.Gender;
import com.orthiva.core.identity.Person;

public record PatientDto(UUID id, String firstName, String lastName, String email, String documentId,
                         LocalDate birthDate, Gender gender, String phoneCountry, String phoneNumber,
                         boolean hasLogin, boolean active) {

    public static PatientDto from(Person p) {
        return new PatientDto(p.getId(), p.getFirstName(), p.getLastName(), p.getEmail(), p.getDocumentId(),
                p.getBirthDate(), p.getGender(), p.getPhoneCountry(), p.getPhoneNumber(),
                p.getKeycloakUserId() != null, p.isActive());
    }
}
