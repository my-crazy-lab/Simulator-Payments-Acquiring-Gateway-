package com.paymentgateway.authorization.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Database performance configuration optimized for high-throughput payment processing.
 * 
 * Requirements: 20.1, 20.2, 20.3, 20.4, 27.1, 27.2, 27.3
 * - Connection pooling with configurable min/max connections
 * - Prepared statements for SQL injection prevention and performance
 * - Batch inserts for event logs
 * - Optimized indexes for payment workflows
 */
@Configuration
public class DatabasePerformanceConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabasePerformanceConfig.class);
    
    @Value("${spring.datasource.hikari.maximum-pool-size:50}")
    private int maxPoolSize;
    
    @Value("${spring.datasource.hikari.minimum-idle:10}")
    private int minIdle;
    
    @Value("${spring.datasource.hikari.connection-timeout:10000}")
    private long connectionTimeout;
    
    @Value("${spring.datasource.hikari.idle-timeout:300000}")
    private long idleTimeout;
    
    @Value("${spring.datasource.hikari.max-lifetime:600000}")
    private long maxLifetime;
    
    @Value("${spring.datasource.hikari.leak-detection-threshold:60000}")
    private long leakDetectionThreshold;
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties, MeterRegistry meterRegistry) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        
        // Connection pool sizing for 10K TPS target
        // Rule of thumb: connections = (core_count * 2) + effective_spindle_count
        // For high-throughput: start with 50 connections, tune based on load testing
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setMinimumIdle(minIdle);
        
        // Connection timeouts
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setMaxLifetime(maxLifetime);
        
        // Leak detection for debugging
        dataSource.setLeakDetectionThreshold(leakDetectionThreshold);
        
        // Performance optimizations
        dataSource.setAutoCommit(false); // Explicit transaction management
        dataSource.setPoolName("PaymentGatewayPool");
        
        // PostgreSQL-specific optimizations
        dataSource.addDataSourceProperty("cachePrepStmts", "true");
        dataSource.addDataSourceProperty("prepStmtCacheSize", "250");
        dataSource.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource.addDataSourceProperty("useServerPrepStmts", "true");
        dataSource.addDataSourceProperty("reWriteBatchedInserts", "true");
        
        // Connection validation
        dataSource.setConnectionTestQuery("SELECT 1");
        dataSource.setValidationTimeout(5000);
        
        // Register metrics
        dataSource.setMetricRegistry(meterRegistry);
        
        logger.info("Configured HikariCP with maxPoolSize={}, minIdle={}, connectionTimeout={}ms",
                maxPoolSize, minIdle, connectionTimeout);
        
        return dataSource;
    }
}
