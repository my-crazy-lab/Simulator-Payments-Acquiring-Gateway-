package com.paymentgateway.authorization.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service for generating and managing alerts based on system metrics.
 * Supports configurable thresholds and alert severity levels.
 * 
 * Requirements: 10.4 - Real-time alerts on anomaly detection
 */
@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final ConcurrentLinkedQueue<Alert> activeAlerts = new ConcurrentLinkedQueue<>();
    private final List<AlertListener> listeners = new ArrayList<>();

    // Configurable thresholds
    private double errorRateThreshold = 0.05; // 5%
    private double latencyThresholdMs = 500.0; // 500ms
    private int circuitBreakerThreshold = 1;
    private double fraudScoreThreshold = 0.75;

    public void checkErrorRate(double errorRate) {
        if (errorRate > errorRateThreshold) {
            triggerAlert(new Alert(
                    AlertSeverity.CRITICAL,
                    AlertType.HIGH_ERROR_RATE,
                    String.format("Error rate %.2f%% exceeds threshold %.2f%%", 
                            errorRate * 100, errorRateThreshold * 100),
                    Instant.now()
            ));
        }
    }

    public void checkLatency(double latencyMs) {
        if (latencyMs > latencyThresholdMs) {
            triggerAlert(new Alert(
                    AlertSeverity.WARNING,
                    AlertType.HIGH_LATENCY,
                    String.format("Latency %.2fms exceeds threshold %.2fms", 
                            latencyMs, latencyThresholdMs),
                    Instant.now()
            ));
        }
    }

    public void checkCircuitBreakers(int openCount) {
        if (openCount >= circuitBreakerThreshold) {
            triggerAlert(new Alert(
                    AlertSeverity.CRITICAL,
                    AlertType.CIRCUIT_BREAKER_OPEN,
                    String.format("%d circuit breaker(s) are open", openCount),
                    Instant.now()
            ));
        }
    }

    public void checkFraudScore(String transactionId, double fraudScore) {
        if (fraudScore >= fraudScoreThreshold) {
            triggerAlert(new Alert(
                    AlertSeverity.WARNING,
                    AlertType.HIGH_FRAUD_SCORE,
                    String.format("Transaction %s has high fraud score: %.2f", 
                            transactionId, fraudScore),
                    Instant.now()
            ));
        }
    }

    public void triggerAlert(Alert alert) {
        activeAlerts.add(alert);
        logger.warn("ALERT [{}] {}: {}", alert.getSeverity(), alert.getType(), alert.getMessage());
        
        // Notify listeners
        for (AlertListener listener : listeners) {
            try {
                listener.onAlert(alert);
            } catch (Exception e) {
                logger.error("Error notifying alert listener", e);
            }
        }
    }

    public void resolveAlert(AlertType type) {
        activeAlerts.removeIf(alert -> alert.getType() == type);
        logger.info("Alert resolved: {}", type);
    }

    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts);
    }

    public List<Alert> getAlertsBySeverity(AlertSeverity severity) {
        return activeAlerts.stream()
                .filter(alert -> alert.getSeverity() == severity)
                .toList();
    }

    public void addListener(AlertListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AlertListener listener) {
        listeners.remove(listener);
    }

    // Configuration setters
    public void setErrorRateThreshold(double threshold) {
        this.errorRateThreshold = threshold;
    }

    public void setLatencyThresholdMs(double threshold) {
        this.latencyThresholdMs = threshold;
    }

    public void setCircuitBreakerThreshold(int threshold) {
        this.circuitBreakerThreshold = threshold;
    }

    public void setFraudScoreThreshold(double threshold) {
        this.fraudScoreThreshold = threshold;
    }

    public double getErrorRateThreshold() {
        return errorRateThreshold;
    }

    public double getLatencyThresholdMs() {
        return latencyThresholdMs;
    }

    public int getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public double getFraudScoreThreshold() {
        return fraudScoreThreshold;
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }

    public enum AlertType {
        HIGH_ERROR_RATE,
        HIGH_LATENCY,
        CIRCUIT_BREAKER_OPEN,
        HIGH_FRAUD_SCORE,
        DATABASE_UNAVAILABLE,
        REDIS_UNAVAILABLE,
        KAFKA_UNAVAILABLE,
        PSP_UNAVAILABLE
    }

    public interface AlertListener {
        void onAlert(Alert alert);
    }

    public static class Alert {
        private final AlertSeverity severity;
        private final AlertType type;
        private final String message;
        private final Instant timestamp;

        public Alert(AlertSeverity severity, AlertType type, String message, Instant timestamp) {
            this.severity = severity;
            this.type = type;
            this.message = message;
            this.timestamp = timestamp;
        }

        public AlertSeverity getSeverity() {
            return severity;
        }

        public AlertType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }
}
