package com.orthiva.core.followup.web;

import com.orthiva.core.followup.FollowUpDto;
import com.orthiva.core.followup.FollowUpInput;
import com.orthiva.core.followup.FollowUpService;

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
public class FollowUpController {

    private final FollowUpService service;

    public FollowUpController(FollowUpService service) {
        this.service = service;
    }

    @GetMapping("/orders/{orderId}/follow-ups")
    public List<FollowUpDto> forOrder(@PathVariable UUID orderId) {
        return service.forOrder(orderId);
    }

    @PostMapping("/orders/{orderId}/follow-ups")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public FollowUpDto create(@PathVariable UUID orderId, @Valid @RequestBody FollowUpInput body) {
        return service.create(orderId, body);
    }

    @PutMapping("/follow-ups/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public FollowUpDto update(@PathVariable UUID id, @Valid @RequestBody FollowUpInput body) {
        return service.update(id, body);
    }

    @DeleteMapping("/follow-ups/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/follow-ups/{id}/media")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDto addMedia(@PathVariable UUID id, @RequestParam MediaKind kind, @RequestPart("file") MultipartFile file) {
        return service.addMedia(id, kind, file);
    }

    @DeleteMapping("/follow-ups/{id}/media/{mediaId}")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMedia(@PathVariable UUID id, @PathVariable UUID mediaId) {
        service.removeMedia(id, mediaId);
    }
}
