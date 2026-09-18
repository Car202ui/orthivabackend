package com.orthiva.core.file;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID orderId);

    List<MediaAsset> findByPlanIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID planId);

    List<MediaAsset> findByFollowUpIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID followUpId);

    Optional<MediaAsset> findByIdAndDeletedAtIsNull(UUID id);
}
