package com.orthiva.core.shared.tenant;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-request identity as the database sees it. Populated by {@link TenantFilter} after
 * the JWT is validated and read by {@link TenantAwareTransactionManager} to configure
 * PostgreSQL row-level security for every transaction.
 */
public final class TenantContext {

    /** Snapshot of who is acting. {@code tenantId} null + {@code platform} true = platform admin. */
    public record Actor(UUID personId, UUID tenantId, Set<String> roles, boolean platform) {
        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }

    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private TenantContext() {
    }

    public static void set(Actor actor) {
        CURRENT.set(actor);
    }

    public static Optional<Actor> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Actor require() {
        return current().orElseThrow(() -> new IllegalStateException("No actor in context"));
    }

    public static Optional<UUID> tenantId() {
        return current().map(Actor::tenantId);
    }

    /** True while inside {@link PlatformScope#run} or when the actor is a platform admin. */
    public static boolean bypassRls() {
        return BYPASS.get() || current().map(Actor::platform).orElse(false);
    }

    static void setBypass(boolean value) {
        BYPASS.set(value);
    }

    public static void clear() {
        CURRENT.remove();
        BYPASS.remove();
    }
}
