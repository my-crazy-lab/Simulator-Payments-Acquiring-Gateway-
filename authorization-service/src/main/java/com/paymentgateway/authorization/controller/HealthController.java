package com.paymentgateway.authorization.controller;

import com.paymentgateway.authorization.degradation.GracefulDegradationService;
import com.paymentgateway.authorization.degradation.GracefulDegradationService.DegradationHealthStatus;
import com.paymentgateway.authorization.degradation.GracefulDegradationService.DegradationState;
import com.paymentgateway.authorization.degradation.GracefulDegradationService.ServiceType;
import com.paymentgateway.authorization.health.DatabaseHealthIndicator;
import com.paymentgateway.authorization.health.KafkaHealthIndicator;
import com.paymentgateway.authorization.health.PSPHealthIndicator;
import com.paymentgateway.authorization.health.RedisHealthIndicator;
import com.paymentgateway.authorization.monitoring.AlertService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for health check endpoints.
 * Provides detailed health status for all system components.
 * 
 * Requirements: 10.3, 10.4, 10.5, 30.5 - Monitoring, observability, and degradation status
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final DatabaseHealthIndicator databaseHealthIndicator;
    private final RedisHealthIndicator redisHealthIndicator;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final PSPHealthIndicator pspHealthIndicator;
    private final AlertService alertService;
    private final GracefulDegradationService degradationService;

    public HealthController(
            DatabaseHealthIndicator databaseHealthIndicator,
            RedisHealthIndicator redisHealthIndicator,
            KafkaHealthIndicator kafkaHealthIndicator,
            PSPHealthIndicator pspHealthIndicator,
            AlertService alertService,
            GracefulDegradationService degradationService) {
        this.databaseHealthIndicator = databaseHealthIndicator;
        this.redisHealthIndicator = redisHealthIndicator;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.pspHealthIndicator = pspHealthIndicator;
        this.alertService = alertService;
        this.degradationService = degradationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("service", "authorization-service");

        Health dbHealth = databaseHealthIndicator.health();
        Health redisHealth = redisHealthIndicator.health();
        Health kafkaHealth = kafkaHealthIndicator.health();
        Health pspHealth = pspHealthIndicator.health();

        Map<String, Object> components = new HashMap<>();
        components.put("database", healthToMap(dbHealth));
        components.put("redis", healthToMap(redisHealth));
        components.put("kafka", healthToMap(kafkaHealth));
        components.put("psp", healthToMap(pspHealth));

        response.put("components", components);

        // Determine overall status
        String overallStatus = determineOverallStatus(dbHealth, redisHealth, kafkaHealth, pspHealth);
        response.put("status", overallStatus);

        // Include active alerts
        List<AlertService.Alert> activeAlerts = alertService.getActiveAlerts();
        response.put("activeAlerts", activeAlerts.size());

        if ("DOWN".equals(overallStatus)) {
            return ResponseEntity.status(503).body(response);
        } else if ("DEGRADED".equals(overallStatus)) {
            return ResponseEntity.status(200).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/liveness")
    public ResponseEntity<Map<String, Object>> getLiveness() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> getReadiness() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());

        Health dbHealth = databaseHealthIndicator.health();
        Health redisHealth = redisHealthIndicator.health();

        boolean isReady = "UP".equals(dbHealth.getStatus().getCode()) 
                && "UP".equals(redisHealth.getStatus().getCode());

        response.put("status", isReady ? "UP" : "DOWN");
        response.put("database", dbHealth.getStatus().getCode());
        response.put("redis", redisHealth.getStatus().getCode());

        if (isReady) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(503).body(response);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        
        List<AlertService.Alert> alerts = alertService.getActiveAlerts();
        response.put("totalAlerts", alerts.size());
        response.put("criticalAlerts", alertService.getAlertsBySeverity(AlertService.AlertSeverity.CRITICAL).size());
        response.put("warningAlerts", alertService.getAlertsBySeverity(AlertService.AlertSeverity.WARNING).size());
        response.put("alerts", alerts.stream().map(this::alertToMap).toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get degradation status indicating which services are impaired.
     * 
     * Requirements: 30.5 - Health endpoints indicating impaired services
     */
    @GetMapping("/degradation")
    public ResponseEntity<Map<String, Object>> getDegradationStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        
        DegradationHealthStatus status = degradationService.getHealthStatus();
        
        response.put("operationalMode", status.getMode().name());
        response.put("fullyOperational", status.isFullyOperational());
        response.put("impairedServices", status.getImpairedServices());
        response.put("bufferedEventCount", status.getBufferedEventCount());
        
        // Include detailed service states
        Map<String, Object> serviceStates = new HashMap<>();
        for (Map.Entry<ServiceType, DegradationState> entry : status.getServiceStates().entrySet()) {
            DegradationState state = entry.getValue();
            Map<String, Object> stateMap = new HashMap<>();
            stateMap.put("degraded", state.isDegraded());
            stateMap.put("reason", state.getReason());
            if (state.getSince() != null) {
                stateMap.put("since", state.getSince().toString());
            }
            serviceStates.put(entry.getKey().name(), stateMap);
        }
        response.put("serviceStates", serviceStates);
        
        // Return appropriate HTTP status based on operational mode
        return switch (status.getMode()) {
            case NORMAL -> ResponseEntity.ok(response);
            case DEGRADED -> ResponseEntity.status(200).body(response);
            case SEVERELY_DEGRADED -> ResponseEntity.status(503).body(response);
        };
    }

    private Map<String, Object> healthToMap(Health health) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", health.getStatus().getCode());
        map.putAll(health.getDetails());
        return map;
    }

    private Map<String, Object> alertToMap(AlertService.Alert alert) {
        Map<String, Object> map = new HashMap<>();
        map.put("severity", alert.getSeverity().name());
        map.put("type", alert.getType().name());
        map.put("message", alert.getMessage());
        map.put("timestamp", alert.getTimestamp().toString());
        return map;
    }

    private String determineOverallStatus(Health... healths) {
        boolean hasDown = false;
        boolean hasDegraded = false;

        for (Health health : healths) {
            String status = health.getStatus().getCode();
            if ("DOWN".equals(status)) {
                hasDown = true;
            } else if ("DEGRADED".equals(status)) {
                hasDegraded = true;
            }
        }

        if (hasDown) {
            return "DOWN";
        } else if (hasDegraded) {
            return "DEGRADED";
        }
        return "UP";
    }
}
