package com.paymentgateway.authorization.monitoring;

import com.paymentgateway.authorization.config.MetricsConfig;
import com.paymentgateway.authorization.config.MetricsConfig.PaymentMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for metrics collection functionality.
 * Tests metric exporters, counters, timers, and gauges.
 * 
 * Requirements: 10.3 - Prometheus-compatible metrics endpoints
 */
class MetricsConfigTest {

    private MeterRegistry registry;
    private PaymentMetrics paymentMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        paymentMetrics = new PaymentMetrics(registry);
    }

    @Test
    @DisplayName("Should increment payment success counter")
    void shouldIncrementPaymentSuccessCounter() {
        // When
        paymentMetrics.incrementPaymentSuccess();
        paymentMetrics.incrementPaymentSuccess();
        paymentMetrics.incrementPaymentSuccess();

        // Then
        assertThat(paymentMetrics.getPaymentSuccessCounter().count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should increment payment failure counter")
    void shouldIncrementPaymentFailureCounter() {
        // When
        paymentMetrics.incrementPaymentFailure();
        paymentMetrics.incrementPaymentFailure();

        // Then
        assertThat(paymentMetrics.getPaymentFailureCounter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should increment payment failure counter with reason")
    void shouldIncrementPaymentFailureCounterWithReason() {
        // When
        paymentMetrics.incrementPaymentFailure("insufficient_funds");
        paymentMetrics.incrementPaymentFailure("card_declined");
        paymentMetrics.incrementPaymentFailure("insufficient_funds");

        // Then - verify counters are created with tags
        var counter = registry.find("payment.failure.total")
                .tag("reason", "insufficient_funds")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should increment authorization counter")
    void shouldIncrementAuthorizationCounter() {
        // When
        paymentMetrics.incrementAuthorization();

        // Then
        assertThat(paymentMetrics.getAuthorizationCounter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should track active transactions gauge")
    void shouldTrackActiveTransactionsGauge() {
        // When
        paymentMetrics.incrementActiveTransactions();
        paymentMetrics.incrementActiveTransactions();
        paymentMetrics.incrementActiveTransactions();
        paymentMetrics.decrementActiveTransactions();

        // Then
        assertThat(paymentMetrics.getActiveTransactions().get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should track circuit breaker open count")
    void shouldTrackCircuitBreakerOpenCount() {
        // When
        paymentMetrics.setCircuitBreakerOpenCount(3);

        // Then
        assertThat(paymentMetrics.getCircuitBreakerOpenCount().get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should record payment processing timer")
    void shouldRecordPaymentProcessingTimer() throws InterruptedException {
        // When
        Timer.Sample sample = paymentMetrics.startPaymentTimer();
        Thread.sleep(10); // Simulate some processing time
        paymentMetrics.stopPaymentTimer(sample);

        // Then
        assertThat(paymentMetrics.getPaymentProcessingTimer().count()).isEqualTo(1);
        assertThat(paymentMetrics.getPaymentProcessingTimer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Should record PSP response timer with PSP name tag")
    void shouldRecordPspResponseTimerWithTag() throws InterruptedException {
        // When
        Timer.Sample sample = paymentMetrics.startPspTimer();
        Thread.sleep(10);
        paymentMetrics.stopPspTimer(sample, "stripe");

        // Then
        var timer = registry.find("psp.response.duration")
                .tag("psp", "stripe")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should record fraud score distribution")
    void shouldRecordFraudScoreDistribution() {
        // When
        paymentMetrics.recordFraudScore(0.25);
        paymentMetrics.recordFraudScore(0.75);
        paymentMetrics.recordFraudScore(0.50);

        // Then
        var summary = registry.find("fraud.score.distribution").summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should record transaction amount distribution by currency")
    void shouldRecordTransactionAmountByCurrency() {
        // When
        paymentMetrics.recordTransactionAmount(100.00, "USD");
        paymentMetrics.recordTransactionAmount(85.00, "EUR");
        paymentMetrics.recordTransactionAmount(200.00, "USD");

        // Then
        var usdSummary = registry.find("payment.amount.distribution")
                .tag("currency", "USD")
                .summary();
        assertThat(usdSummary).isNotNull();
        assertThat(usdSummary.count()).isEqualTo(2);

        var eurSummary = registry.find("payment.amount.distribution")
                .tag("currency", "EUR")
                .summary();
        assertThat(eurSummary).isNotNull();
        assertThat(eurSummary.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should create metrics with correct tags")
    void shouldCreateMetricsWithCorrectTags() {
        // When
        paymentMetrics.incrementPaymentSuccess();

        // Then
        var counter = registry.find("payment.success.total")
                .tag("service", "authorization")
                .counter();
        assertThat(counter).isNotNull();
    }

    @Test
    @DisplayName("Should increment capture counter")
    void shouldIncrementCaptureCounter() {
        // When
        paymentMetrics.incrementCapture();
        paymentMetrics.incrementCapture();

        // Then
        var counter = registry.find("payment.capture.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should increment refund counter")
    void shouldIncrementRefundCounter() {
        // When
        paymentMetrics.incrementRefund();

        // Then
        var counter = registry.find("payment.refund.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
