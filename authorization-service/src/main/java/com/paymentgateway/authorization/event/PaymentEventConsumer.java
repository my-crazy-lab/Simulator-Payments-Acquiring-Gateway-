package com.paymentgateway.authorization.event;

import com.paymentgateway.authorization.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Consumer for payment events from Kafka.
 * Implements idempotent event processing using Redis for deduplication.
 */
@Service
public class PaymentEventConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final String PROCESSED_EVENTS_KEY_PREFIX = "processed_event:";
    private static final Duration EVENT_DEDUPLICATION_TTL = Duration.ofHours(24);
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public PaymentEventConsumer(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Consumes payment events from Kafka with idempotent processing.
     * Uses Redis to track processed event IDs and prevent duplicate processing.
     */
    @KafkaListener(
            topics = KafkaConfig.PAYMENT_EVENTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:authorization-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentEvent(
            @Payload PaymentEventMessage event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            logger.info("Received payment event: eventId={}, eventType={}, paymentId={}, partition={}, offset={}",
                    event.getEventId(), event.getEventType(), 
                    event.getPayload().getPaymentId(), partition, offset);
            
            // Check if event has already been processed (idempotency)
            if (isEventProcessed(event.getEventId())) {
                logger.info("Event already processed, skipping: eventId={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Process the event
            processEvent(event);
            
            // Mark event as processed
            markEventAsProcessed(event.getEventId());
            
            // Acknowledge the message
            acknowledgment.acknowledge();
            
            logger.info("Successfully processed payment event: eventId={}, eventType={}",
                    event.getEventId(), event.getEventType());
            
        } catch (Exception e) {
            logger.error("Error processing payment event: eventId={}, eventType={}, paymentId={}",
                    event.getEventId(), event.getEventType(), 
                    event.getPayload().getPaymentId(), e);
            
            // Don't acknowledge - message will be redelivered
            // In production, implement retry logic with exponential backoff
            // and move to DLQ after max retries
            throw new RuntimeException("Failed to process payment event", e);
        }
    }
    
    /**
     * Processes a payment event.
     * This is where business logic for event handling would be implemented.
     */
    private void processEvent(PaymentEventMessage event) {
        // Business logic based on event type
        switch (event.getEventType()) {
            case PAYMENT_AUTHORIZED:
                handlePaymentAuthorized(event);
                break;
            case PAYMENT_CAPTURED:
                handlePaymentCaptured(event);
                break;
            case PAYMENT_DECLINED:
                handlePaymentDeclined(event);
                break;
            case PAYMENT_CANCELLED:
                handlePaymentCancelled(event);
                break;
            case PAYMENT_REFUNDED:
                handlePaymentRefunded(event);
                break;
            default:
                logger.warn("Unknown event type: {}", event.getEventType());
        }
    }
    
    private void handlePaymentAuthorized(PaymentEventMessage event) {
        logger.info("Handling PAYMENT_AUTHORIZED event: paymentId={}", 
                event.getPayload().getPaymentId());
        // Implementation: Update analytics, trigger webhooks, etc.
    }
    
    private void handlePaymentCaptured(PaymentEventMessage event) {
        logger.info("Handling PAYMENT_CAPTURED event: paymentId={}", 
                event.getPayload().getPaymentId());
        // Implementation: Trigger settlement processing, update reporting, etc.
    }
    
    private void handlePaymentDeclined(PaymentEventMessage event) {
        logger.info("Handling PAYMENT_DECLINED event: paymentId={}", 
                event.getPayload().getPaymentId());
        // Implementation: Update fraud models, notify merchant, etc.
    }
    
    private void handlePaymentCancelled(PaymentEventMessage event) {
        logger.info("Handling PAYMENT_CANCELLED event: paymentId={}", 
                event.getPayload().getPaymentId());
        // Implementation: Release reserved funds, update analytics, etc.
    }
    
    private void handlePaymentRefunded(PaymentEventMessage event) {
        logger.info("Handling PAYMENT_REFUNDED event: paymentId={}", 
                event.getPayload().getPaymentId());
        // Implementation: Update settlement records, trigger webhooks, etc.
    }
    
    /**
     * Checks if an event has already been processed.
     */
    private boolean isEventProcessed(String eventId) {
        String key = PROCESSED_EVENTS_KEY_PREFIX + eventId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * Marks an event as processed in Redis with TTL.
     */
    private void markEventAsProcessed(String eventId) {
        String key = PROCESSED_EVENTS_KEY_PREFIX + eventId;
        redisTemplate.opsForValue().set(key, "1", 
                EVENT_DEDUPLICATION_TTL.toMillis(), TimeUnit.MILLISECONDS);
    }
}
