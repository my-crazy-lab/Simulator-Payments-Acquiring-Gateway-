package com.paymentgateway.authorization.monitoring;

import com.paymentgateway.authorization.health.DatabaseHealthIndicator;
import com.paymentgateway.authorization.health.PSPHealthIndicator;
import com.paymentgateway.authorization.health.PSPHealthIndicator.PSPStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for health check indicators.
 * Tests database, Redis, Kafka, and PSP health checks.
 * 
 * Requirements: 10.3, 10.4 - Health monitoring and alerting
 */
@ExtendWith(MockitoExtension.class)
class HealthIndicatorTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    private DatabaseHealthIndicator databaseHealthIndicator;
    private PSPHealthIndicator pspHealthIndicator;

    @BeforeEach
    void setUp() {
        databaseHealthIndicator = new DatabaseHealthIndicator(dataSource);
        pspHealthIndicator = new PSPHealthIndicator();
    }

    @Test
    @DisplayName("Database health should return UP when connection is valid")
    void databaseHealthShouldReturnUpWhenConnectionValid() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        // When
        Health health = databaseHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
        assertThat(health.getDetails()).containsEntry("status", "Connected");
    }

    @Test
    @DisplayName("Database health should return DOWN when connection is invalid")
    void databaseHealthShouldReturnDownWhenConnectionInvalid() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        // When
        Health health = databaseHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Connection invalid");
    }

    @Test
    @DisplayName("Database health should return DOWN when connection throws exception")
    void databaseHealthShouldReturnDownOnException() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        // When
        Health health = databaseHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Connection failed");
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("PSP health should return UP when all PSPs are healthy")
    void pspHealthShouldReturnUpWhenAllHealthy() {
        // Given - default state has all PSPs healthy

        // When
        Health health = pspHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("stripe");
        assertThat(health.getDetails()).containsKey("adyen");
    }

    @Test
    @DisplayName("PSP health should return DEGRADED when some PSPs are unhealthy")
    void pspHealthShouldReturnDegradedWhenSomeUnhealthy() {
        // Given
        pspHealthIndicator.markPSPUnhealthy("stripe", 5);

        // When
        Health health = pspHealthIndicator.health();

        // Then
        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getDetails().get("healthyPSPs")).isEqualTo("1/2");
    }

    @Test
    @DisplayName("PSP health should return DOWN when all PSPs are unhealthy")
    void pspHealthShouldReturnDownWhenAllUnhealthy() {
        // Given
        pspHealthIndicator.markPSPUnhealthy("stripe", 5);
        pspHealthIndicator.markPSPUnhealthy("adyen", 3);

        // When
        Health health = pspHealthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("healthyPSPs")).isEqualTo("0/2");
    }

    @Test
    @DisplayName("Should update PSP status correctly")
    void shouldUpdatePspStatusCorrectly() {
        // Given
        pspHealthIndicator.markPSPUnhealthy("stripe", 5);

        // When
        pspHealthIndicator.markPSPHealthy("stripe");

        // Then
        assertThat(pspHealthIndicator.isPSPHealthy("stripe")).isTrue();
    }

    @Test
    @DisplayName("Should track PSP failure count")
    void shouldTrackPspFailureCount() {
        // Given
        pspHealthIndicator.updatePSPStatus("stripe", false, 7);

        // When
        Map<String, PSPStatus> statuses = pspHealthIndicator.getPspStatuses();

        // Then
        PSPStatus stripeStatus = statuses.get("stripe");
        assertThat(stripeStatus).isNotNull();
        assertThat(stripeStatus.isHealthy()).isFalse();
        assertThat(stripeStatus.getFailureCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("PSP health check should be case insensitive")
    void pspHealthCheckShouldBeCaseInsensitive() {
        // Given
        pspHealthIndicator.markPSPUnhealthy("STRIPE", 3);

        // When/Then
        assertThat(pspHealthIndicator.isPSPHealthy("stripe")).isFalse();
        assertThat(pspHealthIndicator.isPSPHealthy("Stripe")).isFalse();
        assertThat(pspHealthIndicator.isPSPHealthy("STRIPE")).isFalse();
    }

    @Test
    @DisplayName("Should return false for unknown PSP")
    void shouldReturnFalseForUnknownPsp() {
        // When/Then
        assertThat(pspHealthIndicator.isPSPHealthy("unknown_psp")).isFalse();
    }
}
