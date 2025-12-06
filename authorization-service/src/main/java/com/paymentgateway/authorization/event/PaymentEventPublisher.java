package com.paymentgateway.authorization.event;

import com.paymentgateway.authorization.config.KafkaConfig;
import com.paymentgateway.authorization.domain.Payment;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing payment events to Kafka.
 * Handles event creation, schema validation, and publishing with proper error handling.
 */
@Service
public class PaymentEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentEventPublisher.class);
    
    private final KafkaTemplate<String, PaymentEventMessage> kafkaTemplate;
    private final Tracer tracer;
    
    public PaymentEventPublisher(KafkaTemplate<String, PaymentEventMessage> kafkaTemplate,
                                Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
    }
    
    /**
     * Publishes a payment event to Kafka.
     * Uses the payment ID as the partition key to ensure ordering.
     */
    public void publishPaymentEvent(Payment payment, PaymentEventType eventType) {
        Span span = tracer.spanBuilder("publishPaymentEvent").startSpan();
        try (var scope = span.makeCurrent()) {
            
            // Create event message
            PaymentEventMessage event = createEventMessage(payment, eventType);
            
            // Use payment ID as partition key to ensure ordering
            String partitionKey = payment.getPaymentId();
            
            // Publish to Kafka
            CompletableFuture<SendResult<String, PaymentEventMessage>> future = 
                    kafkaTemplate.send(KafkaConfig.PAYMENT_EVENTS_TOPIC, partitionKey, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.info("Published payment event: eventId={}, eventType={}, paymentId={}, partition={}, offset={}",
                            event.getEventId(), eventType, payment.getPaymentId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    span.addEvent("event_published");
                } else {
                    logger.error("Failed to publish payment event: eventId={}, eventType={}, paymentId={}",
                            event.getEventId(), eventType, payment.getPaymentId(), ex);
                    span.recordException(ex);
                }
            });
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Error creating payment event: paymentId={}, eventType={}",
                    payment.getPaymentId(), eventType, e);
            throw new RuntimeException("Failed to publish payment event", e);
        } finally {
            span.end();
        }
    }
    
    /**
     * Creates a payment event message from a payment entity.
     */
    private PaymentEventMessage createEventMessage(Payment payment, PaymentEventType eventType) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String traceId = Span.current().getSpanContext().getTraceId();
        
        // Create payload
        PaymentEventMessage.PaymentEventPayload payload = new PaymentEventMessage.PaymentEventPayload();
        payload.setPaymentId(payment.getPaymentId());
        payload.setMerchantId(payment.getMerchantId() != null ? payment.getMerchantId().toString() : null);
        payload.setAmount(payment.getAmount());
        payload.setCurrency(payment.getCurrency());
        payload.setStatus(payment.getStatus() != null ? payment.getStatus().name() : null);
        payload.setPspTransactionId(payment.getPspTransactionId());
        payload.setFraudScore(payment.getFraudScore());
        payload.setThreeDsStatus(payment.getThreeDsStatus() != null ? payment.getThreeDsStatus().name() : null);
        
        // Create event message
        return new PaymentEventMessage(
                eventId,
                eventType,
                Instant.now(),
                payment.getPaymentId(), // correlation ID
                traceId,
                payload
        );
    }
}
