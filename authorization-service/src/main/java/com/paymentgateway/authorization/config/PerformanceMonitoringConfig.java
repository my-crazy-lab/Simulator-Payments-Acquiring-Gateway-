package com.paymentgateway.authorization.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Performance monitoring and async execution configuration.
 * 
 * Requirements: 27.1, 27.2, 27.3, 27.4
 * - 99.99% uptime for payment authorization
 * - <500ms p95 response time
 * - 10,000 TPS throughput
 * - Latency, throughput, and error rate monitoring
 */
@Configuration
@EnableAsync
public class PerformanceMonitoringConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringConfig.class);
    
    @Value("${performance.async.core-pool-size:20}")
    private int corePoolSize;
    
    @Value("${performance.async.max-pool-size:100}")
    private int maxPoolSize;
    
    @Value("${performance.async.queue-capacity:500}")
    private int queueCapacity;
    
    @Value("${performance.async.thread-name-prefix:payment-async-}")
    private String threadNamePrefix;
    
    /**
     * Enable @Timed annotation support for method-level timing metrics
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
    
    /**
     * Async executor for non-blocking operations like webhook delivery,
     * event publishing, and audit logging.
     */
    @Bean(name = "paymentAsyncExecutor")
    public Executor paymentAsyncExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        // Register executor metrics
        ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), 
                "payment-async-executor");
        
        logger.info("Configured async executor with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }
    
    /**
     * Dedicated executor for PSP communication to isolate external call latency
     */
    @Bean(name = "pspExecutor")
    public Executor pspExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(30);
        executor.setMaxPoolSize(150);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("psp-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), 
                "psp-executor");
        
        logger.info("Configured PSP executor with corePoolSize=30, maxPoolSize=150");
        
        return executor;
    }
    
    /**
     * Dedicated executor for Kafka event publishing
     */
    @Bean(name = "kafkaPublisherExecutor")
    public Executor kafkaPublisherExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("kafka-pub-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), 
                "kafka-publisher-executor");
        
        logger.info("Configured Kafka publisher executor");
        
        return executor;
    }
    
    /**
     * Custom timer for payment processing latency tracking
     */
    @Bean
    public Timer paymentProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("payment.processing.time")
                .description("Time taken to process a payment")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
    
    /**
     * Custom timer for PSP communication latency
     */
    @Bean
    public Timer pspLatencyTimer(MeterRegistry meterRegistry) {
        return Timer.builder("psp.communication.time")
                .description("Time taken for PSP communication")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
    
    /**
     * Custom timer for database query latency
     */
    @Bean
    public Timer databaseQueryTimer(MeterRegistry meterRegistry) {
        return Timer.builder("database.query.time")
                .description("Time taken for database queries")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}
