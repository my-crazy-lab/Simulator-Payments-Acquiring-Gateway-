package com.paymentgateway.settlement.repository;

import com.paymentgateway.settlement.domain.SettlementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, UUID> {
    List<SettlementTransaction> findByBatchId(UUID batchId);
    
    @Query("SELECT SUM(st.grossAmount) FROM SettlementTransaction st WHERE st.batchId = :batchId")
    BigDecimal sumGrossAmountByBatchId(UUID batchId);
}
