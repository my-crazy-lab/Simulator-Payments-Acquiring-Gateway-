package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.event.PaymentEventConsumer;
import com.paymentgateway.authorization.event.PaymentEventMessage;
import com.paymentgateway.authorization.event.PaymentEventType;
import net.jqwik.api.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Feature: payment-acquiring-gateway, Property 28: Event Ordering Preserved
 * 
 * For any sequence of events with the same partition key, consuming the events 
 * should maintain the original publication order.
 * 
 * Validates: Requirements 14.5
 */
public class EventOrderingPreservedPropertyTest {
    
    @Property(tries = 100)
    void eventsWithSamePartitionKeyShouldMaintainOrder(
            @ForAll("eventSequences") List<PaymentEventMessage> events) {
        
        // Setup mocks
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(false); // No events processed yet
        
        PaymentEventConsumer consumer = new PaymentEventConsumer(redisTemplate);
        
        // Track the order of processed events
        List<String> processedEventIds = new ArrayList<>();
        AtomicInteger processOrder = new AtomicInteger(0);
        
        // Process events in order
        for (int i = 0; i < events.size(); i++) {
            PaymentEventMessage event = events.get(i);
            Acknowledgment ack = mock(Acknowledgment.class);
            
            // Capture when event is processed
            doAnswer(invocation -> {
                processedEventIds.add(event.getEventId());
                return null;
            }).when(ack).acknowledge();
            
            // Consume event with same partition (0) to simulate same partition key
            consumer.consumePaymentEvent(event, 0, (long) i, ack);
        }
        
        // Verify events were processed in the same order they were sent
        assertThat(processedEventIds).hasSize(events.size());
        for (int i = 0; i < events.size(); i++) {
            assertThat(processedEventIds.get(i))
                    .as("Event at position %d should match original order", i)
                    .isEqualTo(events.get(i).getEventId());
        }
    }
    
    @Property(tries = 100)
    void eventsForSamePaymentShouldBeProcessedInOrder(
            @ForAll("samePaymentEventSequence") List<PaymentEventMessage> events) {
        
        // All events in this sequence are for the same payment
        String paymentId = events.get(0).getPayload().getPaymentId();
        
        // Verify all events are for the same payment
        assertThat(events).allMatch(e -> e.getPayload().getPaymentId().equals(paymentId));
        
        // Setup mocks
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        
        PaymentEventConsumer consumer = new PaymentEventConsumer(redisTemplate);
        
        // Track processing order
        List<PaymentEventType> processedEventTypes = new ArrayList<>();
        
        // Process events
        for (int i = 0; i < events.size(); i++) {
            PaymentEventMessage event = events.get(i);
            Acknowledgment ack = mock(Acknowledgment.class);
            
            doAnswer(invocation -> {
                processedEventTypes.add(event.getEventType());
                return null;
            }).when(ack).acknowledge();
            
            consumer.consumePaymentEvent(event, 0, (long) i, ack);
        }
        
        // Verify event types were processed in original order
        assertThat(processedEventTypes).hasSize(events.size());
        for (int i = 0; i < events.size(); i++) {
            assertThat(processedEventTypes.get(i))
                    .as("Event type at position %d should match original order", i)
                    .isEqualTo(events.get(i).getEventType());
        }
    }
    
    @Provide
    Arbitrary<List<PaymentEventMessage>> eventSequences() {
        return Arbitraries.integers().between(2, 10).flatMap(size -> {
            List<Arbitrary<PaymentEventMessage>> eventArbitraries = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                eventArbitraries.add(validEventMessage());
            }
            return Combinators.combine(eventArbitraries).as(events -> events);
        });
    }
    
    @Provide
    Arbitrary<List<PaymentEventMessage>> samePaymentEventSequence() {
        return validPaymentIds().flatMap(paymentId -> {
            // Create a sequence of events for the same payment in logical order
            return Arbitraries.integers().between(2, 5).flatMap(size -> {
                List<Arbitrary<PaymentEventMessage>> eventArbitraries = new ArrayList<>();
                
                // Define logical event progression
                PaymentEventType[] progression = {
                    PaymentEventType.PAYMENT_CREATED,
                    PaymentEventType.PAYMENT_AUTHORIZED,
                    PaymentEventType.PAYMENT_CAPTURED
                };
                
                for (int i = 0; i < Math.min(size, progression.length); i++) {
                    final int index = i;
                    eventArbitraries.add(
                        Combinators.combine(
                            validEventIds(),
                            Arbitraries.just(progression[index]),
                            Arbitraries.just(Instant.now().plusSeconds(index)),
                            Arbitraries.just(paymentId),
                            validTraceIds(),
                            payloadForPayment(paymentId)
                        ).as((eventId, eventType, timestamp, corrId, traceId, payload) ->
                            new PaymentEventMessage(eventId, eventType, timestamp, corrId, traceId, payload)
                        )
                    );
                }
                
                return Combinators.combine(eventArbitraries).as(events -> events);
            });
        });
    }
    
    @Provide
    Arbitrary<PaymentEventMessage> validEventMessage() {
        return Combinators.combine(
                validEventIds(),
                Arbitraries.of(PaymentEventType.values()),
                Arbitraries.just(Instant.now()),
                validPaymentIds(),
                validTraceIds(),
                validPayload()
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
    Arbitrary<PaymentEventMessage.PaymentEventPayload> validPayload() {
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
    
    @Provide
    Arbitrary<PaymentEventMessage.PaymentEventPayload> payloadForPayment(String paymentId) {
        return Combinators.combine(
                validMerchantIds(),
                validAmounts(),
                validCurrencies(),
                validStatuses()
        ).as((merchantId, amount, currency, status) -> {
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
