package com.paymentgateway.authorization.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "merchant_id", unique = true, nullable = false, length = 50)
    private String merchantId;
    
    @Column(name = "merchant_name", nullable = false)
    private String merchantName;
    
    @Column(name = "mcc", length = 4)
    private String mcc; // Merchant Category Code
    
    @Column(name = "country_code", length = 2)
    private String countryCode;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "risk_level", length = 20)
    private String riskLevel;
    
    @Column(name = "pci_compliance_level", length = 10)
    private String pciComplianceLevel;
    
    @Column(name = "last_pci_scan")
    private LocalDate lastPciScan;
    
    @Column(name = "api_key_hash")
    private String apiKeyHash;
    
    @Column(name = "webhook_url")
    private String webhookUrl;
    
    @Column(name = "webhook_secret_hash")
    private String webhookSecretHash;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "rate_limit_per_second")
    private Integer rateLimitPerSecond = 100;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "merchant_roles", joinColumns = @JoinColumn(name = "merchant_id"))
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();
    
    // Constructors
    public Merchant() {}
    
    public Merchant(String merchantId, String merchantName) {
        this.merchantId = merchantId;
        this.merchantName = merchantName;
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    
    public String getMcc() { return mcc; }
    public void setMcc(String mcc) { this.mcc = mcc; }
    
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public String getPciComplianceLevel() { return pciComplianceLevel; }
    public void setPciComplianceLevel(String pciComplianceLevel) { this.pciComplianceLevel = pciComplianceLevel; }
    
    public LocalDate getLastPciScan() { return lastPciScan; }
    public void setLastPciScan(LocalDate lastPciScan) { this.lastPciScan = lastPciScan; }
    
    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }
    
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    
    public String getWebhookSecretHash() { return webhookSecretHash; }
    public void setWebhookSecretHash(String webhookSecretHash) { this.webhookSecretHash = webhookSecretHash; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public Integer getRateLimitPerSecond() { return rateLimitPerSecond; }
    public void setRateLimitPerSecond(Integer rateLimitPerSecond) { this.rateLimitPerSecond = rateLimitPerSecond; }
    
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    
    public void addRole(String role) {
        this.roles.add(role);
    }
}
