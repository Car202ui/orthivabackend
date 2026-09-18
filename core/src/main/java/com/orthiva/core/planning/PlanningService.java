package com.orthiva.core.planning;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaKind;

/** Public API of the planning module: the laboratory builds and sends plan versions. */
public interface PlanningService {

    /** DIAGNOSIS_PAID / CHANGES_REQUESTED → IN_PLANNING and opens (or returns) the unsent version. */
    PlanDto startPlanning(UUID orderId);

    PlanDto update(UUID planId, PlanInput in);

    MediaDto addMedia(UUID planId, MediaKind kind, MultipartFile file);

    void removeMedia(UUID planId, UUID mediaId);

    /** Freezes the version and moves the order to PLAN_SENT. */
    PlanDto send(UUID planId);

    /** Versions of an order's plan: lab sees all, doctor/patient only sent ones. */
    List<PlanDto> forOrder(UUID orderId);

    PlanDto get(UUID planId);
}
