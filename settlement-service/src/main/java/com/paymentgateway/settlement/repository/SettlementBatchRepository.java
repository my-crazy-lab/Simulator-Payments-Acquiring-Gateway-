package com.paymentgateway.settlement.repository;

import com.paymentgateway.settlement.domain.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {
    Optional<SettlementBatch> findByBatchId(String batchId);
    List<SettlementBatch> findByMerchantIdAndSettlementDate(UUID merchantId, LocalDate settlementDate);
    List<SettlementBatch> findBySettlementDate(LocalDate settlementDate);
}
