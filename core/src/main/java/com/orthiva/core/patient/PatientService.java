package com.orthiva.core.patient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public API of the patient module: a doctor's patients and clinics, and a patient's care team. */
public interface PatientService {

    // ---- patients (acting doctor) ---------------------------------------------------

    List<PatientDto> myPatients(String query);

    PatientDto myPatient(UUID patientId);

    PatientDto createPatient(PatientInput in);

    PatientDto updatePatient(UUID patientId, PatientInput in);

    /** Fails with 404 unless the patient is actively linked to the acting doctor. */
    void assertMyPatient(UUID patientId);

    // ---- clinics (acting doctor) ----------------------------------------------------

    List<ClinicDto> myClinics();

    ClinicDto createClinic(ClinicInput in);

    ClinicDto updateClinic(UUID id, ClinicInput in);

    void deleteClinic(UUID id);

    /** Fails with 404 unless the clinic belongs to the acting doctor. */
    void assertMyClinic(UUID clinicId);

    // ---- patient portal -------------------------------------------------------------

    List<DoctorSummary> myDoctors();

    record DoctorSummary(UUID id, String firstName, String lastName, String specialty, LocalDate since) {
    }
}
