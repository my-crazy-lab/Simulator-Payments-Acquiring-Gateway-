package com.paymentgateway.authorization.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.idempotency.IdempotencyService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: payment-acquiring-gateway, Property 30: Idempotency Key Deduplication
 * 
 * For any payment request submitted multiple times with the same idempotency key,
 * only one payment transaction should be created and all requests should return the same result.
 * 
 * Validates: Requirements 21.2
 */
public class IdempotencyKeyDeduplicationPropertyTest {
    
    /**
     * Property: For any idempotency key, checking for an existing result multiple times
     * should return the same cached result.
     */
    @Property(tries = 100)
    void idempotencyKeyReturnsConsistentCachedResult(
            @ForAll("validIdempotencyKey") String idempotencyKey,
            @ForAll("validPaymentResponse") PaymentResponse cachedResponse,
            @ForAll @IntRange(min = 2, max = 10) int checkCount) throws Exception {
        
        // Arrange
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        String cachedJson = objectMapper.writeValueAsString(cachedResponse);
        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(cachedJson);
        
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        
        // Act - Check for existing result multiple times
        List<PaymentResponse> results = new ArrayList<>();
        for (int i = 0; i < checkCount; i++) {
            PaymentResponse result = idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class);
            results.add(result);
        }
        
        // Assert - All results should be identical to the cached response
        for (int i = 0; i < results.size(); i++) {
            PaymentResponse result = results.get(i);
            
            assertThat(result).isNotNull();
            assertThat(result.getPaymentId())
                .as("Result %d should have same payment ID as cached response", i)
                .isEqualTo(cachedResponse.getPaymentId());
            assertThat(result.getStatus())
                .as("Result %d should have same status as cached response", i)
                .isEqualTo(cachedResponse.getStatus());
            assertThat(result.getAmount())
                .as("Result %d should have same amount as cached response", i)
                .isEqualByComparingTo(cachedResponse.getAmount());
        }
        
        // Verify Redis was queried the correct number of times
        verify(valueOps, times(checkCount)).get("idempotency:" + idempotencyKey);
    }
    
    /**
     * Property: For any idempotency key that doesn't exist, getExistingResult should return null.
     */
    @Property(tries = 100)
    void nonExistentIdempotencyKeyReturnsNull(
            @ForAll("validIdempotencyKey") String idempotencyKey) {
        
        // Arrange
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(null);
        
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        
        // Act
        PaymentResponse result = idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class);
        
        // Assert
        assertThat(result)
            .as("Non-existent idempotency key should return null")
            .isNull();
    }
    
    /**
     * Property: For any result stored with an idempotency key, it should be retrievable.
     */
    @Property(tries = 100)
    void storedResultShouldBeRetrievable(
            @ForAll("validIdempotencyKey") String idempotencyKey,
            @ForAll("validPaymentResponse") PaymentResponse response) throws Exception {
        
        // Arrange
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        
        // Act - Store the result
        idempotencyService.storeResult(idempotencyKey, response);
        
        // Assert - Verify Redis set was called with correct parameters
        String expectedJson = objectMapper.writeValueAsString(response);
        verify(valueOps).set(
            eq("idempotency:" + idempotencyKey),
            eq(expectedJson),
            eq(86400000L), // 24 hours in milliseconds
            eq(TimeUnit.MILLISECONDS)
        );
    }
    
    @Provide
    Arbitrary<String> validIdempotencyKey() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(20).ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<PaymentResponse> validPaymentResponse() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(30),
            Arbitraries.of(com.paymentgateway.authorization.domain.PaymentStatus.values()),
            Arbitraries.bigDecimals().between(BigDecimal.ONE, BigDecimal.valueOf(10000)).ofScale(2),
            Arbitraries.of("USD", "EUR", "GBP"),
            Arbitraries.strings().numeric().ofLength(4)
        ).as((paymentId, status, amount, currency, lastFour) -> {
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId("pay_" + paymentId);
            response.setStatus(status);
            response.setAmount(amount);
            response.setCurrency(currency);
            response.setCardLastFour(lastFour);
            response.setCardBrand("VISA");
            response.setCreatedAt(Instant.now());
            return response;
        });
    }
}
