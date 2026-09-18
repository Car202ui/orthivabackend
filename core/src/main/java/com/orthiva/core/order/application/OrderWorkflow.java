package com.orthiva.core.order.application;

import com.orthiva.core.order.domain.OrderStatusHistory;
import com.orthiva.core.order.domain.TreatmentOrder;
import com.orthiva.core.order.infrastructure.persistence.OrderRepository;
import com.orthiva.core.order.infrastructure.persistence.OrderStatusHistoryRepository;
import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.order.OrderStatusChanged;

import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/**
 * The only place that changes {@link TreatmentOrder#getStatus()}. Validates the
 * transition against {@link OrderStatus}, records history and publishes the event.
 */
@Service
@Transactional
class OrderWorkflow {

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ApplicationEventPublisher events;

    OrderWorkflow(OrderRepository orders, OrderStatusHistoryRepository history,
                                ApplicationEventPublisher events) {
        this.orders = orders;
        this.history = history;
        this.events = events;
    }

    /** Transition requested by the current user; their roles must allow it. */
    public TreatmentOrder transition(TreatmentOrder order, OrderStatus to, String note) {
        var actor = TenantContext.require();
        OrderStatus from = order.getStatus();
        if (!from.canTransitionTo(to, actor.roles())) {
            throw DomainException.conflict("invalid_transition",
                    "Cannot move order from " + from + " to " + to + " with roles " + actor.roles());
        }
        return apply(order, from, to, actor.personId(), note);
    }

    /** Transition driven by the system (payment webhooks, schedulers), not by a user. */
    public TreatmentOrder systemTransition(TreatmentOrder order, OrderStatus to, String note) {
        OrderStatus from = order.getStatus();
        if (!from.isSystemTransition(to)) {
            throw DomainException.conflict("invalid_transition", "No system transition from " + from + " to " + to);
        }
        return apply(order, from, to, null, note);
    }

    private TreatmentOrder apply(TreatmentOrder order, OrderStatus from, OrderStatus to, UUID actorId, String note) {
        order.setStatus(to);
        history.save(new OrderStatusHistory(order, from, to, actorId, note));
        orders.save(order);
        events.publishEvent(new OrderStatusChanged(order.getId(), order.getTenantId(), order.getDoctorId(),
                order.getPatientId(), order.getOrderNumber(), from, to, actorId,
                order.getDiagnosisPrice(), order.getCurrency()));
        return order;
    }

    static boolean hasAny(Set<String> roles, String... wanted) {
        for (String w : wanted) {
            if (roles.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
