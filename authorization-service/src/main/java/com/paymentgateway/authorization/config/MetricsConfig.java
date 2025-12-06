package com.paymentgateway.authorization.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration for Prometheus metrics collection.
 * Exposes payment gateway specific metrics for monitoring and alerting.
 * 
 * Requirements: 10.3 - Prometheus-compatible metrics endpoints
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public PaymentMetrics paymentMetrics(MeterRegistry registry) {
        return new PaymentMetrics(registry);
    }

    /**
     * Payment-specific metrics for monitoring payment processing.
     */
    public static class PaymentMetrics {
        private final MeterRegistry registry;
        private final Counter paymentSuccessCounter;
        private final Counter paymentFailureCounter;
        private final Counter authorizationCounter;
        private final Counter captureCounter;
        private final Counter refundCounter;
        private final Timer paymentProcessingTimer;
        private final Timer pspResponseTimer;
        private final AtomicInteger activeTransactions;
        private final AtomicInteger circuitBreakerOpenCount;

        public PaymentMetrics(MeterRegistry registry) {
            this.registry = registry;

            // Payment counters
            this.paymentSuccessCounter = Counter.builder("payment.success.total")
                    .description("Total number of successful payments")
                    .tag("service", "authorization")
                    .register(registry);

            this.paymentFailureCounter = Counter.builder("payment.failure.total")
                    .description("Total number of failed payments")
                    .tag("service", "authorization")
                    .register(registry);

            this.authorizationCounter = Counter.builder("payment.authorization.total")
                    .description("Total number of authorization requests")
                    .tag("service", "authorization")
                    .register(registry);

            this.captureCounter = Counter.builder("payment.capture.total")
                    .description("Total number of capture requests")
                    .tag("service", "authorization")
                    .register(registry);

            this.refundCounter = Counter.builder("payment.refund.total")
                    .description("Total number of refund requests")
                    .tag("service", "authorization")
                    .register(registry);

            // Timers
            this.paymentProcessingTimer = Timer.builder("payment.processing.duration")
                    .description("Time taken to process a payment")
                    .tag("service", "authorization")
                    .register(registry);

            this.pspResponseTimer = Timer.builder("psp.response.duration")
                    .description("Time taken for PSP to respond")
                    .tag("service", "authorization")
                    .register(registry);

            // Gauges
            this.activeTransactions = new AtomicInteger(0);
            Gauge.builder("payment.active.transactions", activeTransactions, AtomicInteger::get)
                    .description("Number of currently active transactions")
                    .tag("service", "authorization")
                    .register(registry);

            this.circuitBreakerOpenCount = new AtomicInteger(0);
            Gauge.builder("circuit.breaker.open.count", circuitBreakerOpenCount, AtomicInteger::get)
                    .description("Number of open circuit breakers")
                    .tag("service", "authorization")
                    .register(registry);
        }

        public void incrementPaymentSuccess() {
            paymentSuccessCounter.increment();
        }

        public void incrementPaymentFailure() {
            paymentFailureCounter.increment();
        }

        public void incrementPaymentFailure(String reason) {
            Counter.builder("payment.failure.total")
                    .description("Total number of failed payments")
                    .tag("service", "authorization")
                    .tag("reason", reason)
                    .register(registry)
                    .increment();
        }

        public void incrementAuthorization() {
            authorizationCounter.increment();
        }

        public void incrementCapture() {
            captureCounter.increment();
        }

        public void incrementRefund() {
            refundCounter.increment();
        }

        public Timer.Sample startPaymentTimer() {
            return Timer.start(registry);
        }

        public void stopPaymentTimer(Timer.Sample sample) {
            sample.stop(paymentProcessingTimer);
        }

        public Timer.Sample startPspTimer() {
            return Timer.start(registry);
        }

        public void stopPspTimer(Timer.Sample sample, String pspName) {
            sample.stop(Timer.builder("psp.response.duration")
                    .description("Time taken for PSP to respond")
                    .tag("service", "authorization")
                    .tag("psp", pspName)
                    .register(registry));
        }

        public void incrementActiveTransactions() {
            activeTransactions.incrementAndGet();
        }

        public void decrementActiveTransactions() {
            activeTransactions.decrementAndGet();
        }

        public void setCircuitBreakerOpenCount(int count) {
            circuitBreakerOpenCount.set(count);
        }

        public void recordFraudScore(double score) {
            registry.summary("fraud.score.distribution")
                    .record(score);
        }

        public void recordTransactionAmount(double amount, String currency) {
            registry.summary("payment.amount.distribution", "currency", currency)
                    .record(amount);
        }

        public Counter getPaymentSuccessCounter() {
            return paymentSuccessCounter;
        }

        public Counter getPaymentFailureCounter() {
            return paymentFailureCounter;
        }

        public Counter getAuthorizationCounter() {
            return authorizationCounter;
        }

        public Timer getPaymentProcessingTimer() {
            return paymentProcessingTimer;
        }

        public AtomicInteger getActiveTransactions() {
            return activeTransactions;
        }

        public AtomicInteger getCircuitBreakerOpenCount() {
            return circuitBreakerOpenCount;
        }
    }
}
