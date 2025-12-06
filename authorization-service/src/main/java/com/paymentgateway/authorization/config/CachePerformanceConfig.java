package com.paymentgateway.authorization.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache performance configuration for high-throughput payment processing.
 * 
 * Requirements: 15.4, 20.4, 23.4, 27.2
 * - Redis caching for frequently accessed data
 * - Configurable TTL per cache type
 * - Cache eviction strategies
 */
@Configuration
@EnableCaching
public class CachePerformanceConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(CachePerformanceConfig.class);
    
    // Cache names
    public static final String PAYMENT_CACHE = "payments";
    public static final String TRANSACTION_QUERY_CACHE = "transactionQueries";
    public static final String MERCHANT_CACHE = "merchants";
    public static final String EXCHANGE_RATE_CACHE = "exchangeRates";
    public static final String PSP_CONFIG_CACHE = "pspConfigs";
    public static final String FRAUD_RULES_CACHE = "fraudRules";
    public static final String IDEMPOTENCY_CACHE = "idempotency";
    
    @Value("${cache.payment.ttl-seconds:300}")
    private long paymentCacheTtl;
    
    @Value("${cache.transaction-query.ttl-seconds:60}")
    private long transactionQueryCacheTtl;
    
    @Value("${cache.merchant.ttl-seconds:3600}")
    private long merchantCacheTtl;
    
    @Value("${cache.exchange-rate.ttl-seconds:300}")
    private long exchangeRateCacheTtl;
    
    @Value("${cache.psp-config.ttl-seconds:1800}")
    private long pspConfigCacheTtl;
    
    @Value("${cache.fraud-rules.ttl-seconds:600}")
    private long fraudRulesCacheTtl;
    
    @Value("${cache.idempotency.ttl-seconds:86400}")
    private long idempotencyCacheTtl;
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
        
        // Cache-specific configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Payment cache - moderate TTL for balance between freshness and performance
        cacheConfigurations.put(PAYMENT_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(paymentCacheTtl)));
        
        // Transaction query cache - short TTL for near real-time data
        cacheConfigurations.put(TRANSACTION_QUERY_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(transactionQueryCacheTtl)));
        
        // Merchant cache - longer TTL as merchant data changes infrequently
        cacheConfigurations.put(MERCHANT_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(merchantCacheTtl)));
        
        // Exchange rate cache - moderate TTL with refresh strategy
        cacheConfigurations.put(EXCHANGE_RATE_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(exchangeRateCacheTtl)));
        
        // PSP config cache - longer TTL as configs change infrequently
        cacheConfigurations.put(PSP_CONFIG_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(pspConfigCacheTtl)));
        
        // Fraud rules cache - moderate TTL
        cacheConfigurations.put(FRAUD_RULES_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(fraudRulesCacheTtl)));
        
        // Idempotency cache - 24 hour TTL per requirements
        cacheConfigurations.put(IDEMPOTENCY_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(idempotencyCacheTtl)));
        
        logger.info("Configured Redis cache manager with {} cache configurations", 
                cacheConfigurations.size());
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
