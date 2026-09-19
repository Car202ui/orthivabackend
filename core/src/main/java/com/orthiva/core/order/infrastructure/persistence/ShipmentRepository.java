package com.orthiva.core.order.infrastructure.persistence;

import com.orthiva.core.order.domain.Shipment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByOrderId(UUID orderId);
}
