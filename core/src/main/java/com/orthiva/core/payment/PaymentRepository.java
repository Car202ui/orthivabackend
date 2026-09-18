package com.orthiva.core.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Optional<Payment> findFirstByOrderIdAndPurposeAndStatus(UUID orderId, Payment.Purpose purpose, Payment.Status status);

    Optional<Payment> findByGatewayAndGatewayReference(String gateway, String gatewayReference);
}
