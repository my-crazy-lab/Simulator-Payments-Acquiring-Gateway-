package com.paymentgateway.settlement.repository;

import com.paymentgateway.settlement.domain.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    Optional<Dispute> findByDisputeId(String disputeId);
    List<Dispute> findByPaymentId(UUID paymentId);
    List<Dispute> findByMerchantId(UUID merchantId);
    List<Dispute> findByStatus(String status);
}
