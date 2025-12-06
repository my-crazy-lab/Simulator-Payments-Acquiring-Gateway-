package com.paymentgateway.settlement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "disputes")
public class Dispute {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "dispute_id", unique = true, nullable = false)
    private String disputeId;
    
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(name = "reason_code")
    private String reasonCode;
    
    @Column(columnDefinition = "TEXT")
    private String reason;
    
    @Column(nullable = false)
    private String status = "OPEN"; // OPEN, PENDING_EVIDENCE, UNDER_REVIEW, WON, LOST, CLOSED
    
    @Column(name = "chargeback_reference")
    private String chargebackReference;
    
    @Column(name = "deadline")
    private OffsetDateTime deadline;
    
    @Column(name = "evidence_submitted_at")
    private OffsetDateTime evidenceSubmittedAt;
    
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
    
    @Column(name = "resolution")
    private String resolution;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    // Constructors
    public Dispute() {}
    
    public Dispute(String disputeId, UUID paymentId, UUID merchantId, 
                  BigDecimal amount, String currency, String reasonCode, String reason) {
        this.disputeId = disputeId;
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.reasonCode = reasonCode;
        this.reason = reason;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getDisputeId() {
        return disputeId;
    }
    
    public void setDisputeId(String disputeId) {
        this.disputeId = disputeId;
    }
    
    public UUID getPaymentId() {
        return paymentId;
    }
    
    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }
    
    public UUID getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getReasonCode() {
        return reasonCode;
    }
    
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getChargebackReference() {
        return chargebackReference;
    }
    
    public void setChargebackReference(String chargebackReference) {
        this.chargebackReference = chargebackReference;
    }
    
    public OffsetDateTime getDeadline() {
        return deadline;
    }
    
    public void setDeadline(OffsetDateTime deadline) {
        this.deadline = deadline;
    }
    
    public OffsetDateTime getEvidenceSubmittedAt() {
        return evidenceSubmittedAt;
    }
    
    public void setEvidenceSubmittedAt(OffsetDateTime evidenceSubmittedAt) {
        this.evidenceSubmittedAt = evidenceSubmittedAt;
    }
    
    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
    
    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
    
    public String getResolution() {
        return resolution;
    }
    
    public void setResolution(String resolution) {
        this.resolution = resolution;
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
}
