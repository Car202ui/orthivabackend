package com.orthiva.core.order;

import java.util.HashMap;
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
import com.orthiva.core.identity.Person;
import com.orthiva.core.identity.PersonRepository;
import com.orthiva.core.patient.PatientService;
import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/** Prescriptions: the doctor's side of the order lifecycle (draft → submit) plus reads for lab and patient. */
@Service
@Transactional
public class OrderService {

    private static final Set<String> LAB_ROLES = Set.of("LAB", "PLANNER", "PRODUCTION", "ADMIN");

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderWorkflowService workflow;
    private final PatientService patients;
    private final PersonRepository persons;
    private final MediaService media;
    private final IdentityService identity;

    public OrderService(OrderRepository orders, OrderStatusHistoryRepository history, OrderWorkflowService workflow,
                        PatientService patients, PersonRepository persons, MediaService media, IdentityService identity) {
        this.orders = orders;
        this.history = history;
        this.workflow = workflow;
        this.patients = patients;
        this.persons = persons;
        this.media = media;
        this.identity = identity;
    }

    // ------------------------------------------------------------------ doctor

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

    public MediaDto addMedia(UUID id, MediaKind kind, MultipartFile file) {
        var order = loadForDoctor(id);
        if (!order.getStatus().isEditableByDoctor()) {
            throw DomainException.conflict("not_editable", "Files can only be added to draft orders");
        }
        return media.store(MediaOwner.order(order.getId()), kind, file);
    }

    public void removeMedia(UUID id, UUID mediaId) {
        var order = loadForDoctor(id);
        if (!order.getStatus().isEditableByDoctor()) {
            throw DomainException.conflict("not_editable", "Files can only be removed from draft orders");
        }
        media.delete(mediaId, MediaOwner.order(order.getId()));
    }

    /** DRAFT → SUBMITTED. Snapshots the diagnosis price; the payment module reacts to the event. */
    public OrderDto submit(UUID id) {
        var order = loadForDoctor(id);
        var tenant = identity.currentTenant();
        order.markSubmitted(tenant.getDiagnosisPrice(), tenant.getCurrency());
        workflow.transition(order, OrderStatus.SUBMITTED, null);
        return toDto(order);
    }

    public OrderDto cancel(UUID id, String note) {
        var order = loadForDoctor(id);
        workflow.transition(order, OrderStatus.CANCELLED, note);
        return toDto(order);
    }

    // ------------------------------------------------------------------ reads

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
                .map(o -> OrderDto.of(o, names.get(o.getDoctorId()), names.get(o.getPatientId()), List.of(), List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(UUID id) {
        var actor = TenantContext.require();
        var order = orders.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> DomainException.notFound("Order"));
        boolean allowed = hasLabRole(actor)
                || (actor.hasRole("DOCTOR") && order.getDoctorId().equals(actor.personId()))
                || (actor.hasRole("PATIENT") && order.getPatientId().equals(actor.personId()));
        if (!allowed) {
            throw DomainException.notFound("Order");
        }
        return toDto(order);
    }

    // ------------------------------------------------------------------ helpers

    private TreatmentOrder loadForDoctor(UUID id) {
        var actor = requireDoctor();
        var order = orders.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> DomainException.notFound("Order"));
        if (!order.getDoctorId().equals(actor.personId())) {
            throw DomainException.notFound("Order");
        }
        return order;
    }

    private OrderDto toDto(TreatmentOrder o) {
        Map<UUID, String> names = namesFor(List.of(o));
        var hist = history.findByOrderIdOrderByChangedAtAsc(o.getId()).stream()
                .map(h -> new OrderDto.HistoryDto(h.getFromStatus(), h.getToStatus(), h.getChangedBy(),
                        h.getChangedBy() == null ? null : persons.findById(h.getChangedBy()).map(Person::getFullName).orElse(null),
                        h.getNote(), h.getChangedAt()))
                .toList();
        return OrderDto.of(o, names.get(o.getDoctorId()), names.get(o.getPatientId()),
                media.listFor(MediaOwner.order(o.getId())), hist);
    }

    private Map<UUID, String> namesFor(List<TreatmentOrder> list) {
        Map<UUID, String> names = new HashMap<>();
        list.forEach(o -> {
            names.computeIfAbsent(o.getDoctorId(), id -> persons.findById(id).map(Person::getFullName).orElse("?"));
            names.computeIfAbsent(o.getPatientId(), id -> persons.findById(id).map(Person::getFullName).orElse("?"));
        });
        return names;
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
