package com.orthiva.core.payment.web;

import com.orthiva.core.payment.CheckoutSessionDto;
import com.orthiva.core.payment.PaymentDto;
import com.orthiva.core.payment.PaymentService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    /** Body of POST /api/payments/{id}/checkout: where the provider sends the payer afterwards. */
    public record CheckoutInput(@NotBlank @Size(max = 500) String returnUrl) {
    }

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** Payments of one order (tenant-scoped by RLS; order access is checked by the caller's UI). */
    @GetMapping
    public List<PaymentDto> byOrder(@RequestParam UUID orderId) {
        return service.forOrder(orderId);
    }

    @GetMapping("/{id}")
    public PaymentDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Opens a checkout at the active gateway; the UI redirects the payer to {@code checkoutUrl}. */
    @PostMapping("/{id}/checkout")
    public CheckoutSessionDto checkout(@PathVariable UUID id, @Valid @RequestBody CheckoutInput body) {
        return service.checkout(id, body.returnUrl());
    }

    /**
     * Provider webhooks (public, see SecurityConfig): {@code wompi} verifies X-Event-Checksum,
     * {@code mock} is the dev page posting the tester's decision. Idempotent per reference.
     */
    @PostMapping("/webhooks/{gateway}")
    public Map<String, String> webhook(@PathVariable String gateway, @RequestHeader Map<String, String> headers,
                                       @RequestBody String rawBody) {
        // Providers only need an acknowledgement; never echo payment details on a public endpoint.
        var result = service.handleWebhook(gateway, headers, rawBody);
        return Map.of("status", result.status().name());
    }

    /** Dev-only shortcut (orthiva.payments.mock-enabled=true): approve without a checkout. */
    @PostMapping("/{id}/mock-approve")
    public PaymentDto mockApprove(@PathVariable UUID id) {
        return service.mockApprove(id);
    }
}
