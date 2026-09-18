package com.orthiva.core.patient;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orthiva.core.identity.Person;
import com.orthiva.core.identity.PersonRepository;
import com.orthiva.core.identity.PersonType;
import com.orthiva.core.shared.persistence.AddressRepository;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/**
 * Doctor-facing management of clinics and patients. Every query is already scoped to the
 * tenant by RLS; here we additionally scope to the acting doctor. Public methods return
 * DTOs so lazy associations are resolved before the transaction ends.
 */
@Service
@Transactional
public class PatientService {

    private final PersonRepository persons;
    private final DoctorPatientRepository links;
    private final ClinicRepository clinics;
    private final AddressRepository addresses;

    public PatientService(PersonRepository persons, DoctorPatientRepository links, ClinicRepository clinics,
                          AddressRepository addresses) {
        this.persons = persons;
        this.links = links;
        this.clinics = clinics;
        this.addresses = addresses;
    }

    // ------------------------------------------------------------------ patients

    @Transactional(readOnly = true)
    public List<PatientDto> myPatients(String query) {
        var actor = requireDoctor();
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return links.searchPatients(actor.personId(), q).stream().map(PatientDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PatientDto myPatient(UUID patientId) {
        return PatientDto.from(loadMyPatient(patientId));
    }

    public PatientDto createPatient(PatientInput in) {
        var actor = requireDoctor();
        // Same email + same tenant = same patient (whether they registered themselves or another
        // doctor created them): link, never duplicate. RLS already limits the lookup to this tenant.
        Person patient = null;
        if (in.email() != null && !in.email().isBlank()) {
            patient = persons.findFirstByEmailIgnoreCaseAndTypeAndDeletedAtIsNull(in.email(), PersonType.PATIENT).orElse(null);
        }
        if (patient == null) {
            patient = persons.save(Person.newPatient(actor.tenantId(), in.firstName(), in.lastName(), in.email(),
                    in.documentId(), in.birthDate(), in.gender()));
            patient.updateBasics(in.firstName(), in.lastName(), in.email(), in.documentId(), in.birthDate(),
                    in.gender(), in.phoneCountry(), in.phoneNumber());
        }
        if (links.findByDoctorIdAndPatientIdAndActiveTrue(actor.personId(), patient.getId()).isEmpty()) {
            links.save(new DoctorPatient(actor.tenantId(), actor.personId(), patient.getId()));
        }
        return PatientDto.from(patient);
    }

    public PatientDto updatePatient(UUID patientId, PatientInput in) {
        Person patient = loadMyPatient(patientId);
        patient.updateBasics(in.firstName(), in.lastName(), in.email(), in.documentId(), in.birthDate(),
                in.gender(), in.phoneCountry(), in.phoneNumber());
        return PatientDto.from(patient);
    }

    /** For other modules: fails with 404 unless the patient is actively linked to the acting doctor. */
    @Transactional(readOnly = true)
    public void assertMyPatient(UUID patientId) {
        loadMyPatient(patientId);
    }

    /** For other modules: fails with 404 unless the clinic belongs to the acting doctor. */
    @Transactional(readOnly = true)
    public void assertMyClinic(UUID clinicId) {
        myClinic(clinicId);
    }

    private Person loadMyPatient(UUID patientId) {
        var actor = requireDoctor();
        links.findByDoctorIdAndPatientIdAndActiveTrue(actor.personId(), patientId)
                .orElseThrow(() -> DomainException.notFound("Patient"));
        return persons.findById(patientId).orElseThrow(() -> DomainException.notFound("Patient"));
    }

    // ------------------------------------------------------------------ clinics

    @Transactional(readOnly = true)
    public List<ClinicDto> myClinics() {
        return clinics.findByDoctorIdAndDeletedAtIsNullOrderByNameAsc(requireDoctor().personId())
                .stream().map(ClinicDto::from).toList();
    }

    public ClinicDto createClinic(ClinicInput in) {
        var actor = requireDoctor();
        var address = addresses.save(in.address().toEntity());
        return ClinicDto.from(clinics.save(new Clinic(actor.tenantId(), actor.personId(), in, address)));
    }

    public ClinicDto updateClinic(UUID id, ClinicInput in) {
        Clinic clinic = myClinic(id);
        clinic.apply(in);
        if (clinic.getAddress() == null) {
            clinic.setAddress(addresses.save(in.address().toEntity()));
        } else {
            in.address().applyTo(clinic.getAddress());
        }
        return ClinicDto.from(clinic);
    }

    public void deleteClinic(UUID id) {
        myClinic(id).softDelete();
    }

    private Clinic myClinic(UUID id) {
        return clinics.findByIdAndDoctorIdAndDeletedAtIsNull(id, requireDoctor().personId())
                .orElseThrow(() -> DomainException.notFound("Clinic"));
    }

    private static TenantContext.Actor requireDoctor() {
        var actor = TenantContext.require();
        if (!actor.hasRole("DOCTOR")) {
            throw DomainException.forbidden("Only doctors can manage patients and clinics");
        }
        return actor;
    }
}
