package com.orthiva.core.patient.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orthiva.core.patient.PatientService;

/** What a logged-in patient can see about their own care team. */
@RestController
@RequestMapping("/api/portal")
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalController {

    private final PatientService service;

    public PatientPortalController(PatientService service) {
        this.service = service;
    }

    @GetMapping("/doctors")
    public List<PatientService.DoctorSummary> myDoctors() {
        return service.myDoctors();
    }
}
