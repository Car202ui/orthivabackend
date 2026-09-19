package com.orthiva.core.payment.infrastructure.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Wompi (Colombia) credentials. Test keys start with {@code pub_test_}/{@code prv_test_}
 * and work against the same checkout URL. All values come from the environment; the
 * gateway stays disabled while any of them is missing.
 */
@ConfigurationProperties(prefix = "orthiva.payments.wompi")
public record WompiProperties(
        String publicKey,
        /** Signs the checkout (signature:integrity). */
        String integritySecret,
        /** Verifies webhooks (X-Event-Checksum). */
        String eventsSecret,
        String checkoutUrl) {

    public boolean configured() {
        return notBlank(publicKey) && notBlank(integritySecret) && notBlank(eventsSecret);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
