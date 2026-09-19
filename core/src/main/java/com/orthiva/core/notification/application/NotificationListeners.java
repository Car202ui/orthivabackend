package com.orthiva.core.notification.application;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.orthiva.core.followup.FollowUpRecorded;
import com.orthiva.core.identity.IdentityService;
import com.orthiva.core.identity.PersonDto;
import com.orthiva.core.identity.PersonType;
import com.orthiva.core.order.OrderStatusChanged;
import com.orthiva.core.payment.PaymentApproved;
import com.orthiva.core.planning.PlanApproved;
import com.orthiva.core.planning.PlanSent;
import com.orthiva.core.shared.tenant.PlatformScope;

/**
 * Turns domain events into e-mails. Runs after the publishing transaction commits, on a
 * worker thread without request context, hence the platform scope for every lookup.
 * Each recipient gets the mail in their own language ({@code person.locale}).
 */
@Component
class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);
    private static final Set<PersonType> LAB_STAFF = Set.of(PersonType.LAB, PersonType.PLANNER);
    private static final Set<PersonType> PRODUCTION_STAFF = Set.of(PersonType.LAB, PersonType.PRODUCTION);

    private final Mailer mailer;
    private final IdentityService identity;
    private final PlatformScope platform;

    NotificationListeners(Mailer mailer, IdentityService identity, PlatformScope platform) {
        this.mailer = mailer;
        this.identity = identity;
        this.platform = platform;
    }

    @ApplicationModuleListener
    public void onOrderStatusChanged(OrderStatusChanged e) {
        platform.run(() -> {
            var model = base(e.tenantId(), e.orderNumber(), e.orderId());
            switch (e.to()) {
                case SUBMITTED -> {
                    var doctor = identity.get(e.doctorId());
                    model.put("doctorName", doctor.fullName());
                    model.put("patientName", identity.get(e.patientId()).fullName());
                    toStaff(e.tenantId(), LAB_STAFF, "order-submitted", model);
                }
                case CHANGES_REQUESTED -> {
                    model.put("doctorName", identity.get(e.doctorId()).fullName());
                    toStaff(e.tenantId(), LAB_STAFF, "plan-changes-requested", model);
                }
                case TREATMENT_PAID -> toStaff(e.tenantId(), PRODUCTION_STAFF, "production-ready", model);
                case SHIPPED -> {
                    var doctor = identity.get(e.doctorId());
                    var patient = identity.get(e.patientId());
                    model.put("doctorName", doctor.fullName());
                    model.put("patientName", patient.fullName());
                    to(doctor, "order-shipped-doctor", model);
                    to(patient, "order-shipped-patient", model);
                }
                default -> { /* other transitions have their own richer events or need no mail */ }
            }
        });
    }

    @ApplicationModuleListener
    public void onPlanSent(PlanSent e) {
        platform.run(() -> {
            var model = base(e.tenantId(), e.orderNumber(), e.orderId());
            model.put("version", e.version());
            var doctor = identity.get(e.doctorId());
            model.put("doctorName", doctor.fullName());
            to(doctor, "plan-sent", model);
        });
    }

    @ApplicationModuleListener
    public void onPlanApproved(PlanApproved e) {
        platform.run(() -> {
            var model = base(e.tenantId(), e.orderNumber(), e.orderId());
            model.put("version", e.version());
            model.put("doctorName", identity.get(e.doctorId()).fullName());
            model.put("amount", money(e.priceTotal(), e.currency()));
            toStaff(e.tenantId(), LAB_STAFF, "plan-approved", model);
        });
    }

    @ApplicationModuleListener
    public void onPaymentApproved(PaymentApproved e) {
        platform.run(() -> {
            var model = base(e.tenantId(), e.orderNumber(), e.orderId());
            model.put("purpose", e.purpose().name());
            model.put("amount", money(e.amount(), e.currency()));
            model.put("gateway", e.gateway());
            if (e.payerId() != null) {
                var payer = identity.get(e.payerId());
                model.put("doctorName", payer.fullName());
                to(payer, "payment-approved-doctor", model);
            }
            toStaff(e.tenantId(), LAB_STAFF, "payment-approved-lab", model);
        });
    }

    @ApplicationModuleListener
    public void onFollowUpRecorded(FollowUpRecorded e) {
        platform.run(() -> {
            var patient = identity.get(e.patientId());
            var model = base(e.tenantId(), e.orderNumber(), e.orderId());
            model.put("doctorName", identity.get(e.doctorId()).fullName());
            model.put("patientName", patient.fullName());
            model.put("month", e.treatmentMonth());
            model.put("visitDate", e.visitDate().toString());
            to(patient, "follow-up-recorded", model);
        });
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> base(UUID tenantId, Long orderNumber, UUID orderId) {
        var model = new HashMap<String, Object>();
        model.put("labName", identity.tenant(tenantId).name());
        model.put("orderId", orderId);
        model.put("orderNumber", orderNumber == null ? "" : orderNumber);
        return model;
    }

    private void toStaff(UUID tenantId, Set<PersonType> types, String template, Map<String, Object> model) {
        List<PersonDto> staff = identity.staffOf(tenantId, types);
        staff.forEach(p -> to(p, template, model));
    }

    private void to(PersonDto person, String template, Map<String, Object> model) {
        if (person == null || !person.active() || person.email() == null || person.email().isBlank()) {
            log.debug("Skipping mail '{}': recipient without e-mail", template);
            return;
        }
        var copy = new HashMap<>(model);
        copy.put("recipientName", person.firstName());
        mailer.send(person.email(), template, localeOf(person), copy);
    }

    private static Locale localeOf(PersonDto p) {
        return p.locale() == null ? Locale.forLanguageTag("es") : Locale.forLanguageTag(p.locale());
    }

    private static String money(BigDecimal amount, String currency) {
        var nf = NumberFormat.getInstance(Locale.forLanguageTag("es-CO"));
        nf.setMaximumFractionDigits(0);
        return nf.format(amount == null ? BigDecimal.ZERO : amount) + " " + (currency == null ? "" : currency);
    }
}
