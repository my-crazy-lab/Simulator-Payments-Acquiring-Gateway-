package com.paymentgateway.authorization.event;

import com.paymentgateway.authorization.domain.*;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Kafka event publishing and consumption.
 */
class KafkaIntegrationTest {
    
    private KafkaTemplate<String, PaymentEventMessage> kafkaTemplate;
    private Tracer tracer;
    private PaymentEventPublisher publisher;
    
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        tracer = mock(Tracer.class);
        publisher = new PaymentEventPublisher(kafkaTemplate, tracer);
        
        // Mock tracer
        io.opentelemetry.api.trace.Span span = mock(io.opentelemetry.api.trace.Span.class);
        io.opentelemetry.api.trace.SpanBuilder spanBuilder = mock(io.opentelemetry.api.trace.SpanBuilder.class);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(mock(io.opentelemetry.context.Scope.class));
        
        io.opentelemetry.api.trace.SpanContext spanContext = mock(io.opentelemetry.api.trace.SpanContext.class);
        when(span.getSpanContext()).thenReturn(spanContext);
        when(spanContext.getTraceId()).thenReturn("test-trace-id");
    }
    
    @Test
    void shouldPublishPaymentAuthorizedEvent() {
        // Given
        Payment payment = createTestPayment();
        payment.setStatus(PaymentStatus.AUTHORIZED);
        
        CompletableFuture<SendResult<String, PaymentEventMessage>> future = new CompletableFuture<>();
        SendResult<String, PaymentEventMessage> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = 
                mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(123L);
        future.complete(sendResult);
        
        when(kafkaTemplate.send(anyString(), anyString(), any(PaymentEventMessage.class)))
                .thenReturn(future);
        
        // When
        publisher.publishPaymentEvent(payment, PaymentEventType.PAYMENT_AUTHORIZED);
        
        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PaymentEventMessage> eventCaptor = ArgumentCaptor.forClass(PaymentEventMessage.class);
        
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
        
        assertThat(topicCaptor.getValue()).isEqualTo("payment-events");
        assertThat(keyCaptor.getValue()).isEqualTo(payment.getPaymentId());
        
        PaymentEventMessage event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_AUTHORIZED);
        assertThat(event.getPayload().getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(event.getPayload().getAmount()).isEqualByComparingTo(payment.getAmount());
        assertThat(event.getPayload().getCurrency()).isEqualTo(payment.getCurrency());
        assertThat(event.getPayload().getStatus()).isEqualTo("AUTHORIZED");
    }
    
    @Test
    void shouldPublishPaymentCapturedEvent() {
        // Given
        Payment payment = createTestPayment();
        payment.setStatus(PaymentStatus.CAPTURED);
        
        CompletableFuture<SendResult<String, PaymentEventMessage>> future = new CompletableFuture<>();
        SendResult<String, PaymentEventMessage> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = 
                mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(124L);
        future.complete(sendResult);
        
        when(kafkaTemplate.send(anyString(), anyString(), any(PaymentEventMessage.class)))
                .thenReturn(future);
        
        // When
        publisher.publishPaymentEvent(payment, PaymentEventType.PAYMENT_CAPTURED);
        
        // Then
        ArgumentCaptor<PaymentEventMessage> eventCaptor = ArgumentCaptor.forClass(PaymentEventMessage.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());
        
        PaymentEventMessage event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_CAPTURED);
        assertThat(event.getPayload().getStatus()).isEqualTo("CAPTURED");
    }
    
    @Test
    void shouldUsePaymentIdAsPartitionKey() {
        // Given
        Payment payment = createTestPayment();
        String expectedPartitionKey = payment.getPaymentId();
        
        CompletableFuture<SendResult<String, PaymentEventMessage>> future = new CompletableFuture<>();
        SendResult<String, PaymentEventMessage> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = 
                mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(125L);
        future.complete(sendResult);
        
        when(kafkaTemplate.send(anyString(), anyString(), any(PaymentEventMessage.class)))
                .thenReturn(future);
        
        // When
        publisher.publishPaymentEvent(payment, PaymentEventType.PAYMENT_AUTHORIZED);
        
        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), any(PaymentEventMessage.class));
        
        assertThat(keyCaptor.getValue()).isEqualTo(expectedPartitionKey);
    }
    
    @Test
    void shouldIncludeAllRequiredFieldsInEvent() {
        // Given
        Payment payment = createTestPayment();
        payment.setFraudScore(BigDecimal.valueOf(0.25));
        payment.setThreeDsStatus(ThreeDSStatus.AUTHENTICATED);
        payment.setPspTransactionId("psp_txn_123");
        
        CompletableFuture<SendResult<String, PaymentEventMessage>> future = new CompletableFuture<>();
        SendResult<String, PaymentEventMessage> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = 
                mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(126L);
        future.complete(sendResult);
        
        when(kafkaTemplate.send(anyString(), anyString(), any(PaymentEventMessage.class)))
                .thenReturn(future);
        
        // When
        publisher.publishPaymentEvent(payment, PaymentEventType.PAYMENT_AUTHORIZED);
        
        // Then
        ArgumentCaptor<PaymentEventMessage> eventCaptor = ArgumentCaptor.forClass(PaymentEventMessage.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());
        
        PaymentEventMessage event = eventCaptor.getValue();
        assertThat(event.getEventId()).isNotNull().startsWith("evt_");
        assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_AUTHORIZED);
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getCorrelationId()).isEqualTo(payment.getPaymentId());
        assertThat(event.getTraceId()).isNotNull();
        
        PaymentEventMessage.PaymentEventPayload payload = event.getPayload();
        assertThat(payload.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(payload.getMerchantId()).isEqualTo(payment.getMerchantId().toString());
        assertThat(payload.getAmount()).isEqualByComparingTo(payment.getAmount());
        assertThat(payload.getCurrency()).isEqualTo(payment.getCurrency());
        assertThat(payload.getStatus()).isEqualTo(payment.getStatus().name());
        assertThat(payload.getPspTransactionId()).isEqualTo(payment.getPspTransactionId());
        assertThat(payload.getFraudScore()).isEqualByComparingTo(payment.getFraudScore());
        assertThat(payload.getThreeDsStatus()).isEqualTo(payment.getThreeDsStatus().name());
    }
    
    private Payment createTestPayment() {
        Payment payment = new Payment();
        payment.setPaymentId("pay_test123456789012345678");
        payment.setMerchantId(UUID.randomUUID());
        payment.setAmount(BigDecimal.valueOf(100.00));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCardTokenId(UUID.randomUUID());
        payment.setCardLastFour("1234");
        payment.setCardBrand(CardBrand.VISA);
        return payment;
    }
}
