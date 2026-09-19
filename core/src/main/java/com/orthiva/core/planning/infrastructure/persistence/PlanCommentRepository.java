package com.orthiva.core.planning.infrastructure.persistence;

import com.orthiva.core.planning.domain.PlanComment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCommentRepository extends JpaRepository<PlanComment, UUID> {

    List<PlanComment> findByPlanIdOrderByCreatedAtAsc(UUID planId);
}
