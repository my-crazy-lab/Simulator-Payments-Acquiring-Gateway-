package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.event.PaymentEventConsumer;
import com.paymentgateway.authorization.event.PaymentEventMessage;
import com.paymentgateway.authorization.event.PaymentEventType;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: payment-acquiring-gateway, Property 27: Idempotent Event Processing
 * 
 * For any event processed multiple times with the same event ID, the system state 
 * should be identical to processing it once.
 * 
 * Validates: Requirements 14.3
 */
public class IdempotentEventProcessingPropertyTest {
    
    @Property(tries = 100)
    void processingEventMultipleTimesShouldBeIdempotent(
            @ForAll("validEventMessages") PaymentEventMessage event) {
        
        // Setup mocks for this test
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        PaymentEventConsumer consumer = new PaymentEventConsumer(redisTemplate);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        // First processing - event not yet processed
        when(redisTemplate.hasKey("processed_event:" + event.getEventId())).thenReturn(false);
        
        consumer.consumePaymentEvent(event, 0, 0L, acknowledgment);
        
        // Verify event was marked as processed
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), 
                ttlCaptor.capture(), unitCaptor.capture());
        
        assertThat(keyCaptor.getValue()).isEqualTo("processed_event:" + event.getEventId());
        assertThat(valueCaptor.getValue()).isEqualTo("1");
        
        // Verify acknowledgment was called
        verify(acknowledgment, times(1)).acknowledge();
        
        // Reset mocks for second processing
        reset(valueOperations, acknowledgment);
        
        // Second processing - event already processed
        when(redisTemplate.hasKey("processed_event:" + event.getEventId())).thenReturn(true);
        
        consumer.consumePaymentEvent(event, 0, 1L, acknowledgment);
        
        // Verify event processing was skipped (no new Redis write)
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        
        // Verify acknowledgment was still called (message consumed but skipped)
        verify(acknowledgment, times(1)).acknowledge();
    }
    
    @Property(tries = 100)
    void concurrentProcessingOfSameEventShouldBeIdempotent(
            @ForAll("validEventMessages") PaymentEventMessage event) {
        
        // Setup mocks for this test
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        PaymentEventConsumer consumer = new PaymentEventConsumer(redisTemplate);
        
        // Simulate concurrent processing - both threads check at same time
        when(redisTemplate.hasKey("processed_event:" + event.getEventId())).thenReturn(false);
        
        Acknowledgment ack1 = mock(Acknowledgment.class);
        Acknowledgment ack2 = mock(Acknowledgment.class);
        
        // First thread processes
        consumer.consumePaymentEvent(event, 0, 0L, ack1);
        
        // Second thread tries to process (but Redis now shows it's processed)
        when(redisTemplate.hasKey("processed_event:" + event.getEventId())).thenReturn(true);
        consumer.consumePaymentEvent(event, 0, 0L, ack2);
        
        // Verify Redis write happened only once
        verify(valueOperations, times(1)).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        
        // Both acknowledgments should be called
        verify(ack1, times(1)).acknowledge();
        verify(ack2, times(1)).acknowledge();
    }
    
    @Provide
    Arbitrary<PaymentEventMessage> validEventMessages() {
        return Combinators.combine(
                validEventIds(),
                Arbitraries.of(PaymentEventType.values()),
                Arbitraries.just(Instant.now()),
                validPaymentIds(),
                validTraceIds(),
                validPayloads()
        ).as((eventId, eventType, timestamp, correlationId, traceId, payload) -> 
                new PaymentEventMessage(eventId, eventType, timestamp, correlationId, traceId, payload)
        );
    }
    
    @Provide
    Arbitrary<String> validEventIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(24)
                .map(s -> "evt_" + s);
    }
    
    @Provide
    Arbitrary<String> validPaymentIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(24)
                .map(s -> "pay_" + s);
    }
    
    @Provide
    Arbitrary<String> validTraceIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(32);
    }
    
    @Provide
    Arbitrary<String> validMerchantIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(36);
    }
    
    @Provide
    Arbitrary<BigDecimal> validAmounts() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(999999.99))
                .ofScale(2);
    }
    
    @Provide
    Arbitrary<String> validCurrencies() {
        return Arbitraries.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD");
    }
    
    @Provide
    Arbitrary<String> validStatuses() {
        return Arbitraries.of("PENDING", "AUTHORIZED", "CAPTURED", "DECLINED", "CANCELLED", "REFUNDED");
    }
    
    @Provide
    Arbitrary<PaymentEventMessage.PaymentEventPayload> validPayloads() {
        return Combinators.combine(
                validPaymentIds(),
                validMerchantIds(),
                validAmounts(),
                validCurrencies(),
                validStatuses()
        ).as((paymentId, merchantId, amount, currency, status) -> {
            PaymentEventMessage.PaymentEventPayload payload = new PaymentEventMessage.PaymentEventPayload();
            payload.setPaymentId(paymentId);
            payload.setMerchantId(merchantId);
            payload.setAmount(amount);
            payload.setCurrency(currency);
            payload.setStatus(status);
            return payload;
        });
    }
}
