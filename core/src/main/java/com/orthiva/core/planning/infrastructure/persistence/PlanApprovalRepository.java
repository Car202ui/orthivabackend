package com.orthiva.core.planning.infrastructure.persistence;

import com.orthiva.core.planning.domain.PlanApproval;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanApprovalRepository extends JpaRepository<PlanApproval, UUID> {

    Optional<PlanApproval> findByPlanId(UUID planId);
}
