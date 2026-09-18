package com.orthiva.core.shared.tenant;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads Keycloak realm roles from an access token. */
public final class JwtRoles {

    private JwtRoles() {
    }

    @SuppressWarnings("unchecked")
    public static Set<String> realmRoles(Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return Set.of();
        }
        return ((Collection<String>) roles).stream().collect(Collectors.toUnmodifiableSet());
    }
}
