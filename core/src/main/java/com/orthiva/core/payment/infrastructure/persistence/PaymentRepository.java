package com.orthiva.core.payment.infrastructure.persistence;

import com.orthiva.core.payment.PaymentPurpose;
import com.orthiva.core.payment.PaymentStatus;
import com.orthiva.core.payment.domain.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Optional<Payment> findFirstByOrderIdAndPurposeAndStatus(UUID orderId, PaymentPurpose purpose, PaymentStatus status);

    Optional<Payment> findByGatewayAndGatewayReference(String gateway, String gatewayReference);
}
