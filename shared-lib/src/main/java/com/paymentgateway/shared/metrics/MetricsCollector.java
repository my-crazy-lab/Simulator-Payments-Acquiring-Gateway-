package com.paymentgateway.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.TimeUnit;

/**
 * Metrics collector for Prometheus
 */
public class MetricsCollector {
    private final MeterRegistry registry;

    public MetricsCollector() {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;
    }

    public void incrementCounter(String name, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(registry)
                .increment();
    }

    public void recordTimer(String name, long duration, TimeUnit unit, String... tags) {
        Timer.builder(name)
                .tags(tags)
                .register(registry)
                .record(duration, unit);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample, String name, String... tags) {
        sample.stop(Timer.builder(name)
                .tags(tags)
                .register(registry));
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public String scrape() {
        if (registry instanceof PrometheusMeterRegistry) {
            return ((PrometheusMeterRegistry) registry).scrape();
        }
        return "";
    }
}
