package com.orthiva.core.order.infrastructure.persistence;

import com.orthiva.core.order.domain.TreatmentOrder;
import com.orthiva.core.order.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrderRepository extends JpaRepository<TreatmentOrder, UUID>, JpaSpecificationExecutor<TreatmentOrder> {

    Optional<TreatmentOrder> findByIdAndDeletedAtIsNull(UUID id);

    /** Dynamic filters (null = ignore); built in Java so Postgres never sees an untyped null parameter. */
    default List<TreatmentOrder> search(UUID doctorId, UUID patientId, OrderStatus status) {
        Specification<TreatmentOrder> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (doctorId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("doctorId"), doctorId));
        }
        if (patientId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        return findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
