package com.orthiva.core.shared.tenant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

@Configuration
public class TenantConfig {

    /** Replaces Boot's default JpaTransactionManager so every transaction is tenant-aware. */
    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new TenantAwareTransactionManager(emf);
    }
}
