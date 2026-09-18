package com.orthiva.core.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orthiva.core.shared.persistence.AddressRepository;
import com.orthiva.core.shared.tenant.ActorResolver;
import com.orthiva.core.shared.tenant.PlatformScope;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/**
 * Bridges Keycloak identities and domain persons. First login provisions the person in
 * the default tenant; self-registered users pick DOCTOR/PATIENT during onboarding and
 * the matching realm role is granted through the admin API.
 */
@Service
public class IdentityService implements ActorResolver {

    private final PersonRepository persons;
    private final TenantRepository tenants;
    private final AddressRepository addresses;
    private final KeycloakAdminClient keycloak;
    private final PlatformScope platform;
    private final String defaultTenantSlug;

    public IdentityService(PersonRepository persons, TenantRepository tenants, AddressRepository addresses,
                           KeycloakAdminClient keycloak, PlatformScope platform,
                           @Value("${orthiva.default-tenant-slug}") String defaultTenantSlug) {
        this.persons = persons;
        this.tenants = tenants;
        this.addresses = addresses;
        this.keycloak = keycloak;
        this.platform = platform;
        this.defaultTenantSlug = defaultTenantSlug;
    }

    // ------------------------------------------------------------------ ActorResolver

    @Override
    public Optional<TenantContext.Actor> resolve(Jwt jwt) {
        UUID kcId = UUID.fromString(jwt.getSubject());
        Set<String> roles = realmRoles(jwt);
        Optional<PersonType> type = PersonType.fromRoles(roles);
        if (type.isEmpty()) {
            return Optional.empty();   // registered in Keycloak, not onboarded yet
        }
        Person person = platform.run(() -> persons.findByKeycloakUserId(kcId)
                .orElseGet(() -> provision(kcId, type.get(), jwt)));
        boolean isPlatform = person.getType() == PersonType.ADMIN;
        return Optional.of(new TenantContext.Actor(person.getId(), person.getTenantId(), roles, isPlatform));
    }

    /** Called inside PlatformScope (RLS bypassed). */
    private Person provision(UUID kcId, PersonType type, Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        // A patient created earlier by their doctor is linked by email instead of duplicated.
        if (type == PersonType.PATIENT && email != null) {
            var existing = persons.findFirstByEmailIgnoreCaseAndTypeAndKeycloakUserIdIsNull(email, PersonType.PATIENT);
            if (existing.isPresent()) {
                existing.get().linkKeycloak(kcId);
                return existing.get();
            }
        }
        UUID tenantId = type == PersonType.ADMIN ? null : defaultTenant().getId();
        return persons.save(Person.provision(tenantId, kcId, type,
                jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"), email));
    }

    // ------------------------------------------------------------------ onboarding / profile

    public record OnboardingInput(PersonType type, ProfileInput profile) {
    }

    /**
     * Self-registered user chooses DOCTOR or PATIENT. Creates the person, grants the realm
     * role in Keycloak; the frontend then refreshes its token to pick up the role.
     */
    public PersonDto onboard(Jwt jwt, OnboardingInput in) {
        if (!PersonType.selfServiceAllowed(in.type())) {
            throw DomainException.badRequest("invalid_type", "Only DOCTOR or PATIENT can self-register");
        }
        UUID kcId = UUID.fromString(jwt.getSubject());
        return platform.run(() -> {
            if (persons.findByKeycloakUserId(kcId).isPresent()) {
                throw DomainException.conflict("already_onboarded", "Profile already exists");
            }
            validateDoctorFields(in.type(), in.profile());
            Person person = provision(kcId, in.type(), jwt);
            applyProfile(person, in.profile());
            keycloak.assignRealmRoles(kcId, List.of(in.type().name()));
            return PersonDto.from(person);
        });
    }

    /** Entity for use inside a transaction; controllers use {@link #currentProfile()} (DTOs are mapped before the session closes). */
    @Transactional(readOnly = true)
    public Person currentPerson() {
        UUID id = TenantContext.require().personId();
        return persons.findById(id).orElseThrow(() -> DomainException.notFound("Person"));
    }

    @Transactional(readOnly = true)
    public PersonDto currentProfile() {
        return PersonDto.from(currentPerson());
    }

    @Transactional
    public PersonDto updateProfile(ProfileInput in) {
        Person person = currentPerson();
        validateDoctorFields(person.getType(), in);
        applyProfile(person, in);
        return PersonDto.from(person);
    }

    private void applyProfile(Person person, ProfileInput in) {
        person.completeProfile(in);
        if (in.address() != null) {
            if (person.getAddress() == null) {
                person.setAddress(addresses.save(in.address().toEntity()));
            } else {
                in.address().applyTo(person.getAddress());
            }
        }
    }

    private static void validateDoctorFields(PersonType type, ProfileInput in) {
        if (type == PersonType.DOCTOR) {
            if (in.licenseNumber() == null || in.licenseNumber().isBlank()) {
                throw DomainException.badRequest("license_required", "Doctors must provide a license number");
            }
            if (in.licenseCountry() == null || in.licenseCountry().isBlank()) {
                throw DomainException.badRequest("license_country_required", "Doctors must provide the license country");
            }
        }
    }

    // ------------------------------------------------------------------ admin

    public record NewStaffInput(String email, String firstName, String lastName, PersonType type,
                                String temporaryPassword) {
    }

    /** ADMIN creates internal users (LAB, PLANNER, PRODUCTION, ACCOUNTING, REPRESENTATIVE). */
    public PersonDto createStaff(NewStaffInput in) {
        if (!in.type().isStaff() || in.type() == PersonType.ADMIN) {
            throw DomainException.badRequest("invalid_type", "Only internal staff roles can be created here");
        }
        return platform.run(() -> {
            // Idempotent: if a previous attempt created the Keycloak user but failed afterwards,
            // reuse it instead of failing with 409 forever.
            UUID kcId;
            var existing = keycloak.findUserIdByEmail(in.email());
            if (existing.isPresent()) {
                kcId = existing.get();
                if (persons.findByKeycloakUserId(kcId).isPresent()) {
                    throw DomainException.conflict("user_exists", "A user with that email already exists");
                }
                keycloak.assignRealmRoles(kcId, List.of(in.type().name()));
            } else {
                kcId = keycloak.createUser(in.email(), in.firstName(), in.lastName(),
                        in.temporaryPassword(), true, List.of(in.type().name())).id();
            }
            var person = Person.provision(defaultTenant().getId(), kcId, in.type(),
                    in.firstName(), in.lastName(), in.email());
            person.completeProfile(new ProfileInput(in.firstName(), in.lastName(), null, null, "es",
                    null, null, null, null, null, null, null));
            return PersonDto.from(persons.save(person));
        });
    }

    @Transactional(readOnly = true)
    public List<PersonDto> listStaff() {
        UUID tenantId = TenantContext.require().tenantId();
        if (tenantId == null) {
            tenantId = defaultTenant().getId();
        }
        return persons.findByTenantIdAndTypeInAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(tenantId,
                        List.of(PersonType.LAB, PersonType.PLANNER, PersonType.PRODUCTION, PersonType.ACCOUNTING, PersonType.REPRESENTATIVE))
                .stream().map(PersonDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Tenant currentTenant() {
        UUID tenantId = TenantContext.require().tenantId();
        return tenantId == null ? defaultTenant() : tenants.findById(tenantId).orElseThrow(() -> DomainException.notFound("Tenant"));
    }

    private Tenant defaultTenant() {
        return tenants.findBySlug(defaultTenantSlug)
                .orElseThrow(() -> new IllegalStateException("Default tenant '" + defaultTenantSlug + "' missing"));
    }

    @SuppressWarnings("unchecked")
    static Set<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return Set.of();
        }
        return ((Collection<String>) roles).stream().collect(Collectors.toUnmodifiableSet());
    }
}
