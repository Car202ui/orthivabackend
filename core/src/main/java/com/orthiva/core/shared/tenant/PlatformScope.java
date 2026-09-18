package com.orthiva.core.shared.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work with row-level security bypassed (SET LOCAL app.bypass_rls = 'on'),
 * in its own transaction. Reserved for platform operations that legitimately span
 * tenants: provisioning a user on first login, webhooks, schedulers.
 */
@Component
public class PlatformScope {

    private final TransactionTemplate tx;

    public PlatformScope(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T run(Supplier<T> work) {
        boolean previous = TenantContext.bypassRls();
        TenantContext.setBypass(true);
        try {
            return tx.execute(status -> work.get());
        } finally {
            TenantContext.setBypass(previous);
        }
    }

    public void run(Runnable work) {
        run(() -> {
            work.run();
            return null;
        });
    }
}
