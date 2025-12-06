package com.paymentgateway.authorization.monitoring;

import com.paymentgateway.authorization.monitoring.AlertService.Alert;
import com.paymentgateway.authorization.monitoring.AlertService.AlertListener;
import com.paymentgateway.authorization.monitoring.AlertService.AlertSeverity;
import com.paymentgateway.authorization.monitoring.AlertService.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for alert service functionality.
 * Tests alert triggers, thresholds, and listener notifications.
 * 
 * Requirements: 10.4 - Real-time alerts on anomaly detection
 */
class AlertServiceTest {

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService();
    }

    @Test
    @DisplayName("Should trigger alert when error rate exceeds threshold")
    void shouldTriggerAlertWhenErrorRateExceedsThreshold() {
        // Given
        alertService.setErrorRateThreshold(0.05); // 5%

        // When
        alertService.checkErrorRate(0.10); // 10% error rate

        // Then
        List<Alert> alerts = alertService.getActiveAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo(AlertType.HIGH_ERROR_RATE);
        assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should not trigger alert when error rate is below threshold")
    void shouldNotTriggerAlertWhenErrorRateBelowThreshold() {
        // Given
        alertService.setErrorRateThreshold(0.05);

        // When
        alertService.checkErrorRate(0.03); // 3% error rate

        // Then
        assertThat(alertService.getActiveAlerts()).isEmpty();
    }

    @Test
    @DisplayName("Should trigger alert when latency exceeds threshold")
    void shouldTriggerAlertWhenLatencyExceedsThreshold() {
        // Given
        alertService.setLatencyThresholdMs(500.0);

        // When
        alertService.checkLatency(750.0); // 750ms latency

        // Then
        List<Alert> alerts = alertService.getActiveAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo(AlertType.HIGH_LATENCY);
        assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    @DisplayName("Should not trigger alert when latency is below threshold")
    void shouldNotTriggerAlertWhenLatencyBelowThreshold() {
        // Given
        alertService.setLatencyThresholdMs(500.0);

        // When
        alertService.checkLatency(300.0);

        // Then
        assertThat(alertService.getActiveAlerts()).isEmpty();
    }

    @Test
    @DisplayName("Should trigger alert when circuit breakers are open")
    void shouldTriggerAlertWhenCircuitBreakersOpen() {
        // Given
        alertService.setCircuitBreakerThreshold(1);

        // When
        alertService.checkCircuitBreakers(2);

        // Then
        List<Alert> alerts = alertService.getActiveAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo(AlertType.CIRCUIT_BREAKER_OPEN);
        assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should trigger alert for high fraud score")
    void shouldTriggerAlertForHighFraudScore() {
        // Given
        alertService.setFraudScoreThreshold(0.75);

        // When
        alertService.checkFraudScore("txn_123", 0.85);

        // Then
        List<Alert> alerts = alertService.getActiveAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo(AlertType.HIGH_FRAUD_SCORE);
        assertThat(alerts.get(0).getMessage()).contains("txn_123");
        assertThat(alerts.get(0).getMessage()).contains("0.85");
    }

    @Test
    @DisplayName("Should not trigger alert for low fraud score")
    void shouldNotTriggerAlertForLowFraudScore() {
        // Given
        alertService.setFraudScoreThreshold(0.75);

        // When
        alertService.checkFraudScore("txn_123", 0.50);

        // Then
        assertThat(alertService.getActiveAlerts()).isEmpty();
    }

    @Test
    @DisplayName("Should resolve alerts by type")
    void shouldResolveAlertsByType() {
        // Given
        alertService.checkErrorRate(0.10);
        alertService.checkLatency(750.0);
        assertThat(alertService.getActiveAlerts()).hasSize(2);

        // When
        alertService.resolveAlert(AlertType.HIGH_ERROR_RATE);

        // Then
        List<Alert> alerts = alertService.getActiveAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo(AlertType.HIGH_LATENCY);
    }

    @Test
    @DisplayName("Should filter alerts by severity")
    void shouldFilterAlertsBySeverity() {
        // Given
        alertService.checkErrorRate(0.10); // CRITICAL
        alertService.checkLatency(750.0);  // WARNING
        alertService.checkCircuitBreakers(2); // CRITICAL

        // When
        List<Alert> criticalAlerts = alertService.getAlertsBySeverity(AlertSeverity.CRITICAL);
        List<Alert> warningAlerts = alertService.getAlertsBySeverity(AlertSeverity.WARNING);

        // Then
        assertThat(criticalAlerts).hasSize(2);
        assertThat(warningAlerts).hasSize(1);
    }

    @Test
    @DisplayName("Should notify listeners when alert is triggered")
    void shouldNotifyListenersWhenAlertTriggered() {
        // Given
        List<Alert> receivedAlerts = new ArrayList<>();
        AlertListener listener = receivedAlerts::add;
        alertService.addListener(listener);

        // When
        alertService.checkErrorRate(0.10);

        // Then
        assertThat(receivedAlerts).hasSize(1);
        assertThat(receivedAlerts.get(0).getType()).isEqualTo(AlertType.HIGH_ERROR_RATE);
    }

    @Test
    @DisplayName("Should support multiple listeners")
    void shouldSupportMultipleListeners() {
        // Given
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);
        
        alertService.addListener(alert -> listener1Count.incrementAndGet());
        alertService.addListener(alert -> listener2Count.incrementAndGet());

        // When
        alertService.checkErrorRate(0.10);

        // Then
        assertThat(listener1Count.get()).isEqualTo(1);
        assertThat(listener2Count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should remove listener correctly")
    void shouldRemoveListenerCorrectly() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        AlertListener listener = alert -> callCount.incrementAndGet();
        alertService.addListener(listener);

        // When
        alertService.checkErrorRate(0.10);
        alertService.removeListener(listener);
        alertService.checkLatency(750.0);

        // Then
        assertThat(callCount.get()).isEqualTo(1); // Only first alert
    }

    @Test
    @DisplayName("Should handle listener exceptions gracefully")
    void shouldHandleListenerExceptionsGracefully() {
        // Given
        AtomicInteger successCount = new AtomicInteger(0);
        alertService.addListener(alert -> {
            throw new RuntimeException("Listener error");
        });
        alertService.addListener(alert -> successCount.incrementAndGet());

        // When
        alertService.checkErrorRate(0.10);

        // Then - second listener should still be called
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Alert should contain timestamp")
    void alertShouldContainTimestamp() {
        // When
        alertService.checkErrorRate(0.10);

        // Then
        Alert alert = alertService.getActiveAlerts().get(0);
        assertThat(alert.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should get and set thresholds correctly")
    void shouldGetAndSetThresholdsCorrectly() {
        // When
        alertService.setErrorRateThreshold(0.10);
        alertService.setLatencyThresholdMs(1000.0);
        alertService.setCircuitBreakerThreshold(3);
        alertService.setFraudScoreThreshold(0.80);

        // Then
        assertThat(alertService.getErrorRateThreshold()).isEqualTo(0.10);
        assertThat(alertService.getLatencyThresholdMs()).isEqualTo(1000.0);
        assertThat(alertService.getCircuitBreakerThreshold()).isEqualTo(3);
        assertThat(alertService.getFraudScoreThreshold()).isEqualTo(0.80);
    }

    @Test
    @DisplayName("Should trigger custom alert directly")
    void shouldTriggerCustomAlertDirectly() {
        // Given
        Alert customAlert = new Alert(
                AlertSeverity.INFO,
                AlertType.DATABASE_UNAVAILABLE,
                "Custom alert message",
                java.time.Instant.now()
        );

        // When
        alertService.triggerAlert(customAlert);

        // Then
        List<Alert> alerts = alertService.getActiveAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMessage()).isEqualTo("Custom alert message");
    }
}
