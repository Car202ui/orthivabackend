package com.orthiva.core.patient.infrastructure.persistence;

import com.orthiva.core.patient.domain.Clinic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {

    List<Clinic> findByDoctorIdAndDeletedAtIsNullOrderByNameAsc(UUID doctorId);

    Optional<Clinic> findByIdAndDoctorIdAndDeletedAtIsNull(UUID id, UUID doctorId);
}
