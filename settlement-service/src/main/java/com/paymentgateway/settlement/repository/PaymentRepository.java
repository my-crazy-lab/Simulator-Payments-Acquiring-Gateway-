package com.paymentgateway.settlement.repository;

import com.paymentgateway.settlement.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByPaymentId(String paymentId);
    
    @Query("SELECT p FROM Payment p WHERE p.status = 'CAPTURED' AND p.settledAt IS NULL AND p.capturedAt < :cutoffTime")
    List<Payment> findUnsettledCapturedPayments(OffsetDateTime cutoffTime);
    
    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId AND p.status = 'CAPTURED' AND p.settledAt IS NULL AND p.capturedAt < :cutoffTime")
    List<Payment> findUnsettledCapturedPaymentsByMerchant(UUID merchantId, OffsetDateTime cutoffTime);
}
