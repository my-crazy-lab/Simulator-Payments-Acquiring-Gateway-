package com.paymentgateway.authorization.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis connectivity.
 * Checks if Redis cache is accessible and responsive.
 * 
 * Requirements: 10.3, 10.4 - Health monitoring and alerting
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try {
            String pong = connectionFactory.getConnection().ping();
            if ("PONG".equals(pong)) {
                return Health.up()
                        .withDetail("cache", "Redis")
                        .withDetail("status", "Connected")
                        .withDetail("ping", pong)
                        .build();
            } else {
                return Health.down()
                        .withDetail("cache", "Redis")
                        .withDetail("status", "Unexpected response")
                        .withDetail("ping", pong)
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("cache", "Redis")
                    .withDetail("status", "Connection failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
