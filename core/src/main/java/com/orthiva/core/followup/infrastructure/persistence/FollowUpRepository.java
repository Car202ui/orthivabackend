package com.orthiva.core.followup.infrastructure.persistence;

import com.orthiva.core.followup.domain.FollowUp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    List<FollowUp> findByOrderIdAndDeletedAtIsNullOrderByTreatmentMonthAscVisitDateAsc(UUID orderId);

    Optional<FollowUp> findByIdAndDeletedAtIsNull(UUID id);
}
