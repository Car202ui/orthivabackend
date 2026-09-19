package com.orthiva.core.planning;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaKind;

/**
 * Public API of the planning module: the laboratory builds and sends plan versions; the
 * doctor comments, approves or rejects them.
 */
public interface PlanningService {

    // ---- laboratory ------------------------------------------------------------------

    /** DIAGNOSIS_PAID / CHANGES_REQUESTED → IN_PLANNING and opens (or returns) the unsent version. */
    PlanDto startPlanning(UUID orderId);

    PlanDto update(UUID planId, PlanInput in);

    MediaDto addMedia(UUID planId, MediaKind kind, MultipartFile file);

    void removeMedia(UUID planId, UUID mediaId);

    /** Freezes the version and moves the order to PLAN_SENT. */
    PlanDto send(UUID planId);

    // ---- doctor ------------------------------------------------------------------------

    /**
     * Adds a message to a sent version. The doctor's first comment while the order is in
     * PLAN_SENT moves it to CHANGES_REQUESTED; later messages (doctor or lab) only extend the thread.
     */
    PlanDto comment(UUID planId, String body);

    /** PLAN_SENT → APPROVED: snapshots shipping address + agreement, publishes {@link PlanApproved}. */
    PlanDto approve(UUID planId, ApprovalInput in);

    /** PLAN_SENT → REJECTED (terminal) with a mandatory reason, stored as a comment. */
    PlanDto reject(UUID planId, String reason);

    // ---- reads -------------------------------------------------------------------------

    /** Versions of an order's plan: lab sees all, doctor/patient only sent ones. */
    List<PlanDto> forOrder(UUID orderId);

    PlanDto get(UUID planId);
}
