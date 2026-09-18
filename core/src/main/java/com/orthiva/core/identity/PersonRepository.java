package com.orthiva.core.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByKeycloakUserId(UUID keycloakUserId);

    Optional<Person> findFirstByEmailIgnoreCaseAndTypeAndKeycloakUserIdIsNull(String email, PersonType type);

    List<Person> findByTenantIdAndTypeInAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(UUID tenantId, List<PersonType> types);
}
