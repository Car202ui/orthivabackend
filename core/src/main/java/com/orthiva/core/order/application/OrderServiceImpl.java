package com.orthiva.core.order.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaKind;
import com.orthiva.core.file.MediaOwner;
import com.orthiva.core.file.MediaService;
import com.orthiva.core.identity.IdentityService;
import com.orthiva.core.order.OrderDto;
import com.orthiva.core.order.OrderInput;
import com.orthiva.core.order.OrderService;
import com.orthiva.core.order.OrderStatus;
import com.orthiva.core.order.ShipmentDto;
import com.orthiva.core.order.ShipmentInput;
import com.orthiva.core.order.domain.OrderStatusHistory;
import com.orthiva.core.order.domain.Shipment;
import com.orthiva.core.order.domain.TreatmentOrder;
import com.orthiva.core.order.infrastructure.persistence.OrderRepository;
import com.orthiva.core.order.infrastructure.persistence.OrderStatusHistoryRepository;
import com.orthiva.core.order.infrastructure.persistence.ShipmentRepository;
import com.orthiva.core.patient.PatientService;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/** Prescriptions: the doctor's side of the order lifecycle (draft → submit) plus reads for lab and patient. */
@Service
@Transactional
class OrderServiceImpl implements OrderService {

    private static final Set<String> LAB_ROLES = Set.of("LAB", "PLANNER", "PRODUCTION", "ADMIN");

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ShipmentRepository shipments;
    private final OrderWorkflow workflow;
    private final PatientService patients;
    private final MediaService media;
    private final IdentityService identity;

    OrderServiceImpl(OrderRepository orders, OrderStatusHistoryRepository history, ShipmentRepository shipments,
                     OrderWorkflow workflow, PatientService patients, MediaService media, IdentityService identity) {
        this.orders = orders;
        this.history = history;
        this.shipments = shipments;
        this.workflow = workflow;
        this.patients = patients;
        this.media = media;
        this.identity = identity;
    }

    // ------------------------------------------------------------------ doctor

    @Override
    public OrderDto createDraft(OrderInput in) {
        var actor = requireDoctor();
        patients.assertMyPatient(in.patientId());
        if (in.clinicId() != null) {
            patients.assertMyClinic(in.clinicId());
        }
        var order = orders.save(new TreatmentOrder(actor.tenantId(), actor.personId(), in.patientId(), in));
        history.save(new OrderStatusHistory(order, null, OrderStatus.DRAFT, actor.personId(), null));
        return toDto(order);
    }

    @Override
    public OrderDto updateDraft(UUID id, OrderInput in) {
        var order = loadForDoctor(id);
        if (!order.getStatus().isEditableByDoctor()) {
            throw DomainException.conflict("not_editable", "Only draft orders can be edited");
        }
        if (!order.getPatientId().equals(in.patientId())) {
            throw DomainException.badRequest("patient_immutable", "The patient of an order cannot change");
        }
        if (in.clinicId() != null) {
            patients.assertMyClinic(in.clinicId());
        }
        order.apply(in);
        return toDto(order);
    }

    @Override
    public MediaDto addMedia(UUID id, MediaKind kind, MultipartFile file) {
        var order = loadForDoctor(id);
        if (!order.getStatus().isEditableByDoctor()) {
            throw DomainException.conflict("not_editable", "Files can only be added to draft orders");
        }
        return media.store(MediaOwner.order(order.getId()), kind, file);
    }

    @Override
    public void removeMedia(UUID id, UUID mediaId) {
        var order = loadForDoctor(id);
        if (!order.getStatus().isEditableByDoctor()) {
            throw DomainException.conflict("not_editable", "Files can only be removed from draft orders");
        }
        media.delete(mediaId, MediaOwner.order(order.getId()));
    }

    @Override
    public OrderDto submit(UUID id) {
        var order = loadForDoctor(id);
        var tenant = identity.currentTenant();
        order.markSubmitted(tenant.diagnosisPrice(), tenant.currency());
        workflow.transition(order, OrderStatus.SUBMITTED, null);
        return toDto(order);
    }

    @Override
    public OrderDto cancel(UUID id, String note) {
        var order = loadForDoctor(id);
        workflow.transition(order, OrderStatus.CANCELLED, note);
        return toDto(order);
    }

    // ------------------------------------------------------------------ laboratory: production & shipping

    @Override
    public OrderDto startProduction(UUID id) {
        var order = requireVisible(id);            // the state machine checks the PRODUCTION/LAB role
        workflow.transition(order, OrderStatus.IN_PRODUCTION, null);
        return toDto(order);
    }

    @Override
    public OrderDto ship(UUID id, ShipmentInput in) {
        var actor = TenantContext.require();
        var order = requireVisible(id);
        if (shipments.findByOrderId(order.getId()).isPresent()) {
            throw DomainException.conflict("already_shipped", "This order already has a shipment");
        }
        workflow.transition(order, OrderStatus.SHIPPED, in.trackingNumber() == null || in.trackingNumber().isBlank()
                ? null : (in.carrier() == null ? "" : in.carrier() + " ") + in.trackingNumber());
        shipments.save(new Shipment(order.getTenantId(), order.getId(), actor.personId(), in));
        return toDto(order);
    }

    @Override
    public OrderDto close(UUID id, String note) {
        var order = requireVisible(id);
        workflow.transition(order, OrderStatus.CLOSED, note);
        return toDto(order);
    }

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> list(OrderStatus status, UUID patientId) {
        var actor = TenantContext.require();
        UUID doctorFilter = actor.hasRole("DOCTOR") && !hasLabRole(actor) ? actor.personId() : null;
        if (actor.hasRole("PATIENT") && doctorFilter == null && !hasLabRole(actor)) {
            patientId = actor.personId();
        }
        var found = orders.search(doctorFilter, patientId, status);
        Map<UUID, String> names = namesFor(found);
        return found.stream()
                .map(o -> OrderDto.of(o, names.get(o.getDoctorId()), names.get(o.getPatientId()), List.of(), List.of(), null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto get(UUID id) {
        return toDto(requireVisible(id));
    }

    // ------------------------------------------------------------------ transitions for other modules

    @Override
    public OrderDto transition(UUID id, OrderStatus to, String note) {
        return toDto(workflow.transition(requireVisible(id), to, note));
    }

    @Override
    public void systemTransition(UUID id, OrderStatus to, String note) {
        var order = orders.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> DomainException.notFound("Order"));
        workflow.systemTransition(order, to, note);
    }

    // ------------------------------------------------------------------ helpers

    /** Order visible to the current actor: lab roles see all, doctors and patients only their own. */
    private TreatmentOrder requireVisible(UUID id) {
        var actor = TenantContext.require();
        var order = orders.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> DomainException.notFound("Order"));
        boolean allowed = hasLabRole(actor)
                || (actor.hasRole("DOCTOR") && order.getDoctorId().equals(actor.personId()))
                || (actor.hasRole("PATIENT") && order.getPatientId().equals(actor.personId()));
        if (!allowed) {
            throw DomainException.notFound("Order");
        }
        return order;
    }

    private TreatmentOrder loadForDoctor(UUID id) {
        var actor = requireDoctor();
        var order = orders.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> DomainException.notFound("Order"));
        if (!order.getDoctorId().equals(actor.personId())) {
            throw DomainException.notFound("Order");
        }
        return order;
    }

    private OrderDto toDto(TreatmentOrder o) {
        var hist = history.findByOrderIdOrderByChangedAtAsc(o.getId());
        var shipment = shipments.findByOrderId(o.getId()).orElse(null);
        Set<UUID> ids = new HashSet<>(List.of(o.getDoctorId(), o.getPatientId()));
        hist.forEach(h -> {
            if (h.getChangedBy() != null) {
                ids.add(h.getChangedBy());
            }
        });
        if (shipment != null && shipment.getShippedBy() != null) {
            ids.add(shipment.getShippedBy());
        }
        Map<UUID, String> names = identity.namesOf(ids);
        var historyDtos = hist.stream()
                .map(h -> new OrderDto.HistoryDto(h.getFromStatus(), h.getToStatus(), h.getChangedBy(),
                        h.getChangedBy() == null ? null : names.get(h.getChangedBy()), h.getNote(), h.getChangedAt()))
                .toList();
        return OrderDto.of(o, names.get(o.getDoctorId()), names.get(o.getPatientId()),
                media.listFor(MediaOwner.order(o.getId())), historyDtos,
                shipment == null ? null : ShipmentDto.from(shipment, names.get(shipment.getShippedBy())));
    }

    private Map<UUID, String> namesFor(List<TreatmentOrder> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(o -> {
            ids.add(o.getDoctorId());
            ids.add(o.getPatientId());
        });
        return identity.namesOf(ids);
    }

    private static boolean hasLabRole(TenantContext.Actor actor) {
        return actor.roles().stream().anyMatch(LAB_ROLES::contains);
    }

    private static TenantContext.Actor requireDoctor() {
        var actor = TenantContext.require();
        if (!actor.hasRole("DOCTOR")) {
            throw DomainException.forbidden("Only doctors can manage prescriptions");
        }
        return actor;
    }
}
