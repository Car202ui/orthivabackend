package com.orthiva.core.identity;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.orthiva.core.shared.web.DomainException;

/**
 * Thin wrapper over the Keycloak Admin REST API using the {@code orthiva-core} service
 * account (client credentials). Only the operations the core needs: create a user,
 * assign realm roles, look up by email.
 */
@Component
public class KeycloakAdminClient {

    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public KeycloakAdminClient(
            @Value("${orthiva.keycloak.base-url}") String baseUrl,
            @Value("${orthiva.keycloak.realm}") String realm,
            @Value("${orthiva.keycloak.client-id}") String clientId,
            @Value("${orthiva.keycloak.client-secret}") String clientSecret) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public record CreatedUser(UUID id, String email) {
    }

    /** Creates an enabled user with a (temporary or permanent) password and the given realm roles. */
    public CreatedUser createUser(String email, String firstName, String lastName, String password,
                                  boolean temporaryPassword, List<String> roles) {
        var body = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", temporaryPassword)));
        URI location;
        try {
            location = http.post().uri("/admin/realms/{realm}/users", realm)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders().getLocation();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw DomainException.conflict("user_exists", "A user with that email already exists");
            }
            throw e;
        }
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return the new user's location");
        }
        String path = location.getPath();
        UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
        assignRealmRoles(id, roles);
        return new CreatedUser(id, email);
    }

    /** Exact-match lookup by email; empty when the user does not exist. */
    @SuppressWarnings("unchecked")
    public java.util.Optional<UUID> findUserIdByEmail(String email) {
        List<Map<String, Object>> users = http.get()
                .uri(b -> b.path("/admin/realms/{realm}/users").queryParam("email", email).queryParam("exact", true).build(realm))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(List.class);
        return users == null || users.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(UUID.fromString((String) users.get(0).get("id")));
    }

    public void assignRealmRoles(UUID userId, List<String> roles) {
        var representations = roles.stream().map(this::realmRole).toList();
        http.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(representations)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> realmRole(String name) {
        return http.get().uri("/admin/realms/{realm}/roles/{name}", realm, name)
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<String, Object> res = http.post().uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        cachedToken = (String) res.get("access_token");
        int expiresIn = ((Number) res.get("expires_in")).intValue();
        tokenExpiry = Instant.now().plusSeconds(Math.max(expiresIn - 15, 5));
        return cachedToken;
    }
}
