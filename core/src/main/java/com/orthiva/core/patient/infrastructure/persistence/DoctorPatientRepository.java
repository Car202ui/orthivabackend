package com.orthiva.core.patient.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orthiva.core.patient.domain.DoctorPatient;

public interface DoctorPatientRepository extends JpaRepository<DoctorPatient, UUID> {

    Optional<DoctorPatient> findByDoctorIdAndPatientIdAndActiveTrue(UUID doctorId, UUID patientId);

    List<DoctorPatient> findByDoctorIdAndActiveTrue(UUID doctorId);

    List<DoctorPatient> findByPatientIdAndActiveTrue(UUID patientId);
}
