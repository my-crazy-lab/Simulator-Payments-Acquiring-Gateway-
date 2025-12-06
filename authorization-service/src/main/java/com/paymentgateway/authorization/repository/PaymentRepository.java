package com.paymentgateway.authorization.repository;

import com.paymentgateway.authorization.domain.Payment;
import com.paymentgateway.authorization.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    
    Optional<Payment> findByPaymentId(String paymentId);
    
    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId AND p.status = :status")
    Iterable<Payment> findByMerchantIdAndStatus(
        @Param("merchantId") UUID merchantId, 
        @Param("status") PaymentStatus status
    );
}
