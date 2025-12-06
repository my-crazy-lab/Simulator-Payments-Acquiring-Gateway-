package com.paymentgateway.authorization.repository;

import com.paymentgateway.authorization.domain.Refund;
import com.paymentgateway.authorization.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    
    Optional<Refund> findByRefundId(String refundId);
    
    List<Refund> findByPaymentId(UUID paymentId);
    
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.paymentId = :paymentId AND r.status IN :statuses")
    BigDecimal sumRefundedAmountByPaymentIdAndStatuses(
        @Param("paymentId") UUID paymentId,
        @Param("statuses") List<RefundStatus> statuses
    );
}
