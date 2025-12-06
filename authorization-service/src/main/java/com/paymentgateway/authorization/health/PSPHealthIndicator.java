package com.paymentgateway.authorization.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Health indicator for PSP (Payment Service Provider) connectivity.
 * Tracks the health status of configured PSPs based on circuit breaker state.
 * 
 * Requirements: 10.3, 10.4 - Health monitoring and alerting
 */
@Component
public class PSPHealthIndicator implements HealthIndicator {

    private final Map<String, PSPStatus> pspStatuses = new ConcurrentHashMap<>();

    public PSPHealthIndicator() {
        // Initialize with default PSPs
        pspStatuses.put("stripe", new PSPStatus("stripe", true, 0));
        pspStatuses.put("adyen", new PSPStatus("adyen", true, 0));
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean allHealthy = true;
        int healthyCount = 0;
        int totalCount = pspStatuses.size();

        for (Map.Entry<String, PSPStatus> entry : pspStatuses.entrySet()) {
            PSPStatus status = entry.getValue();
            details.put(entry.getKey(), Map.of(
                    "healthy", status.isHealthy(),
                    "failureCount", status.getFailureCount(),
                    "circuitBreakerOpen", !status.isHealthy()
            ));
            if (status.isHealthy()) {
                healthyCount++;
            } else {
                allHealthy = false;
            }
        }

        details.put("healthyPSPs", healthyCount + "/" + totalCount);

        if (allHealthy) {
            return Health.up()
                    .withDetails(details)
                    .build();
        } else if (healthyCount > 0) {
            return Health.status("DEGRADED")
                    .withDetails(details)
                    .build();
        } else {
            return Health.down()
                    .withDetails(details)
                    .build();
        }
    }

    public void updatePSPStatus(String pspName, boolean healthy, int failureCount) {
        pspStatuses.put(pspName.toLowerCase(), new PSPStatus(pspName, healthy, failureCount));
    }

    public void markPSPHealthy(String pspName) {
        PSPStatus current = pspStatuses.get(pspName.toLowerCase());
        if (current != null) {
            pspStatuses.put(pspName.toLowerCase(), new PSPStatus(pspName, true, 0));
        }
    }

    public void markPSPUnhealthy(String pspName, int failureCount) {
        pspStatuses.put(pspName.toLowerCase(), new PSPStatus(pspName, false, failureCount));
    }

    public boolean isPSPHealthy(String pspName) {
        PSPStatus status = pspStatuses.get(pspName.toLowerCase());
        return status != null && status.isHealthy();
    }

    public Map<String, PSPStatus> getPspStatuses() {
        return new HashMap<>(pspStatuses);
    }

    public static class PSPStatus {
        private final String name;
        private final boolean healthy;
        private final int failureCount;

        public PSPStatus(String name, boolean healthy, int failureCount) {
            this.name = name;
            this.healthy = healthy;
            this.failureCount = failureCount;
        }

        public String getName() {
            return name;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public int getFailureCount() {
            return failureCount;
        }
    }
}
