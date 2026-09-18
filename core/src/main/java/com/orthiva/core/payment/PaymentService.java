package com.orthiva.core.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.order.OrderStatusChanged;
import com.orthiva.core.shared.tenant.PlatformScope;
import com.orthiva.core.shared.tenant.TenantContext;

/**
 * Creates the charges the workflow requires. Gateways (Mock, Wompi) and webhooks arrive
 * in delivery 1.6; for now a payment is a PENDING row the doctor can see on the order.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PlatformScope platform;

    public PaymentService(PaymentRepository payments, PlatformScope platform) {
        this.payments = payments;
        this.platform = platform;
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
