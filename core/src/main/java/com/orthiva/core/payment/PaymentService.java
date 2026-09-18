package com.orthiva.core.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orthiva.core.order.OrderService;
import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.order.OrderStatusChanged;
import com.orthiva.core.shared.tenant.PlatformScope;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/**
 * Creates the charges the workflow requires. Gateways (Mock, Wompi) and webhooks arrive
 * in delivery 1.6; for now a payment is a PENDING row the doctor can see on the order.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PlatformScope platform;
    private final OrderService orders;
    private final boolean mockEnabled;

    public PaymentService(PaymentRepository payments, PlatformScope platform, OrderService orders,
                          @Value("${orthiva.payments.mock-enabled:false}") boolean mockEnabled) {
        this.payments = payments;
        this.platform = platform;
        this.orders = orders;
        this.mockEnabled = mockEnabled;
    }

    /**
     * Development-only gateway: approves a pending payment on the spot and advances the
     * order (SUBMITTED → DIAGNOSIS_PAID, APPROVED → TREATMENT_PAID). Real gateways (1.6)
     * reach the same {@link #markApproved} through their webhooks.
     */
    @Transactional
    public PaymentDto mockApprove(UUID paymentId) {
        if (!mockEnabled) {
            throw DomainException.forbidden("Mock payments are disabled");
        }
        var actor = TenantContext.require();
        var payment = payments.findById(paymentId).orElseThrow(() -> DomainException.notFound("Payment"));
        if (!actor.hasRole("ADMIN") && !actor.personId().equals(payment.getPayerId())) {
            throw DomainException.notFound("Payment");
        }
        payment.attachGateway("MOCK", "mock-" + UUID.randomUUID());
        return markApproved(payment, Map.of("gateway", "MOCK", "approvedBy", actor.personId().toString()));
    }

    /** Shared by every gateway once a payment is confirmed. */
    PaymentDto markApproved(Payment payment, Map<String, Object> payload) {
        if (payment.getStatus() == Payment.Status.APPROVED) {
            return PaymentDto.from(payment);
        }
        payment.approve(payload);
        OrderStatus next = payment.getPurpose() == Payment.Purpose.DIAGNOSIS
                ? OrderStatus.DIAGNOSIS_PAID : OrderStatus.TREATMENT_PAID;
        orders.systemTransition(payment.getOrderId(), next, payment.getPurpose() + " paid via " + payment.getGateway());
        return PaymentDto.from(payment);
    }

    /**
     * Runs after the order transaction commits (event publication registry), on another
     * thread without request context, hence the platform scope for RLS.
     */
    @ApplicationModuleListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.to() != OrderStatus.SUBMITTED) {
            return;
        }
        platform.run(() -> {
            boolean exists = payments.findFirstByOrderIdAndPurposeAndStatus(
                    event.orderId(), Payment.Purpose.DIAGNOSIS, Payment.Status.PENDING).isPresent();
            if (exists) {
                return;
            }
            BigDecimal amount = event.diagnosisPrice() == null ? BigDecimal.ZERO : event.diagnosisPrice();
            String currency = event.currency() == null ? "COP" : event.currency();
            payments.save(Payment.pending(event.tenantId(), event.orderId(), null, Payment.Purpose.DIAGNOSIS,
                    amount, currency, event.doctorId()));
            log.info("Diagnosis payment created for order {} ({} {})", event.orderNumber(), amount, currency);
        });
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> forOrder(UUID orderId) {
        TenantContext.require();
        return payments.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(PaymentDto::from).toList();
    }
}
