package com.orthiva.core.identity;

import java.math.BigDecimal;
import java.util.UUID;

/** Laboratory (tenant) settings other modules may read. */
public record TenantDto(UUID id, String name, String slug, String currency, BigDecimal diagnosisPrice,
                        String agreementText, boolean active) {
}
