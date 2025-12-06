package com.paymentgateway.authorization.repository;

import com.paymentgateway.authorization.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    
    List<WebhookDelivery> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    
    List<WebhookDelivery> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
    
    @Query("SELECT w FROM WebhookDelivery w WHERE w.status = 'PENDING' AND w.nextRetryAt <= :now")
    List<WebhookDelivery> findPendingRetries(@Param("now") Instant now);
    
    @Query("SELECT w FROM WebhookDelivery w WHERE w.merchantId = :merchantId AND w.status = :status")
    List<WebhookDelivery> findByMerchantIdAndStatus(@Param("merchantId") UUID merchantId, 
                                                     @Param("status") String status);
}
