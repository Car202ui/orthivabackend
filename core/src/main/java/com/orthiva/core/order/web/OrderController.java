package com.orthiva.core.order.web;

import com.orthiva.core.order.OrderDto;
import com.orthiva.core.order.OrderService;
import com.orthiva.core.order.OrderInput;
import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.order.ShipmentInput;

import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** Doctors see their own orders, patients theirs, lab roles every order of the tenant. */
    @GetMapping
    public List<OrderDto> list(@RequestParam(required = false) OrderStatus status,
                               @RequestParam(required = false) UUID patientId) {
        return service.list(status, patientId);
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto create(@Valid @RequestBody OrderInput body) {
        return service.createDraft(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public OrderDto update(@PathVariable UUID id, @Valid @RequestBody OrderInput body) {
        return service.updateDraft(id, body);
    }

    @PostMapping("/{id}/media")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDto addMedia(@PathVariable UUID id, @RequestParam MediaKind kind, @RequestPart("file") MultipartFile file) {
        return service.addMedia(id, kind, file);
    }

    @DeleteMapping("/{id}/media/{mediaId}")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMedia(@PathVariable UUID id, @PathVariable UUID mediaId) {
        service.removeMedia(id, mediaId);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('DOCTOR')")
    public OrderDto submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    public OrderDto cancel(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return service.cancel(id, body == null ? null : body.get("note"));
    }

    // ---- laboratory: production and shipping ---------------------------------------------

    @PostMapping("/{id}/production/start")
    @PreAuthorize("hasAnyRole('LAB','PRODUCTION')")
    public OrderDto startProduction(@PathVariable UUID id) {
        return service.startProduction(id);
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('LAB','PRODUCTION')")
    public OrderDto ship(@PathVariable UUID id, @Valid @RequestBody(required = false) ShipmentInput body) {
        return service.ship(id, body == null ? new ShipmentInput(null, null, null) : body);
    }

    /** Ends the treatment; doctor or lab. */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('DOCTOR','LAB')")
    public OrderDto close(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return service.close(id, body == null ? null : body.get("note"));
    }
}
