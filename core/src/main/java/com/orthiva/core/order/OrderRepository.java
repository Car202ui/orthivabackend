package com.orthiva.core.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<TreatmentOrder, UUID> {

    Optional<TreatmentOrder> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select o from TreatmentOrder o
            where o.deletedAt is null
              and (:doctorId is null or o.doctorId = :doctorId)
              and (:patientId is null or o.patientId = :patientId)
              and (:status is null or o.status = :status)
            order by o.createdAt desc
            """)
    List<TreatmentOrder> search(@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId,
                                @Param("status") OrderStatus status);
}
