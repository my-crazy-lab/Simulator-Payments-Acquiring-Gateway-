package com.paymentgateway.authorization.repository;

import com.paymentgateway.authorization.domain.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
    
    List<PaymentEvent> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
