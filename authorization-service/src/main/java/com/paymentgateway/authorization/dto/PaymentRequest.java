package com.paymentgateway.authorization.dto;

import com.paymentgateway.authorization.validation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@ValidExpiryDate
public class PaymentRequest {
    
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "^[0-9]{13,19}$", message = "Invalid card number format")
    @LuhnCheck
    private String cardNumber;
    
    @NotNull(message = "Expiry month is required")
    @Min(value = 1, message = "Expiry month must be between 1 and 12")
    @Max(value = 12, message = "Expiry month must be between 1 and 12")
    private Integer expiryMonth;
    
    @NotNull(message = "Expiry year is required")
    @Min(value = 2025, message = "Card has expired")
    private Integer expiryYear;
    
    @NotBlank(message = "CVV is required")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "Invalid CVV format")
    private String cvv;
    
    @NotNull(message = "Amount is required")
    @ValidAmount
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @ValidCurrency
    private String currency;
    
    private String description;
    private String referenceId;
    
    // Billing address
    private String billingStreet;
    private String billingCity;
    private String billingState;
    private String billingZip;
    
    @Pattern(regexp = "^[A-Z]{2}$", message = "Invalid country code")
    private String billingCountry;
    
    // Constructors
    public PaymentRequest() {}
    
    public PaymentRequest(String cardNumber, Integer expiryMonth, Integer expiryYear, 
                         String cvv, BigDecimal amount, String currency) {
        this.cardNumber = cardNumber;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.cvv = cvv;
        this.amount = amount;
        this.currency = currency;
    }
    
    // Getters and Setters
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    
    public Integer getExpiryMonth() { return expiryMonth; }
    public void setExpiryMonth(Integer expiryMonth) { this.expiryMonth = expiryMonth; }
    
    public Integer getExpiryYear() { return expiryYear; }
    public void setExpiryYear(Integer expiryYear) { this.expiryYear = expiryYear; }
    
    public String getCvv() { return cvv; }
    public void setCvv(String cvv) { this.cvv = cvv; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    
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
