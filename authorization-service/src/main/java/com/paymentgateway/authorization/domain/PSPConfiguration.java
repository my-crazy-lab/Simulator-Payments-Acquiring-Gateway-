package com.paymentgateway.authorization.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "psp_configurations")
public class PSPConfiguration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
    
    @Column(name = "psp_name", nullable = false, length = 50)
    private String pspName;
    
    @Column(name = "priority", nullable = false)
    private Integer priority; // Lower number = higher priority
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "api_key_encrypted")
    private String apiKeyEncrypted;
    
    @Column(name = "merchant_account_id")
    private String merchantAccountId;
    
    @Column(name = "endpoint_url")
    private String endpointUrl;
    
    // Constructors
    public PSPConfiguration() {}
    
    public PSPConfiguration(UUID merchantId, String pspName, Integer priority) {
        this.merchantId = merchantId;
        this.pspName = pspName;
        this.priority = priority;
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    
    public String getPspName() { return pspName; }
    public void setPspName(String pspName) { this.pspName = pspName; }
    
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public String getApiKeyEncrypted() { return apiKeyEncrypted; }
    public void setApiKeyEncrypted(String apiKeyEncrypted) { this.apiKeyEncrypted = apiKeyEncrypted; }
    
    public String getMerchantAccountId() { return merchantAccountId; }
    public void setMerchantAccountId(String merchantAccountId) { this.merchantAccountId = merchantAccountId; }
    
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
}
