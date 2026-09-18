package com.orthiva.core.patient.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orthiva.core.identity.IdentityService;
import com.orthiva.core.identity.PersonDto;
import com.orthiva.core.identity.PersonType;
import com.orthiva.core.patient.ClinicDto;
import com.orthiva.core.patient.ClinicInput;
import com.orthiva.core.patient.PatientDto;
import com.orthiva.core.patient.PatientInput;
import com.orthiva.core.patient.PatientService;
import com.orthiva.core.patient.domain.Clinic;
import com.orthiva.core.patient.domain.DoctorPatient;
import com.orthiva.core.patient.infrastructure.persistence.ClinicRepository;
import com.orthiva.core.patient.infrastructure.persistence.DoctorPatientRepository;
import com.orthiva.core.shared.persistence.AddressRepository;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/**
 * Doctor-facing management of clinics and patients. Every query is already scoped to the
 * tenant by RLS; here we additionally scope to the acting doctor. People data comes from
 * the identity module's public API — this module owns only the doctor↔patient links and clinics.
 */
@Service
@Transactional
class PatientServiceImpl implements PatientService {

    private final IdentityService identity;
    private final DoctorPatientRepository links;
    private final ClinicRepository clinics;
    private final AddressRepository addresses;

    PatientServiceImpl(IdentityService identity, DoctorPatientRepository links, ClinicRepository clinics,
                       AddressRepository addresses) {
        this.identity = identity;
        this.links = links;
        this.clinics = clinics;
        this.addresses = addresses;
    }

    // ------------------------------------------------------------------ patients

    @Override
    @Transactional(readOnly = true)
    public List<PatientDto> myPatients(String query) {
        var actor = requireDoctor();
        Set<UUID> ids = links.findByDoctorIdAndActiveTrue(actor.personId()).stream()
                .map(DoctorPatient::getPatientId).collect(Collectors.toSet());
        return identity.searchByIds(ids, query).stream().map(PatientDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PatientDto myPatient(UUID patientId) {
        assertMyPatient(patientId);
        return PatientDto.from(identity.get(patientId));
    }

    @Override
    public PatientDto createPatient(PatientInput in) {
        var actor = requireDoctor();
        var basics = new IdentityService.PatientBasics(in.firstName(), in.lastName(), in.email(), in.documentId(),
                in.birthDate(), in.gender(), in.phoneCountry(), in.phoneNumber());
        // Same email + same tenant = same patient (whether they registered themselves or another
        // doctor created them): link, never duplicate. RLS already limits the lookup to this tenant.
        PersonDto patient = null;
        if (in.email() != null && !in.email().isBlank()) {
            patient = identity.findByEmailAndType(in.email(), PersonType.PATIENT).orElse(null);
        }
        if (patient == null) {
            patient = identity.createPatient(actor.tenantId(), basics);
        }
        if (links.findByDoctorIdAndPatientIdAndActiveTrue(actor.personId(), patient.id()).isEmpty()) {
            links.save(new DoctorPatient(actor.tenantId(), actor.personId(), patient.id()));
        }
        return PatientDto.from(patient);
    }

    @Override
    public PatientDto updatePatient(UUID patientId, PatientInput in) {
        assertMyPatient(patientId);
        return PatientDto.from(identity.updatePatientBasics(patientId, new IdentityService.PatientBasics(
                in.firstName(), in.lastName(), in.email(), in.documentId(), in.birthDate(), in.gender(),
                in.phoneCountry(), in.phoneNumber())));
    }

    @Override
    @Transactional(readOnly = true)
    public void assertMyPatient(UUID patientId) {
        var actor = requireDoctor();
        links.findByDoctorIdAndPatientIdAndActiveTrue(actor.personId(), patientId)
                .orElseThrow(() -> DomainException.notFound("Patient"));
    }

    // ------------------------------------------------------------------ clinics

    @Override
    @Transactional(readOnly = true)
    public List<ClinicDto> myClinics() {
        return clinics.findByDoctorIdAndDeletedAtIsNullOrderByNameAsc(requireDoctor().personId())
                .stream().map(ClinicDto::from).toList();
    }

    @Override
    public ClinicDto createClinic(ClinicInput in) {
        var actor = requireDoctor();
        var address = addresses.save(in.address().toEntity());
        return ClinicDto.from(clinics.save(new Clinic(actor.tenantId(), actor.personId(), in, address)));
    }

    @Override
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

    @Override
    public void deleteClinic(UUID id) {
        myClinic(id).softDelete();
    }

    @Override
    @Transactional(readOnly = true)
    public void assertMyClinic(UUID clinicId) {
        myClinic(clinicId);
    }

    private Clinic myClinic(UUID id) {
        return clinics.findByIdAndDoctorIdAndDeletedAtIsNull(id, requireDoctor().personId())
                .orElseThrow(() -> DomainException.notFound("Clinic"));
    }

    // ------------------------------------------------------------------ patient portal

    @Override
    @Transactional(readOnly = true)
    public List<DoctorSummary> myDoctors() {
        UUID me = TenantContext.require().personId();
        return links.findByPatientIdAndActiveTrue(me).stream()
                .map(link -> {
                    var d = identity.get(link.getDoctorId());
                    return new DoctorSummary(d.id(), d.firstName(), d.lastName(), d.specialty(), link.getSince());
                })
                .toList();
    }

    private static TenantContext.Actor requireDoctor() {
        var actor = TenantContext.require();
        if (!actor.hasRole("DOCTOR")) {
            throw DomainException.forbidden("Only doctors can manage patients and clinics");
        }
        return actor;
    }
}
