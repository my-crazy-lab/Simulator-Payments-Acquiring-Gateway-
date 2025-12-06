package com.paymentgateway.fraud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MLFraudScoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(MLFraudScoringService.class);
    
    public double calculateFraudScore(FraudEvaluationRequest request) {
        // In production, this would call a real ML model (TensorFlow, PyTorch, etc.)
        // For now, we use a simple heuristic-based scoring
        
        double score = 0.0;
        
        // Amount-based risk
        BigDecimal amount = request.getAmount();
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            score += 0.3;
        } else if (amount.compareTo(new BigDecimal("5000")) > 0) {
            score += 0.2;
        } else if (amount.compareTo(new BigDecimal("1000")) > 0) {
            score += 0.1;
        }
        
        // Missing information increases risk
        if (request.getDeviceFingerprint() == null) {
            score += 0.1;
        }
        
        if (request.getBillingAddress() == null) {
            score += 0.15;
        }
        
        // Time-based risk (transactions at odd hours)
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 22 || hour <= 6) {
            score += 0.1;
        }
        
        // Random component to simulate ML model variability
        // In production, this would be the actual ML model prediction
        double mlComponent = Math.random() * 0.2;
        score += mlComponent;
        
        return Math.min(score, 1.0);
    }
}
