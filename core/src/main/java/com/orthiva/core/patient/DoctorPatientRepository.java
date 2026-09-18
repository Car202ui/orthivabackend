package com.orthiva.core.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orthiva.core.identity.Person;

public interface DoctorPatientRepository extends JpaRepository<DoctorPatient, UUID> {

    Optional<DoctorPatient> findByDoctorIdAndPatientIdAndActiveTrue(UUID doctorId, UUID patientId);

    /** Active patients of a doctor, optionally filtered by name / document / email. */
    @Query("""
            select p from Person p
            where p.id in (select dp.patientId from DoctorPatient dp where dp.doctorId = :doctorId and dp.active = true)
              and p.deletedAt is null
              and (:q is null or lower(p.firstName) like :q or lower(p.lastName) like :q
                   or lower(p.documentId) like :q or lower(p.email) like :q)
            order by p.lastName, p.firstName
            """)
    List<Person> searchPatients(@Param("doctorId") UUID doctorId, @Param("q") String q);

    List<DoctorPatient> findByPatientIdAndActiveTrue(UUID patientId);
}
