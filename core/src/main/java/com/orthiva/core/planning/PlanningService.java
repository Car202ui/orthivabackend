package com.orthiva.core.planning;

import java.util.List;
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
import com.orthiva.core.identity.Person;
import com.orthiva.core.identity.PersonRepository;
import com.orthiva.core.order.OrderService;
import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.order.TreatmentOrder;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/** The laboratory's side: take an order into planning, build the plan, send it to the doctor. */
@Service
@Transactional
public class PlanningService {

    private static final Set<String> PLANNER_ROLES = Set.of("LAB", "PLANNER");

    private final TreatmentPlanRepository plans;
    private final OrderService orders;
    private final MediaService media;
    private final PersonRepository persons;
    private final ApplicationEventPublisher events;

    public PlanningService(TreatmentPlanRepository plans, OrderService orders, MediaService media,
                           PersonRepository persons, ApplicationEventPublisher events) {
        this.plans = plans;
        this.orders = orders;
        this.media = media;
        this.persons = persons;
        this.events = events;
    }

    /**
     * DIAGNOSIS_PAID / CHANGES_REQUESTED → IN_PLANNING and opens a new plan version
     * (unless an unsent one already exists, which is then returned).
     */
    public PlanDto startPlanning(UUID orderId) {
        var actor = requirePlanner();
        TreatmentOrder order = orders.requireVisible(orderId);
        var existing = plans.findFirstByOrderIdAndSentAtIsNullAndDeletedAtIsNull(orderId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }
        if (order.getStatus() != OrderStatus.IN_PLANNING) {
            orders.transition(orderId, OrderStatus.IN_PLANNING, null);
        }
        int version = plans.findFirstByOrderIdAndDeletedAtIsNullOrderByVersionDesc(orderId)
                .map(p -> p.getVersion() + 1).orElse(1);
        String currency = order.getCurrency() == null ? "COP" : order.getCurrency();
        var plan = plans.save(new TreatmentPlan(actor.tenantId(), orderId, version, actor.personId(), currency));
        return toDto(plan);
    }

    public PlanDto update(UUID planId, PlanInput in) {
        var plan = editable(planId);
        plan.apply(in);
        return toDto(plan);
    }

    public MediaDto addMedia(UUID planId, MediaKind kind, MultipartFile file) {
        var plan = editable(planId);
        var stored = media.store(MediaOwner.plan(plan.getId()), kind, file);
        if (kind == MediaKind.STL) {
            plan.markStlUploaded();
        }
        return stored;
    }

    public void removeMedia(UUID planId, UUID mediaId) {
        var plan = editable(planId);
        media.delete(mediaId, MediaOwner.plan(plan.getId()));
    }

    /** Freezes the version and moves the order to PLAN_SENT. */
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

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<PlanDto> forOrder(UUID orderId) {
        var actor = TenantContext.require();
        orders.requireVisible(orderId);
        boolean lab = actor.roles().stream().anyMatch(PLANNER_ROLES::contains) || actor.hasRole("ADMIN");
        return plans.findByOrderIdAndDeletedAtIsNullOrderByVersionAsc(orderId).stream()
                .filter(p -> lab || p.isSent())        // doctors/patients only see sent versions
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanDto get(UUID planId) {
        var actor = TenantContext.require();
        var plan = plans.findByIdAndDeletedAtIsNull(planId).orElseThrow(() -> DomainException.notFound("Plan"));
        orders.requireVisible(plan.getOrderId());
        boolean lab = actor.roles().stream().anyMatch(PLANNER_ROLES::contains) || actor.hasRole("ADMIN");
        if (!lab && !plan.isSent()) {
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

    private PlanDto toDto(TreatmentPlan p) {
        String plannerName = p.getPlannerId() == null ? null
                : persons.findById(p.getPlannerId()).map(Person::getFullName).orElse(null);
        return PlanDto.of(p, plannerName, media.listFor(MediaOwner.plan(p.getId())));
    }

    private static TenantContext.Actor requirePlanner() {
        var actor = TenantContext.require();
        if (actor.roles().stream().noneMatch(PLANNER_ROLES::contains)) {
            throw DomainException.forbidden("Only laboratory planners can manage treatment plans");
        }
        return actor;
    }
}
