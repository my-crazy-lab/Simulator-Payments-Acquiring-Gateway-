package com.paymentgateway.fraud.service;

import java.math.BigDecimal;
import java.util.Map;

public class FraudEvaluationRequest {
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String cardToken;
    private String ipAddress;
    private String deviceFingerprint;
    private Address billingAddress;
    private String merchantId;
    private Map<String, String> metadata;
    
    public FraudEvaluationRequest() {
    }
    
    public FraudEvaluationRequest(String transactionId, BigDecimal amount, String currency,
                                  String cardToken, String ipAddress, String deviceFingerprint,
                                  Address billingAddress, String merchantId, Map<String, String> metadata) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency;
        this.cardToken = cardToken;
        this.ipAddress = ipAddress;
        this.deviceFingerprint = deviceFingerprint;
        this.billingAddress = billingAddress;
        this.merchantId = merchantId;
        this.metadata = metadata;
    }
    
    // Getters and setters
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
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
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }
    
    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }
    
    public Address getBillingAddress() {
        return billingAddress;
    }
    
    public void setBillingAddress(Address billingAddress) {
        this.billingAddress = billingAddress;
    }
    
    public String getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        
        public Address() {
        }
        
        public Address(String street, String city, String state, String postalCode, String country) {
            this.street = street;
            this.city = city;
            this.state = state;
            this.postalCode = postalCode;
            this.country = country;
        }
        
        // Getters and setters
        public String getStreet() {
            return street;
        }
        
        public void setStreet(String street) {
            this.street = street;
        }
        
        public String getCity() {
            return city;
        }
        
        public void setCity(String city) {
            this.city = city;
        }
        
        public String getState() {
            return state;
        }
        
        public void setState(String state) {
            this.state = state;
        }
        
        public String getPostalCode() {
            return postalCode;
        }
        
        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }
        
        public String getCountry() {
            return country;
        }
        
        public void setCountry(String country) {
            this.country = country;
        }
    }
}
