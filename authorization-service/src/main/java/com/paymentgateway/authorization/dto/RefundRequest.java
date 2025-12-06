package com.paymentgateway.authorization.dto;

import com.paymentgateway.authorization.validation.ValidAmount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class RefundRequest {
    
    @NotBlank(message = "Payment ID is required")
    private String paymentId;
    
    @NotNull(message = "Amount is required")
    @ValidAmount
    private BigDecimal amount;
    
    private String reason;
    
    // Constructors
    public RefundRequest() {}
    
    public RefundRequest(String paymentId, BigDecimal amount) {
        this.paymentId = paymentId;
        this.amount = amount;
    }
    
    public RefundRequest(String paymentId, BigDecimal amount, String reason) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
    }
    
    // Getters and Setters
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
