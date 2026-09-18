package com.orthiva.core.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Mirrors the PostgreSQL enum {@code person_type} and the Keycloak realm roles. */
public enum PersonType {
    ADMIN, LAB, PLANNER, PRODUCTION, ACCOUNTING, REPRESENTATIVE, DOCTOR, PATIENT;

    /** Priority used when a user carries several roles (staff first, then doctor, then patient). */
    private static final List<PersonType> PRIORITY = List.of(
            ADMIN, LAB, PLANNER, PRODUCTION, ACCOUNTING, REPRESENTATIVE, DOCTOR, PATIENT);

    public static Optional<PersonType> fromRoles(Set<String> roles) {
        return PRIORITY.stream().filter(t -> roles.contains(t.name())).findFirst();
    }

    public boolean isStaff() {
        return this != DOCTOR && this != PATIENT;
    }

    /** Roles a self-registered user may choose during onboarding. */
    public static boolean selfServiceAllowed(PersonType type) {
        return type == DOCTOR || type == PATIENT;
    }
}
