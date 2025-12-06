package com.paymentgateway.authorization.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "payment_id", unique = true, nullable = false)
    private String paymentId;
    
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "reference_id", length = 100)
    private String referenceId;
    
    @Column(name = "card_token_id")
    private UUID cardTokenId;
    
    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "card_brand")
    private CardBrand cardBrand;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType = TransactionType.AUTHORIZATION;
    
    @Column(name = "psp_transaction_id", length = 100)
    private String pspTransactionId;
    
    @Column(name = "psp_reference", length = 100)
    private String pspReference;
    
    @Column(name = "acquirer_reference", length = 100)
    private String acquirerReference;
    
    @Column(name = "fraud_score", precision = 3, scale = 2)
    private BigDecimal fraudScore;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "fraud_status")
    private FraudStatus fraudStatus = FraudStatus.CLEAN;
    
    @Column(name = "fraud_reason", columnDefinition = "TEXT")
    private String fraudReason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "three_ds_status")
    private ThreeDSStatus threeDsStatus = ThreeDSStatus.NOT_ENROLLED;
    
    @Column(name = "three_ds_transaction_id", length = 100)
    private String threeDsTransactionId;
    
    @Column(name = "three_ds_eci", length = 2)
    private String threeDsEci;
    
    @Column(name = "three_ds_cavv", columnDefinition = "TEXT")
    private String threeDsCavv;
    
    @Column(name = "three_ds_xid", columnDefinition = "TEXT")
    private String threeDsXid;
    
    @Column(name = "billing_street", columnDefinition = "TEXT")
    private String billingStreet;
    
    @Column(name = "billing_city", length = 100)
    private String billingCity;
    
    @Column(name = "billing_state", length = 100)
    private String billingState;
    
    @Column(name = "billing_zip", length = 20)
    private String billingZip;
    
    @Column(name = "billing_country", length = 2)
    private String billingCountry;
    
    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    
    @Column(name = "last_retry_at")
    private Instant lastRetryAt;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    
    @Column(name = "authorized_at")
    private Instant authorizedAt;
    
    @Column(name = "captured_at")
    private Instant capturedAt;
    
    @Column(name = "settled_at")
    private Instant settledAt;
    
    // Constructors
    public Payment() {}
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    
    public UUID getCardTokenId() { return cardTokenId; }
    public void setCardTokenId(UUID cardTokenId) { this.cardTokenId = cardTokenId; }
    
    public String getCardLastFour() { return cardLastFour; }
    public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
    
    public CardBrand getCardBrand() { return cardBrand; }
    public void setCardBrand(CardBrand cardBrand) { this.cardBrand = cardBrand; }
    
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    
    public String getPspTransactionId() { return pspTransactionId; }
    public void setPspTransactionId(String pspTransactionId) { this.pspTransactionId = pspTransactionId; }
    
    public String getPspReference() { return pspReference; }
    public void setPspReference(String pspReference) { this.pspReference = pspReference; }
    
    public String getAcquirerReference() { return acquirerReference; }
    public void setAcquirerReference(String acquirerReference) { this.acquirerReference = acquirerReference; }
    
    public BigDecimal getFraudScore() { return fraudScore; }
    public void setFraudScore(BigDecimal fraudScore) { this.fraudScore = fraudScore; }
    
    public FraudStatus getFraudStatus() { return fraudStatus; }
    public void setFraudStatus(FraudStatus fraudStatus) { this.fraudStatus = fraudStatus; }
    
    public String getFraudReason() { return fraudReason; }
    public void setFraudReason(String fraudReason) { this.fraudReason = fraudReason; }
    
    public ThreeDSStatus getThreeDsStatus() { return threeDsStatus; }
    public void setThreeDsStatus(ThreeDSStatus threeDsStatus) { this.threeDsStatus = threeDsStatus; }
    
    public String getThreeDsTransactionId() { return threeDsTransactionId; }
    public void setThreeDsTransactionId(String threeDsTransactionId) { this.threeDsTransactionId = threeDsTransactionId; }
    
    public String getThreeDsEci() { return threeDsEci; }
    public void setThreeDsEci(String threeDsEci) { this.threeDsEci = threeDsEci; }
    
    public String getThreeDsCavv() { return threeDsCavv; }
    public void setThreeDsCavv(String threeDsCavv) { this.threeDsCavv = threeDsCavv; }
    
    public String getThreeDsXid() { return threeDsXid; }
    public void setThreeDsXid(String threeDsXid) { this.threeDsXid = threeDsXid; }
    
    public String getBillingStreet() { return billingStreet; }
    public void setBillingStreet(String billingStreet) { this.billingStreet = billingStreet; }
    
    public String getBillingCity() { return billingCity; }
    public void setBillingCity(String billingCity) { this.billingCity = billingCity; }
    
    public String getBillingState() { return billingState; }
    public void setBillingState(String billingState) { this.billingState = billingState; }
    
    public String getBillingZip() { return billingZip; }
    public void setBillingZip(String billingZip) { this.billingZip = billingZip; }
    
    public String getBillingCountry() { return billingCountry; }
    public void setBillingCountry(String billingCountry) { this.billingCountry = billingCountry; }
    
    public Integer getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Instant getLastRetryAt() { return lastRetryAt; }
    public void setLastRetryAt(Instant lastRetryAt) { this.lastRetryAt = lastRetryAt; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public Instant getAuthorizedAt() { return authorizedAt; }
    public void setAuthorizedAt(Instant authorizedAt) { this.authorizedAt = authorizedAt; }
    
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
