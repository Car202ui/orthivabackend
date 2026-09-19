package com.orthiva.core.followup.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaKind;
import com.orthiva.core.file.MediaOwner;
import com.orthiva.core.file.MediaService;
import com.orthiva.core.followup.FollowUpDto;
import com.orthiva.core.followup.FollowUpInput;
import com.orthiva.core.followup.FollowUpRecorded;
import com.orthiva.core.followup.FollowUpService;
import com.orthiva.core.followup.domain.FollowUp;
import com.orthiva.core.followup.infrastructure.persistence.FollowUpRepository;
import com.orthiva.core.identity.IdentityService;
import com.orthiva.core.order.OrderDto;
import com.orthiva.core.order.OrderService;
import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

@Service
@Transactional
class FollowUpServiceImpl implements FollowUpService {

    private static final Set<OrderStatus> OPEN_FOR_FOLLOW_UP = Set.of(OrderStatus.SHIPPED, OrderStatus.IN_FOLLOW_UP);
    private static final Set<MediaKind> PHOTO_KINDS = Set.of(MediaKind.PHOTO_FRONTAL, MediaKind.PHOTO_PROFILE,
            MediaKind.PHOTO_SMILE, MediaKind.PHOTO_INTRAORAL_UPPER, MediaKind.PHOTO_INTRAORAL_LOWER,
            MediaKind.PHOTO_INTRAORAL_RIGHT, MediaKind.PHOTO_INTRAORAL_LEFT, MediaKind.PHOTO_INTRAORAL_FRONTAL,
            MediaKind.XRAY_PANORAMIC, MediaKind.XRAY_LATERAL);

    private final FollowUpRepository followUps;
    private final OrderService orders;
    private final MediaService media;
    private final IdentityService identity;
    private final ApplicationEventPublisher events;

    FollowUpServiceImpl(FollowUpRepository followUps, OrderService orders, MediaService media, IdentityService identity,
                        ApplicationEventPublisher events) {
        this.followUps = followUps;
        this.orders = orders;
        this.media = media;
        this.identity = identity;
        this.events = events;
    }

    @Override
    public FollowUpDto create(UUID orderId, FollowUpInput in) {
        var actor = TenantContext.require();
        OrderDto order = requireTreatingDoctor(actor, orderId);
        if (!OPEN_FOR_FOLLOW_UP.contains(order.status())) {
            throw DomainException.conflict("order_not_in_treatment",
                    "Check-ups can only be recorded once the aligners were shipped (order is " + order.status() + ")");
        }
        var saved = followUps.save(new FollowUp(actor.tenantId(), orderId, actor.personId(), in));
        if (order.status() == OrderStatus.SHIPPED) {
            orders.transition(orderId, OrderStatus.IN_FOLLOW_UP, "Month " + in.treatmentMonth() + " check-up");
        }
        events.publishEvent(new FollowUpRecorded(saved.getId(), orderId, order.orderNumber(), saved.getTenantId(), order.doctorId(),
                order.patientId(), in.treatmentMonth(), in.visitDate()));
        return toDto(saved);
    }

    @Override
    public FollowUpDto update(UUID followUpId, FollowUpInput in) {
        var f = editable(followUpId);
        f.apply(in);
        return toDto(f);
    }

    @Override
    public void delete(UUID followUpId) {
        editable(followUpId).softDelete();
    }

    @Override
    public MediaDto addMedia(UUID followUpId, MediaKind kind, MultipartFile file) {
        if (!PHOTO_KINDS.contains(kind)) {
            throw DomainException.badRequest("invalid_kind", "Only photos and X-rays can be attached to a check-up");
        }
        var f = editable(followUpId);
        return media.store(MediaOwner.followUp(f.getId()), kind, file);
    }

    @Override
    public void removeMedia(UUID followUpId, UUID mediaId) {
        var f = editable(followUpId);
        media.delete(mediaId, MediaOwner.followUp(f.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUpDto> forOrder(UUID orderId) {
        orders.get(orderId);                      // visibility check (doctor, patient, lab)
        var list = followUps.findByOrderIdAndDeletedAtIsNullOrderByTreatmentMonthAscVisitDateAsc(orderId);
        Set<UUID> ids = new HashSet<>();
        list.forEach(f -> { if (f.getRecordedBy() != null) ids.add(f.getRecordedBy()); });
        Map<UUID, String> names = ids.isEmpty() ? Map.of() : identity.namesOf(ids);
        return list.stream()
                .map(f -> FollowUpDto.of(f, names.get(f.getRecordedBy()), media.listFor(MediaOwner.followUp(f.getId()))))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    /** A check-up the current doctor may edit: exists, belongs to one of their orders, treatment still open. */
    private FollowUp editable(UUID followUpId) {
        var actor = TenantContext.require();
        var f = followUps.findByIdAndDeletedAtIsNull(followUpId).orElseThrow(() -> DomainException.notFound("Follow-up"));
        OrderDto order = requireTreatingDoctor(actor, f.getOrderId());
        if (!OPEN_FOR_FOLLOW_UP.contains(order.status())) {
            throw DomainException.conflict("order_closed", "The treatment is closed; check-ups are read-only");
        }
        return f;
    }

    private OrderDto requireTreatingDoctor(TenantContext.Actor actor, UUID orderId) {
        OrderDto order = orders.get(orderId);     // 404 when not visible
        if (!actor.hasRole("DOCTOR") || !actor.personId().equals(order.doctorId())) {
            throw DomainException.forbidden("Only the treating doctor records check-ups");
        }
        return order;
    }

    private FollowUpDto toDto(FollowUp f) {
        String name = f.getRecordedBy() == null ? null : identity.namesOf(Set.of(f.getRecordedBy())).get(f.getRecordedBy());
        return FollowUpDto.of(f, name, media.listFor(MediaOwner.followUp(f.getId())));
    }
}
