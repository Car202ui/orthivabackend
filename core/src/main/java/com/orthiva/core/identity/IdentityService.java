package com.orthiva.core.identity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.orthiva.core.shared.tenant.ActorResolver;

/**
 * Public API of the identity module: who is acting, their profile, their tenant, and
 * the people directory other modules need (names, patients). Entities and repositories
 * stay internal; everything crosses the boundary as DTOs.
 */
public interface IdentityService extends ActorResolver {

    // ---- current user ---------------------------------------------------------------

    PersonDto currentProfile();

    TenantDto currentTenant();

    PersonDto onboard(Jwt jwt, OnboardingInput in);

    PersonDto updateProfile(ProfileInput in);

    // ---- staff (ADMIN) --------------------------------------------------------------

    PersonDto createStaff(NewStaffInput in);

    List<PersonDto> listStaff();

    // ---- context-free lookups (event listeners run without a request actor) ------------

    /** Staff of a tenant with any of the given types (e.g. LAB + PLANNER to notify the laboratory). */
    List<PersonDto> staffOf(UUID tenantId, Set<PersonType> types);

    TenantDto tenant(UUID tenantId);

    // ---- people directory (used by patient, order, planning) ------------------------

    PersonDto get(UUID personId);

    /** Full names by id; missing ids are omitted. */
    Map<UUID, String> namesOf(Set<UUID> personIds);

    /** Active person of that type with that email in the current tenant (RLS scoped). */
    Optional<PersonDto> findByEmailAndType(String email, PersonType type);

    /** Persons among {@code ids} matching an optional free-text filter (name, document, email), ordered by name. */
    List<PersonDto> searchByIds(Set<UUID> ids, String query);

    PersonDto createPatient(UUID tenantId, PatientBasics basics);

    PersonDto updatePatientBasics(UUID personId, PatientBasics basics);

    // ---- inputs ---------------------------------------------------------------------

    record OnboardingInput(PersonType type, ProfileInput profile) {
    }

    record NewStaffInput(String email, String firstName, String lastName, PersonType type, String temporaryPassword) {
    }

    record PatientBasics(String firstName, String lastName, String email, String documentId, LocalDate birthDate,
                         Gender gender, String phoneCountry, String phoneNumber) {
    }
}
