package com.orthiva.core.patient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orthiva.core.identity.PersonRepository;
import com.orthiva.core.shared.tenant.TenantContext;

/** What a logged-in patient can see about their own care team. */
@RestController
@RequestMapping("/api/portal")
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalController {

    private final DoctorPatientRepository links;
    private final PersonRepository persons;

    public PatientPortalController(DoctorPatientRepository links, PersonRepository persons) {
        this.links = links;
        this.persons = persons;
    }

    public record DoctorSummary(UUID id, String firstName, String lastName, String specialty, LocalDate since) {
    }

    @GetMapping("/doctors")
    @Transactional(readOnly = true)
    public List<DoctorSummary> myDoctors() {
        UUID me = TenantContext.require().personId();
        return links.findByPatientIdAndActiveTrue(me).stream()
                .map(link -> persons.findById(link.getDoctorId())
                        .map(d -> new DoctorSummary(d.getId(), d.getFirstName(), d.getLastName(), d.getSpecialty(), link.getSince()))
                        .orElse(null))
                .filter(d -> d != null)
                .toList();
    }
}
