package com.orthiva.core.payment;

import java.util.List;
import java.util.UUID;

/** Public API of the payment module: charges attached to orders and how they get approved. */
public interface PaymentService {

    List<PaymentDto> forOrder(UUID orderId);

    /** Development-only gateway (orthiva.payments.mock-enabled): approves on the spot and advances the order. */
    PaymentDto mockApprove(UUID paymentId);
}
