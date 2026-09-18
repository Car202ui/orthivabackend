package com.orthiva.core.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orthiva.core.identity.PersonType;
import com.orthiva.core.identity.domain.Person;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByKeycloakUserId(UUID keycloakUserId);

    Optional<Person> findFirstByEmailIgnoreCaseAndTypeAndKeycloakUserIdIsNull(String email, PersonType type);

    Optional<Person> findFirstByEmailIgnoreCaseAndTypeAndDeletedAtIsNull(String email, PersonType type);

    List<Person> findByTenantIdAndTypeInAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(UUID tenantId, List<PersonType> types);

    /** Persons among {@code ids}, optionally filtered by name / document / email ({@code q} is a lower-cased LIKE pattern). */
    @Query("""
            select p from Person p
            where p.id in :ids and p.deletedAt is null
              and (:q is null or lower(p.firstName) like :q or lower(p.lastName) like :q
                   or lower(p.documentId) like :q or lower(p.email) like :q)
            order by p.lastName, p.firstName
            """)
    List<Person> searchByIds(@Param("ids") Set<UUID> ids, @Param("q") String q);
}