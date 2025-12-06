package com.paymentgateway.settlement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlement_batches")
public class SettlementBatch {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "batch_id", unique = true, nullable = false)
    private String batchId;
    
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
    
    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status = SettlementStatus.PENDING;
    
    @Column(name = "bank_reference")
    private String bankReference;
    
    @Column(name = "acquirer_batch_id")
    private String acquirerBatchId;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
    
    // Constructors
    public SettlementBatch() {}
    
    public SettlementBatch(String batchId, UUID merchantId, LocalDate settlementDate, 
                          String currency, BigDecimal totalAmount, Integer transactionCount) {
        this.batchId = batchId;
        this.merchantId = merchantId;
        this.settlementDate = settlementDate;
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.transactionCount = transactionCount;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public UUID getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }
    
    public LocalDate getSettlementDate() {
        return settlementDate;
    }
    
    public void setSettlementDate(LocalDate settlementDate) {
        this.settlementDate = settlementDate;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public Integer getTransactionCount() {
        return transactionCount;
    }
    
    public void setTransactionCount(Integer transactionCount) {
        this.transactionCount = transactionCount;
    }
    
    public SettlementStatus getStatus() {
        return status;
    }
    
    public void setStatus(SettlementStatus status) {
        this.status = status;
    }
    
    public String getBankReference() {
        return bankReference;
    }
    
    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }
    
    public String getAcquirerBatchId() {
        return acquirerBatchId;
    }
    
    public void setAcquirerBatchId(String acquirerBatchId) {
        this.acquirerBatchId = acquirerBatchId;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }
    
    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
