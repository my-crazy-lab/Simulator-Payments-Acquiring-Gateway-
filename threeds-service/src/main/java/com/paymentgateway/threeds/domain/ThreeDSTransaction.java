package com.paymentgateway.threeds.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public class ThreeDSTransaction implements Serializable {
    private String transactionId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String cardToken;
    private ThreeDSStatus status;
    private String cavv;
    private String eci;
    private String xid;
    private String acsUrl;
    private String merchantReturnUrl;
    private BrowserInfo browserInfo;
    private Instant createdAt;
    private Instant expiresAt;
    private String errorMessage;

    public ThreeDSTransaction() {
    }

    public ThreeDSTransaction(String transactionId, String merchantId, BigDecimal amount, 
                             String currency, String cardToken, String merchantReturnUrl,
                             BrowserInfo browserInfo) {
        this.transactionId = transactionId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.cardToken = cardToken;
        this.merchantReturnUrl = merchantReturnUrl;
        this.browserInfo = browserInfo;
        this.status = ThreeDSStatus.UNKNOWN;
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(600); // 10 minutes
    }

    // Getters and setters
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
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

    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
    }

    public ThreeDSStatus getStatus() {
        return status;
    }

    public void setStatus(ThreeDSStatus status) {
        this.status = status;
    }

    public String getCavv() {
        return cavv;
    }

    public void setCavv(String cavv) {
        this.cavv = cavv;
    }

    public String getEci() {
        return eci;
    }

    public void setEci(String eci) {
        this.eci = eci;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public String getAcsUrl() {
        return acsUrl;
    }

    public void setAcsUrl(String acsUrl) {
        this.acsUrl = acsUrl;
    }

    public String getMerchantReturnUrl() {
        return merchantReturnUrl;
    }

    public void setMerchantReturnUrl(String merchantReturnUrl) {
        this.merchantReturnUrl = merchantReturnUrl;
    }

    public BrowserInfo getBrowserInfo() {
        return browserInfo;
    }

    public void setBrowserInfo(BrowserInfo browserInfo) {
        this.browserInfo = browserInfo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
