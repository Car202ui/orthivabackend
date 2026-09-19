package com.orthiva.core.order;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaKind;

/** Public API of the order module: the prescription lifecycle and reads for every role. */
public interface OrderService {

    // ---- doctor ---------------------------------------------------------------------

    OrderDto createDraft(OrderInput in);

    OrderDto updateDraft(UUID id, OrderInput in);

    MediaDto addMedia(UUID id, MediaKind kind, MultipartFile file);

    void removeMedia(UUID id, UUID mediaId);

    /** DRAFT → SUBMITTED; snapshots the diagnosis price. */
    OrderDto submit(UUID id);

    OrderDto cancel(UUID id, String note);

    // ---- laboratory: production and shipping -----------------------------------------

    /** TREATMENT_PAID → IN_PRODUCTION. */
    OrderDto startProduction(UUID id);

    /** IN_PRODUCTION → SHIPPED; records carrier/tracking (both optional). */
    OrderDto ship(UUID id, ShipmentInput in);

    /** IN_FOLLOW_UP → CLOSED by the doctor or the lab. */
    OrderDto close(UUID id, String note);

    // ---- reads (doctor: own, patient: own, lab roles: all in tenant) ----------------

    List<OrderDto> list(OrderStatus status, UUID patientId);

    /** Full order (movements, media, history); 404 when the actor may not see it. */
    OrderDto get(UUID id);

    // ---- transitions used by other modules ------------------------------------------

    /** User-driven transition; the state machine checks the actor's roles. */
    OrderDto transition(UUID id, OrderStatus to, String note);

    /** System-driven transition (payments, schedulers); no user role involved. */
    void systemTransition(UUID id, OrderStatus to, String note);
}
