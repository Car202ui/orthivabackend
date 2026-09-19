package com.orthiva.core.payment.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orthiva.core.order.OrderService;
import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.order.OrderStatusChanged;
import com.orthiva.core.payment.CheckoutSessionDto;
import com.orthiva.core.payment.PaymentApproved;
import com.orthiva.core.payment.PaymentDto;
import com.orthiva.core.payment.PaymentPurpose;
import com.orthiva.core.payment.PaymentService;
import com.orthiva.core.payment.PaymentStatus;
import com.orthiva.core.payment.application.gateway.PaymentGateway;
import com.orthiva.core.payment.domain.Payment;
import com.orthiva.core.payment.infrastructure.persistence.PaymentRepository;
import com.orthiva.core.planning.PlanApproved;
import com.orthiva.core.shared.tenant.PlatformScope;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/**
 * Creates the charges the workflow requires (diagnosis on submit, treatment on approval),
 * opens checkouts at the active gateway and applies the gateway's webhooks.
 */
@Service
class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final Set<String> LAB_ROLES = Set.of("LAB", "PLANNER", "PRODUCTION", "ACCOUNTING", "ADMIN");

    private final PaymentRepository payments;
    private final PlatformScope platform;
    private final OrderService orders;
    private final List<PaymentGateway> gateways;
    private final ApplicationEventPublisher events;
    private final boolean mockEnabled;

    PaymentServiceImpl(PaymentRepository payments, PlatformScope platform, OrderService orders,
                       List<PaymentGateway> gateways, ApplicationEventPublisher events,
                       @Value("${orthiva.payments.mock-enabled:false}") boolean mockEnabled) {
        this.payments = payments;
        this.platform = platform;
        this.orders = orders;
        this.gateways = gateways;
        this.events = events;
        this.mockEnabled = mockEnabled;
    }

    // ------------------------------------------------------------------ checkout

    @Override
    @Transactional
    public CheckoutSessionDto checkout(UUID paymentId, String returnUrl) {
        var actor = TenantContext.require();
        var payment = payments.findById(paymentId).orElseThrow(() -> DomainException.notFound("Payment"));
        if (!actor.hasRole("ADMIN") && !actor.personId().equals(payment.getPayerId())) {
            throw DomainException.notFound("Payment");
        }
        if (payment.getStatus() == PaymentStatus.APPROVED) {
            throw DomainException.conflict("payment_already_approved", "This payment is already approved");
        }
        var gateway = activeGateway();
        var session = gateway.createCheckout(toDto(payment), returnUrl);
        // A new checkout always gets a new reference: the previous attempt may still be pending at the provider.
        payment.attachGateway(gateway.name(), session.reference());
        return new CheckoutSessionDto(payment.getId(), gateway.name(), session.reference(), session.checkoutUrl());
    }

    /** Wompi when configured, otherwise the dev Mock; production without a real gateway is a configuration error. */
    private PaymentGateway activeGateway() {
        return gateways.stream().filter(g -> !"MOCK".equals(g.name()) && g.enabled()).findFirst()
                .or(() -> gateways.stream().filter(g -> "MOCK".equals(g.name()) && g.enabled()).findFirst())
                .orElseThrow(() -> DomainException.conflict("no_gateway", "No payment gateway is configured"));
    }

    // ------------------------------------------------------------------ webhooks

    /** No JWT here: the provider is authenticated by its signature, and RLS is bypassed through the platform scope. */
    @Override
    public PaymentDto handleWebhook(String gatewayName, Map<String, String> headers, String rawBody) {
        var gateway = gateways.stream().filter(g -> g.name().equalsIgnoreCase(gatewayName)).findFirst()
                .orElseThrow(() -> DomainException.notFound("Gateway"));
        var result = gateway.handleWebhook(headers, rawBody);
        return platform.run(() -> {
            var payment = payments.findByGatewayAndGatewayReference(gateway.name(), result.reference())
                    .orElseThrow(() -> DomainException.notFound("Payment"));
            return switch (result.status()) {
                case APPROVED -> markApproved(payment, result.payload());
                case DECLINED, ERROR -> {
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        payment.decline(result.payload());
                    }
                    yield toDto(payment);
                }
                default -> toDto(payment);   // still pending at the provider: nothing to do
            };
        });
    }

    /**
     * Development-only shortcut kept for API tests: approves a pending payment on the spot.
     * The UI now goes through {@link #checkout} and the Mock gateway's page instead.
     */
    @Override
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

    /** Shared by every gateway once a payment is confirmed; idempotent. */
    PaymentDto markApproved(Payment payment, Map<String, Object> payload) {
        if (payment.getStatus() == PaymentStatus.APPROVED) {
            return toDto(payment);
        }
        payment.approve(payload);
        OrderStatus next = payment.getPurpose() == PaymentPurpose.DIAGNOSIS
                ? OrderStatus.DIAGNOSIS_PAID : OrderStatus.TREATMENT_PAID;
        orders.systemTransition(payment.getOrderId(), next, payment.getPurpose() + " paid via " + payment.getGateway());
        events.publishEvent(new PaymentApproved(payment.getId(), payment.getOrderId(), payment.getTenantId(),
                payment.getPayerId(), payment.getPurpose(), payment.getAmount(), payment.getCurrency(), payment.getGateway()));
        return toDto(payment);
    }

    // ------------------------------------------------------------------ charges created by the workflow

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
                    event.orderId(), PaymentPurpose.DIAGNOSIS, PaymentStatus.PENDING).isPresent();
            if (exists) {
                return;
            }
            BigDecimal amount = event.diagnosisPrice() == null ? BigDecimal.ZERO : event.diagnosisPrice();
            String currency = event.currency() == null ? "COP" : event.currency();
            payments.save(Payment.pending(event.tenantId(), event.orderId(), null, PaymentPurpose.DIAGNOSIS,
                    amount, currency, event.doctorId()));
            log.info("Diagnosis payment created for order {} ({} {})", event.orderNumber(), amount, currency);
        });
    }

    /** The doctor approved a plan: charge the treatment at the price of that version. */
    @ApplicationModuleListener
    public void onPlanApproved(PlanApproved event) {
        platform.run(() -> {
            boolean exists = payments.findFirstByOrderIdAndPurposeAndStatus(
                    event.orderId(), PaymentPurpose.TREATMENT, PaymentStatus.PENDING).isPresent();
            if (exists) {
                return;
            }
            BigDecimal amount = event.priceTotal() == null ? BigDecimal.ZERO : event.priceTotal();
            String currency = event.currency() == null ? "COP" : event.currency();
            payments.save(Payment.pending(event.tenantId(), event.orderId(), event.planId(), PaymentPurpose.TREATMENT,
                    amount, currency, event.doctorId()));
            log.info("Treatment payment created for order {} plan v{} ({} {})", event.orderId(), event.version(),
                    amount, currency);
        });
    }

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDto> forOrder(UUID orderId) {
        TenantContext.require();
        return payments.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(PaymentServiceImpl::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDto get(UUID paymentId) {
        var actor = TenantContext.require();
        var payment = payments.findById(paymentId).orElseThrow(() -> DomainException.notFound("Payment"));
        boolean lab = actor.roles().stream().anyMatch(LAB_ROLES::contains);
        if (!lab && !actor.personId().equals(payment.getPayerId())) {
            throw DomainException.notFound("Payment");
        }
        return toDto(payment);
    }

    static PaymentDto toDto(Payment p) {
        return new PaymentDto(p.getId(), p.getOrderId(), p.getPlanId(), p.getPurpose(), p.getStatus(), p.getAmount(),
                p.getCurrency(), p.getGateway(), p.getGatewayReference(), p.getPaidAt(), p.getCreatedAt());
    }
}
