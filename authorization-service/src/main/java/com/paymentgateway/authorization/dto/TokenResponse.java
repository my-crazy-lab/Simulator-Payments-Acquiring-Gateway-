package com.paymentgateway.authorization.dto;

public class TokenResponse {
    
    private String token;
    private String tokenType;
    private long expiresIn;
    private String merchantId;
    
    // Constructors
    public TokenResponse() {}
    
    public TokenResponse(String token, String tokenType, long expiresIn, String merchantId) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.merchantId = merchantId;
    }
    
    // Getters and Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
}
