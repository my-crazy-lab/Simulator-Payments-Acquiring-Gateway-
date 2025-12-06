package com.paymentgateway.authorization.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    
    @NotBlank(message = "Merchant ID is required")
    private String merchantId;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    // Constructors
    public LoginRequest() {}
    
    public LoginRequest(String merchantId, String password) {
        this.merchantId = merchantId;
        this.password = password;
    }
    
    // Getters and Setters
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
