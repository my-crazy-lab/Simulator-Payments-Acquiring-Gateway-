package com.paymentgateway.authorization.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentgateway.authorization.domain.PaymentStatus;
import com.paymentgateway.authorization.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IdempotencyService edge cases.
 * Tests expired keys, concurrent access, and key collision scenarios.
 */
class IdempotencyServiceEdgeCasesTest {
    
    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private IdempotencyService idempotencyService;
    
    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
    }
    
    @Test
    void testExpiredKeyReturnsNull() {
        // Arrange
        String idempotencyKey = "expired-key-12345";
        
        // Simulate expired key (Redis returns null)
        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(null);
        
        // Act
        PaymentResponse result = idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class);
        
        // Assert
        assertThat(result).isNull();
    }
    
    @Test
    void testConcurrentAccessWithSameKey() {
        // Arrange
        String idempotencyKey = "concurrent-key-12345";
        
        // First thread acquires lock successfully
        when(valueOps.setIfAbsent(
            eq("idempotency:lock:" + idempotencyKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        )).thenReturn(true).thenReturn(false); // First succeeds, second fails
        
        // Act
        boolean firstAcquire = idempotencyService.acquireLock(idempotencyKey);
        boolean secondAcquire = idempotencyService.acquireLock(idempotencyKey);
        
        // Assert
        assertThat(firstAcquire).isTrue();
        assertThat(secondAcquire).isFalse();
    }
    
    @Test
    void testKeyCollisionWithDifferentValues() throws Exception {
        // Arrange
        String idempotencyKey = "collision-key-12345";
        
        PaymentResponse response1 = createPaymentResponse("pay_111", PaymentStatus.AUTHORIZED, BigDecimal.valueOf(100.00));
        PaymentResponse response2 = createPaymentResponse("pay_222", PaymentStatus.PENDING, BigDecimal.valueOf(200.00));
        
        String json1 = objectMapper.writeValueAsString(response1);
        String json2 = objectMapper.writeValueAsString(response2);
        
        // Simulate first value stored
        when(valueOps.get("idempotency:" + idempotencyKey))
            .thenReturn(json1)
            .thenReturn(json1); // Always returns first value
        
        // Act
        PaymentResponse result1 = idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class);
        
        // Store second value (should not overwrite in real scenario, but we're testing retrieval)
        idempotencyService.storeResult(idempotencyKey, response2);
        
        PaymentResponse result2 = idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class);
        
        // Assert - Should always get the first stored value
        assertThat(result1.getPaymentId()).isEqualTo("pay_111");
        assertThat(result2.getPaymentId()).isEqualTo("pay_111"); // Still first value
    }
    
    @Test
    void testNullIdempotencyKeyHandling() {
        // Act & Assert - getExistingResult
        PaymentResponse result = idempotencyService.getExistingResult(null, PaymentResponse.class);
        assertThat(result).isNull();
        
        // Act & Assert - acquireLock
        boolean lockAcquired = idempotencyService.acquireLock(null);
        assertThat(lockAcquired).isFalse();
        
        // Act & Assert - releaseLock (should not throw)
        idempotencyService.releaseLock(null);
        
        // Act & Assert - storeResult (should log warning but not throw)
        PaymentResponse response = createPaymentResponse("pay_123", PaymentStatus.AUTHORIZED, BigDecimal.valueOf(100.00));
        idempotencyService.storeResult(null, response);
        
        // Verify no Redis operations were attempted
        verify(valueOps, never()).get(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any());
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }
    
    @Test
    void testBlankIdempotencyKeyHandling() {
        // Act & Assert - getExistingResult
        PaymentResponse result = idempotencyService.getExistingResult("   ", PaymentResponse.class);
        assertThat(result).isNull();
        
        // Act & Assert - acquireLock
        boolean lockAcquired = idempotencyService.acquireLock("   ");
        assertThat(lockAcquired).isFalse();
        
        // Act & Assert - releaseLock (should not throw)
        idempotencyService.releaseLock("   ");
        
        // Act & Assert - storeResult (should log warning but not throw)
        PaymentResponse response = createPaymentResponse("pay_123", PaymentStatus.AUTHORIZED, BigDecimal.valueOf(100.00));
        idempotencyService.storeResult("   ", response);
        
        // Verify no Redis operations were attempted
        verify(valueOps, never()).get(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any());
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }
    
    @Test
    void testCorruptedCachedDataHandling() {
        // Arrange
        String idempotencyKey = "corrupted-key-12345";
        String corruptedJson = "{invalid json data}";
        
        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(corruptedJson);
        
        // Act
        PaymentResponse result = idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class);
        
        // Assert - Should return null when deserialization fails
        assertThat(result).isNull();
    }
    
    @Test
    void testStoreResultWithSerializationFailure() throws Exception {
        // Arrange
        String idempotencyKey = "serialization-fail-key";
        
        // Create a mock ObjectMapper that throws on serialization
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialization failed") {});
        
        IdempotencyService serviceWithFailingMapper = new IdempotencyService(redisTemplate, failingMapper);
        
        PaymentResponse response = createPaymentResponse("pay_123", PaymentStatus.AUTHORIZED, BigDecimal.valueOf(100.00));
        
        // Act & Assert
        assertThatThrownBy(() -> serviceWithFailingMapper.storeResult(idempotencyKey, response))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to store idempotency result");
    }
    
    @Test
    void testExistsMethod() {
        // Arrange
        String existingKey = "existing-key-12345";
        String nonExistingKey = "non-existing-key-12345";
        
        when(redisTemplate.hasKey("idempotency:" + existingKey)).thenReturn(true);
        when(redisTemplate.hasKey("idempotency:" + nonExistingKey)).thenReturn(false);
        
        // Act & Assert
        assertThat(idempotencyService.exists(existingKey)).isTrue();
        assertThat(idempotencyService.exists(nonExistingKey)).isFalse();
        assertThat(idempotencyService.exists(null)).isFalse();
        assertThat(idempotencyService.exists("   ")).isFalse();
    }
    
    @Test
    void testDeleteMethod() {
        // Arrange
        String idempotencyKey = "delete-key-12345";
        
        // Act
        idempotencyService.delete(idempotencyKey);
        
        // Assert
        verify(redisTemplate).delete("idempotency:" + idempotencyKey);
        verify(redisTemplate).delete("idempotency:lock:" + idempotencyKey);
    }
    
    @Test
    void testLockExpirationHandling() {
        // Arrange
        String idempotencyKey = "lock-expiration-key";
        
        // Simulate lock acquisition with TTL
        when(valueOps.setIfAbsent(
            eq("idempotency:lock:" + idempotencyKey),
            anyString(),
            eq(30000L), // 30 seconds
            eq(TimeUnit.MILLISECONDS)
        )).thenReturn(true);
        
        // Act
        boolean lockAcquired = idempotencyService.acquireLock(idempotencyKey);
        
        // Assert
        assertThat(lockAcquired).isTrue();
        
        // Verify TTL was set
        verify(valueOps).setIfAbsent(
            eq("idempotency:lock:" + idempotencyKey),
            anyString(),
            eq(30000L),
            eq(TimeUnit.MILLISECONDS)
        );
    }
    
    @Test
    void testResultExpirationHandling() throws Exception {
        // Arrange
        String idempotencyKey = "result-expiration-key";
        PaymentResponse response = createPaymentResponse("pay_123", PaymentStatus.AUTHORIZED, BigDecimal.valueOf(100.00));
        
        // Act
        idempotencyService.storeResult(idempotencyKey, response);
        
        // Assert - Verify 24-hour TTL was set
        String expectedJson = objectMapper.writeValueAsString(response);
        verify(valueOps).set(
            eq("idempotency:" + idempotencyKey),
            eq(expectedJson),
            eq(86400000L), // 24 hours in milliseconds
            eq(TimeUnit.MILLISECONDS)
        );
    }
    
    private PaymentResponse createPaymentResponse(String paymentId, PaymentStatus status, BigDecimal amount) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(paymentId);
        response.setStatus(status);
        response.setAmount(amount);
        response.setCurrency("USD");
        response.setCardLastFour("1234");
        response.setCardBrand("VISA");
        response.setCreatedAt(Instant.now());
        return response;
    }
}
