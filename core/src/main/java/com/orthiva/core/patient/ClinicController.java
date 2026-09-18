package com.orthiva.core.patient;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clinics")
@PreAuthorize("hasRole('DOCTOR')")
public class ClinicController {

    private final PatientService service;

    public ClinicController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClinicDto> list() {
        return service.myClinics();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicDto create(@Valid @RequestBody ClinicInput body) {
        return service.createClinic(body);
    }

    @PutMapping("/{id}")
    public ClinicDto update(@PathVariable UUID id, @Valid @RequestBody ClinicInput body) {
        return service.updateClinic(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteClinic(id);
    }
}
