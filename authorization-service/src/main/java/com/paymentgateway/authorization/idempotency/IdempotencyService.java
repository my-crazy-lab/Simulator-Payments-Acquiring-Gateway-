package com.paymentgateway.authorization.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing idempotency keys to prevent duplicate payment processing.
 * Implements distributed locking and atomic result storage with 24-hour expiration.
 */
@Service
public class IdempotencyService {
    
    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final int MAX_LOCK_ATTEMPTS = 10;
    private static final long LOCK_RETRY_DELAY_MS = 100;
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public IdempotencyService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Check if an idempotency key already exists and return the cached result if available.
     * 
     * @param idempotencyKey The unique idempotency key
     * @param resultClass The class type of the expected result
     * @return The cached result if found, null otherwise
     */
    public <T> T getExistingResult(String idempotencyKey, Class<T> resultClass) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        String cachedJson = redisTemplate.opsForValue().get(key);
        
        if (cachedJson != null) {
            try {
                T result = objectMapper.readValue(cachedJson, resultClass);
                logger.info("Idempotency key found, returning cached result: key={}", idempotencyKey);
                return result;
            } catch (JsonProcessingException e) {
                logger.error("Failed to deserialize cached result for idempotency key: {}", idempotencyKey, e);
                // If deserialization fails, treat as if key doesn't exist
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Acquire a distributed lock for the given idempotency key to prevent concurrent processing.
     * Uses Redis SET NX (set if not exists) with expiration for distributed locking.
     * 
     * @param idempotencyKey The unique idempotency key
     * @return true if lock was acquired, false otherwise
     */
    public boolean acquireLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        
        String lockKey = LOCK_PREFIX + idempotencyKey;
        String lockValue = Thread.currentThread().getName() + ":" + System.currentTimeMillis();
        
        for (int attempt = 0; attempt < MAX_LOCK_ATTEMPTS; attempt++) {
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
            
            if (Boolean.TRUE.equals(acquired)) {
                logger.debug("Lock acquired for idempotency key: {}", idempotencyKey);
                return true;
            }
            
            // Check if there's already a cached result (another thread completed processing)
            if (getExistingResult(idempotencyKey, Object.class) != null) {
                logger.debug("Result already cached while waiting for lock: {}", idempotencyKey);
                return false;
            }
            
            // Wait before retrying
            try {
                Thread.sleep(LOCK_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Lock acquisition interrupted for idempotency key: {}", idempotencyKey);
                return false;
            }
        }
        
        logger.warn("Failed to acquire lock after {} attempts for idempotency key: {}", 
                   MAX_LOCK_ATTEMPTS, idempotencyKey);
        return false;
    }
    
    /**
     * Release the distributed lock for the given idempotency key.
     * 
     * @param idempotencyKey The unique idempotency key
     */
    public void releaseLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        
        String lockKey = LOCK_PREFIX + idempotencyKey;
        redisTemplate.delete(lockKey);
        logger.debug("Lock released for idempotency key: {}", idempotencyKey);
    }
    
    /**
     * Store the result atomically with the idempotency key.
     * The result will be cached for 24 hours.
     * 
     * @param idempotencyKey The unique idempotency key
     * @param result The result to cache
     */
    public <T> void storeResult(String idempotencyKey, T result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            logger.warn("Attempted to store result with null or blank idempotency key");
            return;
        }
        
        try {
            String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
            String resultJson = objectMapper.writeValueAsString(result);
            
            redisTemplate.opsForValue().set(key, resultJson, IDEMPOTENCY_TTL.toMillis(), TimeUnit.MILLISECONDS);
            logger.info("Result stored for idempotency key: key={}, ttl={}h", 
                       idempotencyKey, IDEMPOTENCY_TTL.toHours());
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize result for idempotency key: {}", idempotencyKey, e);
            throw new RuntimeException("Failed to store idempotency result", e);
        }
    }
    
    /**
     * Check if an idempotency key exists (either locked or has a cached result).
     * 
     * @param idempotencyKey The unique idempotency key
     * @return true if the key exists, false otherwise
     */
    public boolean exists(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * Delete an idempotency key and its lock (for testing purposes).
     * 
     * @param idempotencyKey The unique idempotency key
     */
    public void delete(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        String lockKey = LOCK_PREFIX + idempotencyKey;
        
        redisTemplate.delete(key);
        redisTemplate.delete(lockKey);
        logger.debug("Deleted idempotency key and lock: {}", idempotencyKey);
    }
}
