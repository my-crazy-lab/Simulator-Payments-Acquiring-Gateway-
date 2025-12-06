package com.paymentgateway.authorization.dto;

public class ApiKeyResponse {
    
    private String apiKey;
    private String message;
    
    // Constructors
    public ApiKeyResponse() {}
    
    public ApiKeyResponse(String apiKey, String message) {
        this.apiKey = apiKey;
        this.message = message;
    }
    
    // Getters and Setters
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
