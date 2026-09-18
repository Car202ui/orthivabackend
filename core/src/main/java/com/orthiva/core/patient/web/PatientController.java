package com.orthiva.core.patient.web;

import com.orthiva.core.patient.PatientService;
import com.orthiva.core.patient.PatientDto;
import com.orthiva.core.patient.PatientInput;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patients")
@PreAuthorize("hasRole('DOCTOR')")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    public List<PatientDto> list(@RequestParam(required = false) String q) {
        return service.myPatients(q);
    }

    @GetMapping("/{id}")
    public PatientDto get(@PathVariable UUID id) {
        return service.myPatient(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientDto create(@Valid @RequestBody PatientInput body) {
        return service.createPatient(body);
    }

    @PutMapping("/{id}")
    public PatientDto update(@PathVariable UUID id, @Valid @RequestBody PatientInput body) {
        return service.updatePatient(id, body);
    }
}
