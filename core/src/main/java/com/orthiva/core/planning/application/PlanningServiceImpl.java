package com.orthiva.core.planning.application;

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
import com.orthiva.core.identity.IdentityService;
import com.orthiva.core.order.OrderDto;
import com.orthiva.core.order.OrderService;
import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.planning.ApprovalInput;
import com.orthiva.core.planning.PlanApproved;
import com.orthiva.core.planning.PlanDto;
import com.orthiva.core.planning.PlanInput;
import com.orthiva.core.planning.PlanSent;
import com.orthiva.core.planning.PlanningService;
import com.orthiva.core.planning.domain.PlanApproval;
import com.orthiva.core.planning.domain.PlanComment;
import com.orthiva.core.planning.domain.TreatmentPlan;
import com.orthiva.core.planning.infrastructure.persistence.PlanApprovalRepository;
import com.orthiva.core.planning.infrastructure.persistence.PlanCommentRepository;
import com.orthiva.core.planning.infrastructure.persistence.TreatmentPlanRepository;
import com.orthiva.core.shared.persistence.AddressRepository;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/**
 * The laboratory's side (take an order into planning, build the plan, send it) and the
 * doctor's side (comment, approve, reject) of a treatment plan.
 */
@Service
@Transactional
class PlanningServiceImpl implements PlanningService {

    private static final Set<String> PLANNER_ROLES = Set.of("LAB", "PLANNER");

    private final TreatmentPlanRepository plans;
    private final PlanCommentRepository comments;
    private final PlanApprovalRepository approvals;
    private final AddressRepository addresses;
    private final OrderService orders;
    private final MediaService media;
    private final IdentityService identity;
    private final ApplicationEventPublisher events;

    PlanningServiceImpl(TreatmentPlanRepository plans, PlanCommentRepository comments,
                        PlanApprovalRepository approvals, AddressRepository addresses, OrderService orders,
                        MediaService media, IdentityService identity, ApplicationEventPublisher events) {
        this.plans = plans;
        this.comments = comments;
        this.approvals = approvals;
        this.addresses = addresses;
        this.orders = orders;
        this.media = media;
        this.identity = identity;
        this.events = events;
    }

    // ------------------------------------------------------------------ laboratory

    @Override
    public PlanDto startPlanning(UUID orderId) {
        var actor = requirePlanner();
        OrderDto order = orders.get(orderId);     // 404 when not visible to the actor
        var existing = plans.findFirstByOrderIdAndSentAtIsNullAndDeletedAtIsNull(orderId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }
        if (order.status() != OrderStatus.IN_PLANNING) {
            orders.transition(orderId, OrderStatus.IN_PLANNING, null);
        }
        int version = plans.findFirstByOrderIdAndDeletedAtIsNullOrderByVersionDesc(orderId)
                .map(p -> p.getVersion() + 1).orElse(1);
        String currency = order.currency() == null ? "COP" : order.currency();
        var plan = plans.save(new TreatmentPlan(actor.tenantId(), orderId, version, actor.personId(), currency));
        return toDto(plan);
    }

    @Override
    public PlanDto update(UUID planId, PlanInput in) {
        var plan = editable(planId);
        plan.apply(in);
        return toDto(plan);
    }

    @Override
    public MediaDto addMedia(UUID planId, MediaKind kind, MultipartFile file) {
        var plan = editable(planId);
        var stored = media.store(MediaOwner.plan(plan.getId()), kind, file);
        if (kind == MediaKind.STL) {
            plan.markStlUploaded();
        }
        return stored;
    }

    @Override
    public void removeMedia(UUID planId, UUID mediaId) {
        var plan = editable(planId);
        media.delete(mediaId, MediaOwner.plan(plan.getId()));
    }

    @Override
    public PlanDto send(UUID planId) {
        var plan = editable(planId);
        if (plan.getPriceTotal() == null) {
            throw DomainException.badRequest("price_required", "Set the treatment price before sending the plan");
        }
        plan.markSent();
        orders.transition(plan.getOrderId(), OrderStatus.PLAN_SENT, "Plan v" + plan.getVersion());
        events.publishEvent(new PlanSent(plan.getId(), plan.getOrderId(), plan.getTenantId(), plan.getVersion()));
        return toDto(plan);
    }

    // ------------------------------------------------------------------ doctor

    @Override
    public PlanDto comment(UUID planId, String body) {
        var actor = TenantContext.require();
        var plan = sentPlan(planId);
        OrderDto order = orders.get(plan.getOrderId());
        boolean doctor = actor.hasRole("DOCTOR") && actor.personId().equals(order.doctorId());
        if (!doctor && !isLab(actor)) {
            throw DomainException.forbidden("Only the treating doctor or the laboratory can comment on a plan");
        }
        comments.save(new PlanComment(plan.getTenantId(), plan.getId(), actor.personId(), body.trim()));
        // A doctor's remark on the plan under review is a change request.
        if (doctor && order.status() == OrderStatus.PLAN_SENT && isLatest(plan)) {
            orders.transition(plan.getOrderId(), OrderStatus.CHANGES_REQUESTED, abbreviate(body));
        }
        return toDto(plan);
    }

    @Override
    public PlanDto approve(UUID planId, ApprovalInput in) {
        var actor = TenantContext.require();
        var plan = sentPlan(planId);
        OrderDto order = requireTreatingDoctor(actor, plan);
        if (order.status() != OrderStatus.PLAN_SENT || !isLatest(plan)) {
            throw DomainException.conflict("plan_not_reviewable", "Only the latest sent plan of an order awaiting review can be approved");
        }
        if (approvals.findByPlanId(plan.getId()).isPresent()) {
            throw DomainException.conflict("plan_already_approved", "This plan version was already approved");
        }
        var a = in.address();
        if (isBlank(a.country()) || isBlank(a.city()) || isBlank(a.line1())) {
            throw DomainException.badRequest("address_incomplete", "Country, city and address line are required");
        }
        String agreement = identity.currentTenant().agreementText();
        if (isBlank(agreement)) {
            throw DomainException.conflict("agreement_missing", "The laboratory has not configured its responsibility agreement");
        }
        // Snapshot rows: a fresh address and a copy of the agreement, never shared or edited later.
        var shipAddress = addresses.save(a.toEntity());
        approvals.save(new PlanApproval(plan.getTenantId(), plan.getId(), actor.personId(), in.shipToClinicName().trim(),
                shipAddress, in.shippingInstructions(), agreement));
        orders.transition(plan.getOrderId(), OrderStatus.APPROVED, "Plan v" + plan.getVersion() + " approved");
        events.publishEvent(new PlanApproved(plan.getId(), plan.getOrderId(), plan.getTenantId(), actor.personId(),
                plan.getVersion(), plan.getPriceTotal(), plan.getCurrency()));
        return toDto(plan);
    }

    @Override
    public PlanDto reject(UUID planId, String reason) {
        var actor = TenantContext.require();
        var plan = sentPlan(planId);
        OrderDto order = requireTreatingDoctor(actor, plan);
        if (order.status() != OrderStatus.PLAN_SENT || !isLatest(plan)) {
            throw DomainException.conflict("plan_not_reviewable", "Only the latest sent plan of an order awaiting review can be rejected");
        }
        comments.save(new PlanComment(plan.getTenantId(), plan.getId(), actor.personId(), reason.trim()));
        orders.transition(plan.getOrderId(), OrderStatus.REJECTED, abbreviate(reason));
        return toDto(plan);
    }

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional(readOnly = true)
    public List<PlanDto> forOrder(UUID orderId) {
        var actor = TenantContext.require();
        orders.get(orderId);                       // visibility check
        boolean lab = isLab(actor);
        return plans.findByOrderIdAndDeletedAtIsNullOrderByVersionAsc(orderId).stream()
                .filter(p -> lab || p.isSent())    // doctors/patients only see sent versions
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlanDto get(UUID planId) {
        var actor = TenantContext.require();
        var plan = plans.findByIdAndDeletedAtIsNull(planId).orElseThrow(() -> DomainException.notFound("Plan"));
        orders.get(plan.getOrderId());             // visibility check
        if (!isLab(actor) && !plan.isSent()) {
            throw DomainException.notFound("Plan");
        }
        return toDto(plan);
    }

    // ------------------------------------------------------------------ helpers

    private TreatmentPlan editable(UUID planId) {
        requirePlanner();
        var plan = plans.findByIdAndDeletedAtIsNull(planId).orElseThrow(() -> DomainException.notFound("Plan"));
        if (plan.isSent()) {
            throw DomainException.conflict("plan_sent", "A sent plan cannot be modified; start a new version");
        }
        return plan;
    }

    /** A version the doctor may act on: exists, visible through its order, and already sent. */
    private TreatmentPlan sentPlan(UUID planId) {
        var plan = plans.findByIdAndDeletedAtIsNull(planId).orElseThrow(() -> DomainException.notFound("Plan"));
        if (!plan.isSent()) {
            throw DomainException.notFound("Plan");
        }
        return plan;
    }

    private OrderDto requireTreatingDoctor(TenantContext.Actor actor, TreatmentPlan plan) {
        OrderDto order = orders.get(plan.getOrderId());   // 404 when not visible
        if (!actor.hasRole("DOCTOR") || !actor.personId().equals(order.doctorId())) {
            throw DomainException.forbidden("Only the treating doctor can decide on a plan");
        }
        return order;
    }

    private boolean isLatest(TreatmentPlan plan) {
        return plans.findFirstByOrderIdAndDeletedAtIsNullOrderByVersionDesc(plan.getOrderId())
                .map(latest -> latest.getId().equals(plan.getId())).orElse(false);
    }

    private PlanDto toDto(TreatmentPlan p) {
        var thread = comments.findByPlanIdOrderByCreatedAtAsc(p.getId());
        var approval = approvals.findByPlanId(p.getId()).orElse(null);

        Set<UUID> ids = new HashSet<>();
        if (p.getPlannerId() != null) ids.add(p.getPlannerId());
        thread.forEach(c -> ids.add(c.getAuthorId()));
        if (approval != null) ids.add(approval.getApprovedBy());
        Map<UUID, String> names = ids.isEmpty() ? Map.of() : identity.namesOf(ids);

        return PlanDto.of(p, names.get(p.getPlannerId()), media.listFor(MediaOwner.plan(p.getId())),
                thread.stream().map(c -> PlanDto.CommentDto.from(c, names.get(c.getAuthorId()))).toList(),
                approval == null ? null : PlanDto.ApprovalDto.from(approval, names.get(approval.getApprovedBy())));
    }

    private static String abbreviate(String text) {
        String t = text.trim();
        return t.length() <= 200 ? t : t.substring(0, 197) + "...";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isLab(TenantContext.Actor actor) {
        return actor.roles().stream().anyMatch(PLANNER_ROLES::contains) || actor.hasRole("ADMIN");
    }

    private static TenantContext.Actor requirePlanner() {
        var actor = TenantContext.require();
        if (actor.roles().stream().noneMatch(PLANNER_ROLES::contains)) {
            throw DomainException.forbidden("Only laboratory planners can manage treatment plans");
        }
        return actor;
    }
}
