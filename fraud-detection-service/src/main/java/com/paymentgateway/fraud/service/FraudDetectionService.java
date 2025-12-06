package com.paymentgateway.fraud.service;

import com.paymentgateway.fraud.domain.FraudAlert;
import com.paymentgateway.fraud.domain.FraudRule;
import com.paymentgateway.fraud.domain.FraudStatus;
import com.paymentgateway.fraud.repository.BlacklistRepository;
import com.paymentgateway.fraud.repository.FraudAlertRepository;
import com.paymentgateway.fraud.repository.FraudRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FraudDetectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionService.class);
    
    private static final double HIGH_RISK_THRESHOLD = 0.75;
    private static final double REVIEW_THRESHOLD = 0.50;
    
    private final FraudRuleRepository fraudRuleRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final BlacklistRepository blacklistRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final VelocityCheckService velocityCheckService;
    private final GeolocationService geolocationService;
    private final MLFraudScoringService mlFraudScoringService;
    
    public FraudDetectionService(
            FraudRuleRepository fraudRuleRepository,
            FraudAlertRepository fraudAlertRepository,
            BlacklistRepository blacklistRepository,
            RedisTemplate<String, String> redisTemplate,
            VelocityCheckService velocityCheckService,
            GeolocationService geolocationService,
            MLFraudScoringService mlFraudScoringService) {
        this.fraudRuleRepository = fraudRuleRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.blacklistRepository = blacklistRepository;
        this.redisTemplate = redisTemplate;
        this.velocityCheckService = velocityCheckService;
        this.geolocationService = geolocationService;
        this.mlFraudScoringService = mlFraudScoringService;
    }
    
    @Transactional
    public FraudEvaluationResult evaluateTransaction(FraudEvaluationRequest request) {
        logger.info("Evaluating transaction {} for fraud", request.getTransactionId());
        
        // Check blacklist first - immediate rejection
        if (isBlacklisted(request)) {
            logger.warn("Transaction {} rejected - blacklisted source", request.getTransactionId());
            return createBlockedResult(request, 1.0, List.of("BLACKLIST_HIT"));
        }
        
        List<String> triggeredRules = new ArrayList<>();
        
        // Perform velocity checks
        boolean velocityCheckFailed = velocityCheckService.checkVelocity(
            request.getCardToken(),
            request.getIpAddress(),
            request.getMerchantId()
        );
        if (velocityCheckFailed) {
            triggeredRules.add("VELOCITY_LIMIT_EXCEEDED");
        }
        
        // Geolocation risk assessment
        double geoRiskScore = geolocationService.assessRisk(
            request.getIpAddress(),
            request.getBillingAddress()
        );
        if (geoRiskScore > 0.7) {
            triggeredRules.add("HIGH_GEO_RISK");
        }
        
        // Evaluate custom rules
        List<FraudRule> rules = fraudRuleRepository.findByEnabledTrueOrderByPriorityAsc();
        for (FraudRule rule : rules) {
            if (evaluateRule(rule, request)) {
                triggeredRules.add(rule.getRuleName());
            }
        }
        
        // ML-based fraud scoring
        double mlScore = mlFraudScoringService.calculateFraudScore(request);
        
        // Combine scores (weighted average)
        double finalScore = calculateFinalScore(mlScore, geoRiskScore, triggeredRules.size());
        
        // Ensure score is in valid range [0.0, 1.0]
        finalScore = Math.max(0.0, Math.min(1.0, finalScore));
        
        // Determine status and actions
        FraudStatus status;
        boolean require3DS = false;
        String riskLevel;
        
        if (finalScore >= HIGH_RISK_THRESHOLD) {
            status = FraudStatus.BLOCK;
            riskLevel = "HIGH";
            require3DS = true;
            createFraudAlert(request, finalScore, status, triggeredRules);
        } else if (finalScore >= REVIEW_THRESHOLD) {
            status = FraudStatus.REVIEW;
            riskLevel = "MEDIUM";
            require3DS = true;
            createFraudAlert(request, finalScore, status, triggeredRules);
        } else {
            status = FraudStatus.CLEAN;
            riskLevel = "LOW";
        }
        
        logger.info("Transaction {} evaluated: score={}, status={}, require3DS={}", 
            request.getTransactionId(), finalScore, status, require3DS);
        
        return new FraudEvaluationResult(
            finalScore,
            status,
            triggeredRules,
            require3DS,
            riskLevel
        );
    }
    
    private boolean isBlacklisted(FraudEvaluationRequest request) {
        // Check IP blacklist
        if (request.getIpAddress() != null && 
            blacklistRepository.existsByEntryTypeAndValue("IP", request.getIpAddress())) {
            return true;
        }
        
        // Check device fingerprint blacklist
        if (request.getDeviceFingerprint() != null && 
            blacklistRepository.existsByEntryTypeAndValue("DEVICE_FINGERPRINT", request.getDeviceFingerprint())) {
            return true;
        }
        
        // Check card hash blacklist (if available)
        if (request.getCardToken() != null && 
            blacklistRepository.existsByEntryTypeAndValue("CARD_HASH", request.getCardToken())) {
            return true;
        }
        
        return false;
    }
    
    private boolean evaluateRule(FraudRule rule, FraudEvaluationRequest request) {
        // Simple rule evaluation - in production this would be more sophisticated
        String condition = rule.getRuleCondition();
        
        // Example rule conditions:
        // "amount > 10000"
        // "country = 'NG'"
        // "hour >= 22 OR hour <= 6"
        
        try {
            // This is a simplified implementation
            // In production, use a proper rule engine like Drools or custom DSL
            if (condition.contains("amount >")) {
                String[] parts = condition.split(">");
                double threshold = Double.parseDouble(parts[1].trim());
                return request.getAmount().doubleValue() > threshold;
            }
            
            if (condition.contains("country =")) {
                String country = condition.split("=")[1].trim().replace("'", "");
                return request.getBillingAddress() != null && 
                       country.equals(request.getBillingAddress().getCountry());
            }
            
            // Add more rule evaluation logic as needed
            
        } catch (Exception e) {
            logger.error("Error evaluating rule {}: {}", rule.getRuleName(), e.getMessage());
        }
        
        return false;
    }
    
    private double calculateFinalScore(double mlScore, double geoScore, int ruleCount) {
        // Weighted combination of different signals
        double baseScore = (mlScore * 0.6) + (geoScore * 0.3);
        
        // Add penalty for triggered rules
        double rulePenalty = Math.min(ruleCount * 0.1, 0.3);
        
        return Math.min(baseScore + rulePenalty, 1.0);
    }
    
    private void createFraudAlert(
            FraudEvaluationRequest request,
            double fraudScore,
            FraudStatus status,
            List<String> triggeredRules) {
        
        FraudAlert alert = new FraudAlert();
        alert.setTransactionId(request.getTransactionId());
        alert.setAmount(request.getAmount());
        alert.setCurrency(request.getCurrency());
        alert.setFraudScore(fraudScore);
        alert.setStatus(status);
        alert.setTriggeredRules(String.join(", ", triggeredRules));
        alert.setMerchantId(request.getMerchantId());
        alert.setIpAddress(request.getIpAddress());
        alert.setDeviceFingerprint(request.getDeviceFingerprint());
        
        fraudAlertRepository.save(alert);
        
        logger.info("Created fraud alert for transaction {}", request.getTransactionId());
    }
    
    private FraudEvaluationResult createBlockedResult(
            FraudEvaluationRequest request,
            double score,
            List<String> triggeredRules) {
        
        createFraudAlert(request, score, FraudStatus.BLOCK, triggeredRules);
        
        return new FraudEvaluationResult(
            score,
            FraudStatus.BLOCK,
            triggeredRules,
            false,  // No 3DS needed - already blocked
            "BLOCKED"
        );
    }
}
