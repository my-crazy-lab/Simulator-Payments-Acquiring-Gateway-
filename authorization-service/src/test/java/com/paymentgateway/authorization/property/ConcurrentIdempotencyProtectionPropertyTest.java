package com.paymentgateway.authorization.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.authorization.idempotency.IdempotencyService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: payment-acquiring-gateway, Property 31: Concurrent Idempotency Protection
 * 
 * For any concurrent payment requests with the same idempotency key,
 * distributed locking should ensure only one request processes while others wait.
 * 
 * Validates: Requirements 21.4
 */
public class ConcurrentIdempotencyProtectionPropertyTest {
    
    /**
     * Property: For any idempotency key, acquiring a lock should succeed when no lock exists.
     */
    @Property(tries = 100)
    void lockAcquisitionSucceedsWhenNoLockExists(
            @ForAll("validIdempotencyKey") String idempotencyKey) {
        
        // Arrange
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Simulate successful lock acquisition (no existing lock)
        when(valueOps.setIfAbsent(
            eq("idempotency:lock:" + idempotencyKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        )).thenReturn(true);
        
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        
        // Act
        boolean lockAcquired = idempotencyService.acquireLock(idempotencyKey);
        
        // Assert
        assertThat(lockAcquired)
            .as("Lock acquisition should succeed when no lock exists")
            .isTrue();
        
        // Verify setIfAbsent was called
        verify(valueOps, atLeastOnce()).setIfAbsent(
            eq("idempotency:lock:" + idempotencyKey),
            anyString(),
            eq(30000L), // 30 seconds in milliseconds
            eq(TimeUnit.MILLISECONDS)
        );
    }
    
    /**
     * Property: For any idempotency key with an existing lock, acquiring the lock should fail
     * after max attempts if no result is cached.
     */
    @Property(tries = 100)
    void lockAcquisitionFailsWhenLockExists(
            @ForAll("validIdempotencyKey") String idempotencyKey) {
        
        // Arrange
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Simulate failed lock acquisition (lock already exists)
        when(valueOps.setIfAbsent(
            eq("idempotency:lock:" + idempotencyKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        )).thenReturn(false);
        
        // No cached result exists
        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(null);
        
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        
        // Act
        boolean lockAcquired = idempotencyService.acquireLock(idempotencyKey);
        
        // Assert
        assertThat(lockAcquired)
            .as("Lock acquisition should fail when lock already exists and no result is cached")
            .isFalse();
        
        // Verify multiple attempts were made (max 10 attempts)
        verify(valueOps, times(10)).setIfAbsent(
            eq("idempotency:lock:" + idempotencyKey),
            anyString(),
            eq(30000L),
            eq(TimeUnit.MILLISECONDS)
        );
    }
    
    /**
     * Property: For any idempotency key, releasing a lock should delete the lock key.
     */
    @Property(tries = 100)
    void lockReleaseDeletesLockKey(
            @ForAll("validIdempotencyKey") String idempotencyKey) {
        
        // Arrange
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        
        // Act
        idempotencyService.releaseLock(idempotencyKey);
        
        // Assert
        verify(redisTemplate).delete("idempotency:lock:" + idempotencyKey);
    }
    
    /**
     * Property: For any null or blank idempotency key, lock operations should handle gracefully.
     */
    @Property(tries = 100)
    void nullOrBlankIdempotencyKeyHandledGracefully(
            @ForAll("nullOrBlankStrings") String invalidKey) {
        
        // Arrange
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        
        // Act
        boolean lockAcquired = idempotencyService.acquireLock(invalidKey);
        
        // Assert
        assertThat(lockAcquired)
            .as("Lock acquisition with null/blank key should return false")
            .isFalse();
        
        // Verify no Redis operations were attempted
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
        
        // Act - Release lock
        idempotencyService.releaseLock(invalidKey);
        
        // Verify no delete was attempted
        verify(redisTemplate, never()).delete(anyString());
    }
    
    /**
     * Property: For any idempotency key, lock acquisition should check for cached results
     * during retry attempts.
     */
    @Property(tries = 100)
    void lockAcquisitionChecksForCachedResultsDuringRetry(
            @ForAll("validIdempotencyKey") String idempotencyKey,
            @ForAll @IntRange(min = 1, max = 5) int failedAttemptsBeforeResult) {
        
        // Arrange
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        // Simulate lock acquisition failing initially
        when(valueOps.setIfAbsent(
            eq("idempotency:lock:" + idempotencyKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        )).thenReturn(false);
        
        // Simulate a cached result appearing after some attempts
        when(valueOps.get("idempotency:" + idempotencyKey))
            .thenReturn(null) // First few checks return null
            .thenReturn(null)
            .thenReturn("{\"paymentId\":\"pay_123\"}"); // Then a result appears
        
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        
        // Act
        boolean lockAcquired = idempotencyService.acquireLock(idempotencyKey);
        
        // Assert
        assertThat(lockAcquired)
            .as("Lock acquisition should fail when cached result appears during retry")
            .isFalse();
        
        // Verify that cached result was checked during retries
        verify(valueOps, atLeast(1)).get("idempotency:" + idempotencyKey);
    }
    
    @Provide
    Arbitrary<String> validIdempotencyKey() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(20).ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> nullOrBlankStrings() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }
}
