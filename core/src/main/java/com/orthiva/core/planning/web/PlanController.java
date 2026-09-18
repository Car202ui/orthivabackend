package com.orthiva.core.planning.web;

import com.orthiva.core.planning.PlanDto;
import com.orthiva.core.planning.PlanningService;
import com.orthiva.core.planning.PlanInput;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaKind;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PlanController {

    private final PlanningService service;

    public PlanController(PlanningService service) {
        this.service = service;
    }

    /** Versions of an order's plan: lab sees all, doctor/patient only sent ones. */
    @GetMapping("/orders/{orderId}/plans")
    public List<PlanDto> forOrder(@PathVariable UUID orderId) {
        return service.forOrder(orderId);
    }

    @PostMapping("/orders/{orderId}/plans")
    @PreAuthorize("hasAnyRole('LAB','PLANNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto start(@PathVariable UUID orderId) {
        return service.startPlanning(orderId);
    }

    @GetMapping("/plans/{id}")
    public PlanDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasAnyRole('LAB','PLANNER')")
    public PlanDto update(@PathVariable UUID id, @Valid @RequestBody PlanInput body) {
        return service.update(id, body);
    }

    @PostMapping("/plans/{id}/media")
    @PreAuthorize("hasAnyRole('LAB','PLANNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDto addMedia(@PathVariable UUID id, @RequestParam MediaKind kind, @RequestPart("file") MultipartFile file) {
        return service.addMedia(id, kind, file);
    }

    @DeleteMapping("/plans/{id}/media/{mediaId}")
    @PreAuthorize("hasAnyRole('LAB','PLANNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMedia(@PathVariable UUID id, @PathVariable UUID mediaId) {
        service.removeMedia(id, mediaId);
    }

    @PostMapping("/plans/{id}/send")
    @PreAuthorize("hasAnyRole('LAB','PLANNER')")
    public PlanDto send(@PathVariable UUID id) {
        return service.send(id);
    }
}
