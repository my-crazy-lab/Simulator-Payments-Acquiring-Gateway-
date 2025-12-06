package com.paymentgateway.authorization.degradation;

import com.paymentgateway.authorization.health.DatabaseHealthIndicator;
import com.paymentgateway.authorization.health.KafkaHealthIndicator;
import com.paymentgateway.authorization.health.PSPHealthIndicator;
import com.paymentgateway.authorization.health.RedisHealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service for managing graceful degradation when non-critical services fail.
 * Provides fallback logic for fraud detection, 3DS, cache, and Kafka.
 * 
 * Requirements: 30.1, 30.2, 30.3, 30.4, 30.5 - Graceful degradation modes
 */
@Service
public class GracefulDegradationService {

    private static final Logger logger = LoggerFactory.getLogger(GracefulDegradationService.class);

    // Service availability flags
    private volatile boolean fraudServiceAvailable = true;
    private volatile boolean threeDSServiceAvailable = true;
    private volatile boolean cacheAvailable = true;
    private volatile boolean kafkaAvailable = true;

    // Buffered events for Kafka unavailability
    private final Queue<BufferedEvent> eventBuffer = new ConcurrentLinkedQueue<>();
    private static final int MAX_BUFFER_SIZE = 10000;

    // Degradation state tracking
    private final Map<ServiceType, DegradationState> degradationStates = new ConcurrentHashMap<>();

    private final DatabaseHealthIndicator databaseHealthIndicator;
    private final RedisHealthIndicator redisHealthIndicator;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final PSPHealthIndicator pspHealthIndicator;

    public GracefulDegradationService(
            DatabaseHealthIndicator databaseHealthIndicator,
            RedisHealthIndicator redisHealthIndicator,
            KafkaHealthIndicator kafkaHealthIndicator,
            PSPHealthIndicator pspHealthIndicator) {
        this.databaseHealthIndicator = databaseHealthIndicator;
        this.redisHealthIndicator = redisHealthIndicator;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.pspHealthIndicator = pspHealthIndicator;

        // Initialize degradation states
        for (ServiceType type : ServiceType.values()) {
            degradationStates.put(type, new DegradationState(type, false, null));
        }
    }


    /**
     * Fallback for fraud detection when the fraud service is unavailable.
     * Uses basic rule-based fraud checks instead of ML-based scoring.
     * 
     * Requirements: 30.1 - Fallback for fraud service unavailability
     */
    public FraudFallbackResult performFraudFallback(FraudFallbackRequest request) {
        logger.warn("Fraud service unavailable, using rule-based fallback for transaction: {}", 
                   request.getTransactionId());
        
        markServiceDegraded(ServiceType.FRAUD_DETECTION, "Fraud service unavailable, using rule-based checks");
        
        // Basic rule-based fraud checks
        List<String> triggeredRules = new ArrayList<>();
        BigDecimal riskScore = BigDecimal.ZERO;
        
        // Rule 1: High amount check (transactions over $10,000 are flagged)
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            triggeredRules.add("HIGH_AMOUNT");
            riskScore = riskScore.add(new BigDecimal("0.3"));
        }
        
        // Rule 2: International transaction check
        if (request.getBillingCountry() != null && 
            !request.getBillingCountry().equalsIgnoreCase(request.getMerchantCountry())) {
            triggeredRules.add("INTERNATIONAL_TRANSACTION");
            riskScore = riskScore.add(new BigDecimal("0.2"));
        }
        
        // Rule 3: First-time card check (simplified - would check history in real impl)
        if (request.isFirstTimeCard()) {
            triggeredRules.add("FIRST_TIME_CARD");
            riskScore = riskScore.add(new BigDecimal("0.15"));
        }
        
        // Cap risk score at 1.0
        if (riskScore.compareTo(BigDecimal.ONE) > 0) {
            riskScore = BigDecimal.ONE;
        }
        
        // Determine status based on risk score
        FraudStatus status;
        boolean require3DS = false;
        
        if (riskScore.compareTo(new BigDecimal("0.7")) >= 0) {
            status = FraudStatus.BLOCK;
        } else if (riskScore.compareTo(new BigDecimal("0.4")) >= 0) {
            status = FraudStatus.REVIEW;
            require3DS = true;
        } else {
            status = FraudStatus.CLEAN;
        }
        
        logger.info("Fraud fallback completed: transactionId={}, riskScore={}, status={}, triggeredRules={}", 
                   request.getTransactionId(), riskScore, status, triggeredRules);
        
        return new FraudFallbackResult(
            riskScore,
            status,
            triggeredRules,
            require3DS,
            true // isFallback
        );
    }

    /**
     * Fallback for 3D Secure when the 3DS service is unavailable.
     * Processes transactions without 3DS while logging the degradation.
     * 
     * Requirements: 30.2 - Fallback for 3DS service failures
     */
    public ThreeDSFallbackResult performThreeDSFallback(String transactionId, BigDecimal amount) {
        logger.warn("3DS service unavailable, processing without 3DS for transaction: {}", transactionId);
        
        markServiceDegraded(ServiceType.THREE_DS, "3DS service unavailable, processing without authentication");
        
        // Return a result indicating 3DS was skipped
        return new ThreeDSFallbackResult(
            transactionId,
            ThreeDSStatus.NOT_ENROLLED,
            null, // No CAVV
            null, // No ECI
            "3DS service unavailable - transaction processed without authentication",
            true  // isFallback
        );
    }

    /**
     * Fallback for cache unavailability.
     * Falls back to direct database queries.
     * 
     * Requirements: 30.3 - Fallback for cache unavailability
     */
    public <T> T performCacheFallback(String cacheKey, CacheFallbackSupplier<T> databaseFallback) {
        logger.warn("Cache unavailable, falling back to database for key: {}", cacheKey);
        
        markServiceDegraded(ServiceType.CACHE, "Redis cache unavailable, using direct database queries");
        
        try {
            return databaseFallback.get();
        } catch (Exception e) {
            logger.error("Database fallback also failed for cache key: {}", cacheKey, e);
            throw new RuntimeException("Both cache and database fallback failed", e);
        }
    }

    /**
     * Fallback for Kafka unavailability.
     * Buffers events locally for replay when connectivity is restored.
     * 
     * Requirements: 30.4 - Fallback for Kafka unavailability
     */
    public boolean bufferEventForKafka(String topic, String key, Object payload) {
        logger.warn("Kafka unavailable, buffering event for topic: {}, key: {}", topic, key);
        
        markServiceDegraded(ServiceType.KAFKA, "Kafka unavailable, events being buffered locally");
        
        if (eventBuffer.size() >= MAX_BUFFER_SIZE) {
            logger.error("Event buffer full, dropping event for topic: {}, key: {}", topic, key);
            return false;
        }
        
        BufferedEvent event = new BufferedEvent(topic, key, payload, Instant.now());
        eventBuffer.offer(event);
        
        logger.info("Event buffered successfully. Buffer size: {}", eventBuffer.size());
        return true;
    }

    /**
     * Replay buffered events when Kafka becomes available.
     */
    public List<BufferedEvent> drainBufferedEvents() {
        List<BufferedEvent> events = new ArrayList<>();
        BufferedEvent event;
        while ((event = eventBuffer.poll()) != null) {
            events.add(event);
        }
        
        if (!events.isEmpty()) {
            logger.info("Drained {} buffered events for replay", events.size());
            clearDegradation(ServiceType.KAFKA);
        }
        
        return events;
    }

    /**
     * Get the current buffered event count.
     */
    public int getBufferedEventCount() {
        return eventBuffer.size();
    }


    // ==================== Service Availability Management ====================

    public void setFraudServiceAvailable(boolean available) {
        this.fraudServiceAvailable = available;
        if (available) {
            clearDegradation(ServiceType.FRAUD_DETECTION);
        }
    }

    public void setThreeDSServiceAvailable(boolean available) {
        this.threeDSServiceAvailable = available;
        if (available) {
            clearDegradation(ServiceType.THREE_DS);
        }
    }

    public void setCacheAvailable(boolean available) {
        this.cacheAvailable = available;
        if (available) {
            clearDegradation(ServiceType.CACHE);
        }
    }

    public void setKafkaAvailable(boolean available) {
        this.kafkaAvailable = available;
        if (available) {
            clearDegradation(ServiceType.KAFKA);
        }
    }

    public boolean isFraudServiceAvailable() {
        return fraudServiceAvailable;
    }

    public boolean isThreeDSServiceAvailable() {
        return threeDSServiceAvailable;
    }

    public boolean isCacheAvailable() {
        return cacheAvailable;
    }

    public boolean isKafkaAvailable() {
        return kafkaAvailable;
    }

    // ==================== Health Status Indicators ====================

    /**
     * Get comprehensive health status indicating which services are impaired.
     * 
     * Requirements: 30.5 - Health endpoints indicating impaired services
     */
    public DegradationHealthStatus getHealthStatus() {
        Map<ServiceType, DegradationState> currentStates = new HashMap<>(degradationStates);
        
        boolean isFullyOperational = currentStates.values().stream()
            .noneMatch(DegradationState::isDegraded);
        
        List<String> impairedServices = currentStates.entrySet().stream()
            .filter(e -> e.getValue().isDegraded())
            .map(e -> e.getKey().name())
            .toList();
        
        OperationalMode mode;
        if (isFullyOperational) {
            mode = OperationalMode.NORMAL;
        } else if (impairedServices.size() <= 2) {
            mode = OperationalMode.DEGRADED;
        } else {
            mode = OperationalMode.SEVERELY_DEGRADED;
        }
        
        return new DegradationHealthStatus(
            mode,
            isFullyOperational,
            impairedServices,
            currentStates,
            eventBuffer.size()
        );
    }

    /**
     * Check if a specific service is in degraded mode.
     */
    public boolean isServiceDegraded(ServiceType serviceType) {
        DegradationState state = degradationStates.get(serviceType);
        return state != null && state.isDegraded();
    }

    /**
     * Get the degradation reason for a specific service.
     */
    public String getDegradationReason(ServiceType serviceType) {
        DegradationState state = degradationStates.get(serviceType);
        return state != null ? state.getReason() : null;
    }

    private void markServiceDegraded(ServiceType serviceType, String reason) {
        degradationStates.put(serviceType, new DegradationState(serviceType, true, reason));
        logger.warn("Service marked as degraded: {} - {}", serviceType, reason);
    }

    private void clearDegradation(ServiceType serviceType) {
        degradationStates.put(serviceType, new DegradationState(serviceType, false, null));
        logger.info("Service degradation cleared: {}", serviceType);
    }

    // ==================== Inner Classes and Enums ====================

    public enum ServiceType {
        FRAUD_DETECTION,
        THREE_DS,
        CACHE,
        KAFKA,
        DATABASE,
        PSP
    }

    public enum OperationalMode {
        NORMAL,
        DEGRADED,
        SEVERELY_DEGRADED
    }

    public enum FraudStatus {
        CLEAN,
        REVIEW,
        BLOCK
    }

    public enum ThreeDSStatus {
        NOT_ENROLLED,
        AUTHENTICATED,
        CHALLENGE_REQUIRED,
        FAILED
    }

    @FunctionalInterface
    public interface CacheFallbackSupplier<T> {
        T get() throws Exception;
    }

    public static class FraudFallbackRequest {
        private final String transactionId;
        private final BigDecimal amount;
        private final String billingCountry;
        private final String merchantCountry;
        private final boolean firstTimeCard;

        public FraudFallbackRequest(String transactionId, BigDecimal amount, 
                                   String billingCountry, String merchantCountry, 
                                   boolean firstTimeCard) {
            this.transactionId = transactionId;
            this.amount = amount;
            this.billingCountry = billingCountry;
            this.merchantCountry = merchantCountry;
            this.firstTimeCard = firstTimeCard;
        }

        public String getTransactionId() { return transactionId; }
        public BigDecimal getAmount() { return amount; }
        public String getBillingCountry() { return billingCountry; }
        public String getMerchantCountry() { return merchantCountry; }
        public boolean isFirstTimeCard() { return firstTimeCard; }
    }

    public static class FraudFallbackResult {
        private final BigDecimal riskScore;
        private final FraudStatus status;
        private final List<String> triggeredRules;
        private final boolean require3DS;
        private final boolean isFallback;

        public FraudFallbackResult(BigDecimal riskScore, FraudStatus status, 
                                  List<String> triggeredRules, boolean require3DS, 
                                  boolean isFallback) {
            this.riskScore = riskScore;
            this.status = status;
            this.triggeredRules = triggeredRules;
            this.require3DS = require3DS;
            this.isFallback = isFallback;
        }

        public BigDecimal getRiskScore() { return riskScore; }
        public FraudStatus getStatus() { return status; }
        public List<String> getTriggeredRules() { return triggeredRules; }
        public boolean isRequire3DS() { return require3DS; }
        public boolean isFallback() { return isFallback; }
    }

    public static class ThreeDSFallbackResult {
        private final String transactionId;
        private final ThreeDSStatus status;
        private final String cavv;
        private final String eci;
        private final String message;
        private final boolean isFallback;

        public ThreeDSFallbackResult(String transactionId, ThreeDSStatus status, 
                                    String cavv, String eci, String message, 
                                    boolean isFallback) {
            this.transactionId = transactionId;
            this.status = status;
            this.cavv = cavv;
            this.eci = eci;
            this.message = message;
            this.isFallback = isFallback;
        }

        public String getTransactionId() { return transactionId; }
        public ThreeDSStatus getStatus() { return status; }
        public String getCavv() { return cavv; }
        public String getEci() { return eci; }
        public String getMessage() { return message; }
        public boolean isFallback() { return isFallback; }
    }

    public static class BufferedEvent {
        private final String topic;
        private final String key;
        private final Object payload;
        private final Instant bufferedAt;

        public BufferedEvent(String topic, String key, Object payload, Instant bufferedAt) {
            this.topic = topic;
            this.key = key;
            this.payload = payload;
            this.bufferedAt = bufferedAt;
        }

        public String getTopic() { return topic; }
        public String getKey() { return key; }
        public Object getPayload() { return payload; }
        public Instant getBufferedAt() { return bufferedAt; }
    }

    public static class DegradationState {
        private final ServiceType serviceType;
        private final boolean degraded;
        private final String reason;
        private final Instant since;

        public DegradationState(ServiceType serviceType, boolean degraded, String reason) {
            this.serviceType = serviceType;
            this.degraded = degraded;
            this.reason = reason;
            this.since = degraded ? Instant.now() : null;
        }

        public ServiceType getServiceType() { return serviceType; }
        public boolean isDegraded() { return degraded; }
        public String getReason() { return reason; }
        public Instant getSince() { return since; }
    }

    public static class DegradationHealthStatus {
        private final OperationalMode mode;
        private final boolean fullyOperational;
        private final List<String> impairedServices;
        private final Map<ServiceType, DegradationState> serviceStates;
        private final int bufferedEventCount;

        public DegradationHealthStatus(OperationalMode mode, boolean fullyOperational,
                                       List<String> impairedServices,
                                       Map<ServiceType, DegradationState> serviceStates,
                                       int bufferedEventCount) {
            this.mode = mode;
            this.fullyOperational = fullyOperational;
            this.impairedServices = impairedServices;
            this.serviceStates = serviceStates;
            this.bufferedEventCount = bufferedEventCount;
        }

        public OperationalMode getMode() { return mode; }
        public boolean isFullyOperational() { return fullyOperational; }
        public List<String> getImpairedServices() { return impairedServices; }
        public Map<ServiceType, DegradationState> getServiceStates() { return serviceStates; }
        public int getBufferedEventCount() { return bufferedEventCount; }
    }
}
