package com.orthiva.core.order;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors the PostgreSQL enum {@code order_status}. Holds the state machine: which
 * transitions exist and which roles may trigger them. "SYSTEM" transitions are driven by
 * events (payments), never by a user request.
 */
public enum OrderStatus {
    DRAFT, SUBMITTED, DIAGNOSIS_PAID, IN_PLANNING, PLAN_SENT, CHANGES_REQUESTED,
    APPROVED, TREATMENT_PAID, IN_PRODUCTION, SHIPPED, IN_FOLLOW_UP, CLOSED,
    REJECTED, CANCELLED;

    public static final String SYSTEM = "SYSTEM";
    private static final Set<String> LAB_ROLES = Set.of("LAB", "PLANNER");
    private static final Set<String> PRODUCTION_ROLES = Set.of("LAB", "PRODUCTION");

    private static final Map<OrderStatus, Map<OrderStatus, Set<String>>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        allow(DRAFT, SUBMITTED, Set.of("DOCTOR"));
        allow(DRAFT, CANCELLED, Set.of("DOCTOR"));
        allow(SUBMITTED, DIAGNOSIS_PAID, Set.of(SYSTEM));
        allow(SUBMITTED, CANCELLED, Set.of("DOCTOR"));
        allow(DIAGNOSIS_PAID, IN_PLANNING, LAB_ROLES);
        allow(IN_PLANNING, PLAN_SENT, LAB_ROLES);
        allow(PLAN_SENT, CHANGES_REQUESTED, Set.of("DOCTOR"));
        allow(PLAN_SENT, APPROVED, Set.of("DOCTOR"));
        allow(PLAN_SENT, REJECTED, Set.of("DOCTOR"));
        allow(CHANGES_REQUESTED, IN_PLANNING, LAB_ROLES);
        allow(CHANGES_REQUESTED, PLAN_SENT, LAB_ROLES);
        allow(APPROVED, TREATMENT_PAID, Set.of(SYSTEM));
        allow(TREATMENT_PAID, IN_PRODUCTION, PRODUCTION_ROLES);
        allow(IN_PRODUCTION, SHIPPED, PRODUCTION_ROLES);
        allow(SHIPPED, IN_FOLLOW_UP, Set.of("DOCTOR"));
        allow(IN_FOLLOW_UP, CLOSED, Set.of("DOCTOR", "LAB"));
    }

    private static void allow(OrderStatus from, OrderStatus to, Set<String> roles) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(OrderStatus.class)).put(to, roles);
    }

    /** Roles allowed to move from this status to {@code to}; empty set when the transition does not exist. */
    public Set<String> rolesAllowedTo(OrderStatus to) {
        return TRANSITIONS.getOrDefault(this, Map.of()).getOrDefault(to, Set.of());
    }

    public boolean canTransitionTo(OrderStatus to, Set<String> actorRoles) {
        Set<String> allowed = rolesAllowedTo(to);
        return actorRoles.stream().anyMatch(allowed::contains);
    }

    public boolean isSystemTransition(OrderStatus to) {
        return rolesAllowedTo(to).contains(SYSTEM);
    }

    public boolean isEditableByDoctor() {
        return this == DRAFT;
    }

    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED || this == CANCELLED;
    }
}
