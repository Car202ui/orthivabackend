package com.orthiva.core.shared.tenant;

import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps a validated Keycloak token to the domain actor (person + tenant). Implemented by
 * the identity module; declared here so the shared filter does not depend on it.
 */
public interface ActorResolver {

    /** Empty when the user exists in Keycloak but has no domain role/person yet (onboarding). */
    Optional<TenantContext.Actor> resolve(Jwt jwt);
}
