package com.paymentgateway.fraud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class GeolocationService {
    
    private static final Logger logger = LoggerFactory.getLogger(GeolocationService.class);
    
    // High-risk countries (example list)
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "NG", "GH", "KE", "ZA", "RU", "CN", "VN"
    );
    
    public double assessRisk(String ipAddress, FraudEvaluationRequest.Address billingAddress) {
        double riskScore = 0.0;
        
        // In production, use a real IP geolocation service (MaxMind, IP2Location, etc.)
        String ipCountry = getCountryFromIP(ipAddress);
        
        // Check if IP country is high-risk
        if (ipCountry != null && HIGH_RISK_COUNTRIES.contains(ipCountry)) {
            riskScore += 0.4;
        }
        
        // Check if billing address country is high-risk
        if (billingAddress != null && billingAddress.getCountry() != null) {
            if (HIGH_RISK_COUNTRIES.contains(billingAddress.getCountry())) {
                riskScore += 0.3;
            }
            
            // Check for country mismatch
            if (ipCountry != null && !ipCountry.equals(billingAddress.getCountry())) {
                riskScore += 0.3;
                logger.info("Country mismatch detected: IP={}, Billing={}", ipCountry, billingAddress.getCountry());
            }
        }
        
        return Math.min(riskScore, 1.0);
    }
    
    private String getCountryFromIP(String ipAddress) {
        // Simplified implementation - in production use MaxMind GeoIP2 or similar
        if (ipAddress == null) {
            return null;
        }
        
        // Mock implementation for testing
        if (ipAddress.startsWith("41.")) {
            return "NG";  // Nigeria
        } else if (ipAddress.startsWith("102.")) {
            return "GH";  // Ghana
        } else if (ipAddress.startsWith("8.8.")) {
            return "US";  // USA
        }
        
        return "US";  // Default
    }
}
