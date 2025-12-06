package com.paymentgateway.authorization.psp;

import java.math.BigDecimal;
import java.util.UUID;

public class PSPAuthorizationRequest {
    
    private UUID merchantId;
    private BigDecimal amount;
    private String currency;
    private UUID cardTokenId;
    private String cardLastFour;
    private String cardBrand;
    private String description;
    private String referenceId;
    
    // 3DS authentication data
    private String cavv;
    private String eci;
    private String xid;
    
    // Billing address
    private String billingStreet;
    private String billingCity;
    private String billingState;
    private String billingZip;
    private String billingCountry;
    
    // Constructors
    public PSPAuthorizationRequest() {}
    
    public PSPAuthorizationRequest(UUID merchantId, BigDecimal amount, String currency, UUID cardTokenId) {
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.cardTokenId = cardTokenId;
    }
    
    // Getters and Setters
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public UUID getCardTokenId() { return cardTokenId; }
    public void setCardTokenId(UUID cardTokenId) { this.cardTokenId = cardTokenId; }
    
    public String getCardLastFour() { return cardLastFour; }
    public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
    
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    
    public String getCavv() { return cavv; }
    public void setCavv(String cavv) { this.cavv = cavv; }
    
    public String getEci() { return eci; }
    public void setEci(String eci) { this.eci = eci; }
    
    public String getXid() { return xid; }
    public void setXid(String xid) { this.xid = xid; }
    
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
}
