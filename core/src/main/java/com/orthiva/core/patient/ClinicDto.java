package com.orthiva.core.patient;

import com.orthiva.core.patient.domain.Clinic;

import java.util.UUID;

import com.orthiva.core.shared.persistence.Address;

public record ClinicDto(UUID id, String name, String website, String phoneCountry, String phoneNumber,
                        Address.Dto address) {

    public static ClinicDto from(Clinic c) {
        return new ClinicDto(c.getId(), c.getName(), c.getWebsite(), c.getPhoneCountry(), c.getPhoneNumber(),
                Address.Dto.from(c.getAddress()));
    }
}
