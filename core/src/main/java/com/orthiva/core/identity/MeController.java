package com.orthiva.core.identity;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the identity Keycloak asserted for the caller. Used by the frontend after
 * login and as the smoke test that the OIDC integration works end to end.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    /** Claims may be absent depending on the grant/scopes, so fields are nullable. */
    public record MeResponse(String subject, String username, String email, String name, List<String> roles) {
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();

        return new MeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                roles);
    }
}
