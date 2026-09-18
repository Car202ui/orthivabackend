package com.orthiva.core.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orthiva.core.shared.tenant.TenantContext;

import jakarta.validation.Valid;

/**
 * Who am I, as Keycloak and the domain see it. The frontend calls GET /api/me after
 * login and routes to onboarding when {@code onboardingRequired} is true.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final IdentityService identity;

    public MeController(IdentityService identity) {
        this.identity = identity;
    }

    public record MeResponse(String subject, String username, String email, String name, List<String> roles,
                             boolean onboardingRequired, PersonDto person, TenantSummary tenant) {
    }

    public record TenantSummary(UUID id, String name, String currency) {
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = IdentityService.realmRoles(jwt).stream().sorted().toList();
        boolean onboarded = TenantContext.current().isPresent();
        PersonDto person = onboarded ? PersonDto.from(identity.currentPerson()) : null;
        TenantSummary tenant = null;
        if (onboarded) {
            var t = identity.currentTenant();
            tenant = new TenantSummary(t.getId(), t.getName(), t.getCurrency());
        }
        return new MeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                roles,
                !onboarded,
                person,
                tenant);
    }

    /** Self-registered user picks DOCTOR/PATIENT and fills the profile in one step. */
    @PostMapping("/onboarding")
    public PersonDto onboard(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OnboardingRequest body) {
        var person = identity.onboard(jwt, new IdentityService.OnboardingInput(body.type(), body.profile()));
        return PersonDto.from(person);
    }

    public record OnboardingRequest(PersonType type, @Valid ProfileInput profile) {
    }

    @GetMapping("/profile")
    public PersonDto profile() {
        return PersonDto.from(identity.currentPerson());
    }

    @PutMapping("/profile")
    public PersonDto updateProfile(@Valid @RequestBody ProfileInput body) {
        return PersonDto.from(identity.updateProfile(body));
    }
}
