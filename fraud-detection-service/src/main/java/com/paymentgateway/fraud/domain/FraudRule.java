package com.paymentgateway.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fraud_rules")
public class FraudRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String ruleName;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ruleCondition;
    
    @Column(nullable = false)
    private Integer priority;
    
    @Column(nullable = false)
    private Boolean enabled;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
    public FraudRule() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public FraudRule(String ruleName, String ruleCondition, Integer priority, Boolean enabled) {
        this();
        this.ruleName = ruleName;
        this.ruleCondition = ruleCondition;
        this.priority = priority;
        this.enabled = enabled;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public String getRuleCondition() {
        return ruleCondition;
    }
    
    public void setRuleCondition(String ruleCondition) {
        this.ruleCondition = ruleCondition;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
