package com.orthiva.core.shared.tenant;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * Applies the PostgreSQL session settings that drive row-level security right after a
 * transaction begins: {@code app.tenant_id} for tenant-scoped work or
 * {@code app.bypass_rls} for platform work. SET LOCAL dies with the transaction, so a
 * pooled connection never leaks a tenant to the next request.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(getEntityManagerFactory());
        if (holder == null) {
            return;
        }
        Session session = holder.getEntityManager().unwrap(Session.class);
        session.doWork(connection -> {
            if (TenantContext.bypassRls()) {
                setConfig(connection, "app.bypass_rls", "on");
            } else {
                TenantContext.tenantId().ifPresent(id -> {
                    try {
                        setConfig(connection, "app.tenant_id", id.toString());
                    } catch (SQLException e) {
                        throw new IllegalStateException("Cannot set tenant on connection", e);
                    }
                });
            }
        });
    }

    private static void setConfig(java.sql.Connection connection, String key, String value) throws SQLException {
        // set_config(..., is_local = true) == SET LOCAL, but parameterised.
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.execute();
        }
    }
}
