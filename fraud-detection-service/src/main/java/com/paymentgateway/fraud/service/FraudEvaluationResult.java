package com.paymentgateway.fraud.service;

import com.paymentgateway.fraud.domain.FraudStatus;

import java.util.List;

public class FraudEvaluationResult {
    private double fraudScore;
    private FraudStatus status;
    private List<String> triggeredRules;
    private boolean require3DS;
    private String riskLevel;
    
    public FraudEvaluationResult() {
    }
    
    public FraudEvaluationResult(double fraudScore, FraudStatus status, List<String> triggeredRules,
                                 boolean require3DS, String riskLevel) {
        this.fraudScore = fraudScore;
        this.status = status;
        this.triggeredRules = triggeredRules;
        this.require3DS = require3DS;
        this.riskLevel = riskLevel;
    }
    
    // Getters and setters
    public double getFraudScore() {
        return fraudScore;
    }
    
    public void setFraudScore(double fraudScore) {
        this.fraudScore = fraudScore;
    }
    
    public FraudStatus getStatus() {
        return status;
    }
    
    public void setStatus(FraudStatus status) {
        this.status = status;
    }
    
    public List<String> getTriggeredRules() {
        return triggeredRules;
    }
    
    public void setTriggeredRules(List<String> triggeredRules) {
        this.triggeredRules = triggeredRules;
    }
    
    public boolean isRequire3DS() {
        return require3DS;
    }
    
    public void setRequire3DS(boolean require3DS) {
        this.require3DS = require3DS;
    }
    
    public String getRiskLevel() {
        return riskLevel;
    }
    
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
}
