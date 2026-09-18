package com.orthiva.core.identity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.orthiva.core.identity.domain.Person;
import com.orthiva.core.shared.persistence.Address;

public record PersonDto(
        UUID id,
        UUID tenantId,
        PersonType type,
        String firstName,
        String lastName,
        String email,
        String phoneCountry,
        String phoneNumber,
        String locale,
        Gender gender,
        String documentId,
        LocalDate birthDate,
        String specialty,
        String licenseNumber,
        String licenseCountry,
        Instant verifiedAt,
        boolean profileCompleted,
        boolean hasLogin,
        boolean active,
        Address.Dto address) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    public static PersonDto from(Person p) {
        return new PersonDto(p.getId(), p.getTenantId(), p.getType(), p.getFirstName(), p.getLastName(),
                p.getEmail(), p.getPhoneCountry(), p.getPhoneNumber(), p.getLocale(), p.getGender(),
                p.getDocumentId(), p.getBirthDate(), p.getSpecialty(), p.getLicenseNumber(), p.getLicenseCountry(),
                p.getVerifiedAt(), p.isProfileCompleted(), p.getKeycloakUserId() != null, p.isActive(),
                Address.Dto.from(p.getAddress()));
    }
}
