package com.paymentgateway.authorization.repository;

import com.paymentgateway.authorization.domain.Payment;
import com.paymentgateway.authorization.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment repository with optimized queries for high-throughput processing.
 * 
 * Requirements: 20.2, 20.3, 20.4
 * - Prepared statements for SQL injection prevention and performance
 * - Batch operations for event logs
 * - Optimized indexes for payment workflows
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    
    /**
     * Find payment by payment ID with query hints for performance
     */
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.fetchSize", value = "1")
    })
    Optional<Payment> findByPaymentId(String paymentId);
    
    /**
     * Find payments by merchant and status with pagination
     */
    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId AND p.status = :status")
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "100")
    })
    Page<Payment> findByMerchantIdAndStatus(
        @Param("merchantId") UUID merchantId, 
        @Param("status") PaymentStatus status,
        Pageable pageable
    );
    
    /**
     * Find payments by merchant and status (non-paginated for backward compatibility)
     */
    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId AND p.status = :status")
    List<Payment> findByMerchantIdAndStatus(
        @Param("merchantId") UUID merchantId, 
        @Param("status") PaymentStatus status
    );
    
    /**
     * Find payments for settlement - captured but not yet settled
     * Uses index on (merchant_id, status, captured_at)
     */
    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId " +
           "AND p.status = 'CAPTURED' AND p.settledAt IS NULL " +
           "AND p.capturedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY p.capturedAt ASC")
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "500")
    })
    List<Payment> findPaymentsForSettlement(
        @Param("merchantId") UUID merchantId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    /**
     * Count payments by status for dashboard metrics
     * Uses index on (merchant_id, status)
     */
    @Query("SELECT p.status, COUNT(p), COALESCE(SUM(p.amount), 0) " +
           "FROM Payment p WHERE p.merchantId = :merchantId " +
           "AND p.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY p.status")
    List<Object[]> getPaymentStatsByMerchant(
        @Param("merchantId") UUID merchantId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    /**
     * Get total volume by currency for a merchant
     */
    @Query("SELECT p.currency, COALESCE(SUM(p.amount), 0) " +
           "FROM Payment p WHERE p.merchantId = :merchantId " +
           "AND p.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY p.currency")
    List<Object[]> getVolumeByMerchantAndCurrency(
        @Param("merchantId") UUID merchantId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    /**
     * Batch update payment status - useful for settlement processing
     */
    @Modifying
    @Query("UPDATE Payment p SET p.status = :newStatus, p.settledAt = :settledAt " +
           "WHERE p.id IN :paymentIds AND p.status = :currentStatus")
    int batchUpdateStatus(
        @Param("paymentIds") List<UUID> paymentIds,
        @Param("currentStatus") PaymentStatus currentStatus,
        @Param("newStatus") PaymentStatus newStatus,
        @Param("settledAt") Instant settledAt
    );
    
    /**
     * Find recent payments for fraud velocity checks
     * Uses index on (card_token_id, created_at)
     */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.cardTokenId = :cardTokenId " +
           "AND p.createdAt > :since")
    long countRecentPaymentsByCard(
        @Param("cardTokenId") UUID cardTokenId,
        @Param("since") Instant since
    );
    
    /**
     * Find payments by PSP transaction ID for reconciliation
     */
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true")
    })
    Optional<Payment> findByPspTransactionId(String pspTransactionId);
    
    /**
     * Check if payment exists by idempotency reference
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM Payment p WHERE p.referenceId = :referenceId AND p.merchantId = :merchantId")
    boolean existsByReferenceIdAndMerchantId(
        @Param("referenceId") String referenceId,
        @Param("merchantId") UUID merchantId
    );
}
