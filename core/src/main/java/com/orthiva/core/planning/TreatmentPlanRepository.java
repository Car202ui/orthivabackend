package com.orthiva.core.planning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, UUID> {

    List<TreatmentPlan> findByOrderIdAndDeletedAtIsNullOrderByVersionAsc(UUID orderId);

    Optional<TreatmentPlan> findByIdAndDeletedAtIsNull(UUID id);

    Optional<TreatmentPlan> findFirstByOrderIdAndSentAtIsNullAndDeletedAtIsNull(UUID orderId);

    Optional<TreatmentPlan> findFirstByOrderIdAndDeletedAtIsNullOrderByVersionDesc(UUID orderId);
}
