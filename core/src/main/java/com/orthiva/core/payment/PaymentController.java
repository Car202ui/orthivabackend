package com.orthiva.core.payment;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** Payments of one order (tenant-scoped by RLS; order access is checked by the caller's UI). */
    @GetMapping
    public List<PaymentDto> byOrder(@RequestParam UUID orderId) {
        return service.forOrder(orderId);
    }
}
