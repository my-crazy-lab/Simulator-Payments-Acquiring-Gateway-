package com.paymentgateway.authorization.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.authorization.domain.Merchant;
import com.paymentgateway.authorization.domain.Payment;
import com.paymentgateway.authorization.domain.WebhookDelivery;
import com.paymentgateway.authorization.event.PaymentEventMessage;
import com.paymentgateway.authorization.event.PaymentEventType;
import com.paymentgateway.authorization.repository.MerchantRepository;
import com.paymentgateway.authorization.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {
    
    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;
    
    @Mock
    private MerchantRepository merchantRepository;
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private WebhookService webhookService;
    
    private Merchant merchant;
    private Payment payment;
    private PaymentEventMessage eventMessage;
    
    @BeforeEach
    void setUp() {
        merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setMerchantId("MERCHANT_001");
        merchant.setWebhookUrl("https://merchant.example.com/webhook");
        merchant.setWebhookSecretHash("test-secret-key-12345");
        
        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setMerchantId(merchant.getId());
        
        eventMessage = new PaymentEventMessage();
        eventMessage.setEventId(UUID.randomUUID().toString());
        eventMessage.setEventType(PaymentEventType.PAYMENT_AUTHORIZED);
        eventMessage.setTimestamp(Instant.now());
    }
    
    @Test
    void testHmacSignatureGeneration() {
        // Given
        String payload = "{\"test\":\"data\"}";
        String secret = "my-secret-key";
        
        // When
        String signature = webhookService.generateHmacSignature(payload, secret);
        
        // Then
        assertThat(signature).isNotNull();
        assertThat(signature).isNotEmpty();
        assertThat(signature).matches("^[A-Za-z0-9+/]*={0,2}$"); // Valid Base64
    }
    
    @Test
    void testHmacSignatureVerification() {
        // Given
        String payload = "{\"test\":\"data\"}";
        String secret = "my-secret-key";
        String signature = webhookService.generateHmacSignature(payload, secret);
        
        // When
        boolean isValid = webhookService.verifyHmacSignature(payload, signature, secret);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    void testHmacSignatureVerificationFailsWithWrongSecret() {
        // Given
        String payload = "{\"test\":\"data\"}";
        String correctSecret = "correct-secret";
        String wrongSecret = "wrong-secret";
        String signature = webhookService.generateHmacSignature(payload, correctSecret);
        
        // When
        boolean isValid = webhookService.verifyHmacSignature(payload, signature, wrongSecret);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void testHmacSignatureVerificationFailsWithModifiedPayload() {
        // Given
        String originalPayload = "{\"test\":\"data\"}";
        String modifiedPayload = "{\"test\":\"modified\"}";
        String secret = "my-secret-key";
        String signature = webhookService.generateHmacSignature(originalPayload, secret);
        
        // When
        boolean isValid = webhookService.verifyHmacSignature(modifiedPayload, signature, secret);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void testSendWebhookCreatesDeliveryRecord() throws Exception {
        // Given
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"event\":\"test\"}");
        when(webhookDeliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> {
                    WebhookDelivery delivery = invocation.getArgument(0);
                    delivery.setId(UUID.randomUUID()); // Set ID as repository would
                    return delivery;
                });
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
        
        // When
        webhookService.sendWebhook(payment, eventMessage);
        
        // Give async operation time to complete
        Thread.sleep(200);
        
        // Then
        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(webhookDeliveryRepository, atLeastOnce()).save(captor.capture());
        
        WebhookDelivery savedDelivery = captor.getValue();
        assertThat(savedDelivery.getMerchantId()).isEqualTo(merchant.getId());
        assertThat(savedDelivery.getPaymentId()).isEqualTo(payment.getId());
        assertThat(savedDelivery.getWebhookUrl()).isEqualTo(merchant.getWebhookUrl());
        assertThat(savedDelivery.getSignature()).isNotNull();
    }
    
    @Test
    void testSendWebhookSkipsWhenNoWebhookUrlConfigured() throws Exception {
        // Given
        merchant.setWebhookUrl(null);
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));
        
        // When
        webhookService.sendWebhook(payment, eventMessage);
        
        // Give async operation time to complete
        Thread.sleep(100);
        
        // Then
        verify(webhookDeliveryRepository, never()).save(any());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }
    
    @Test
    void testSendWebhookSkipsWhenNoSecretConfigured() throws Exception {
        // Given
        merchant.setWebhookSecretHash(null);
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));
        
        // When
        webhookService.sendWebhook(payment, eventMessage);
        
        // Give async operation time to complete
        Thread.sleep(100);
        
        // Then
        verify(webhookDeliveryRepository, never()).save(any());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }
    
    @Test
    void testRetryLogicWithExponentialBackoff() throws Exception {
        // Given - Create a delivery that will fail
        WebhookDelivery delivery = new WebhookDelivery(
                merchant.getId(),
                payment.getId(),
                "PAYMENT_AUTHORIZED",
                merchant.getWebhookUrl(),
                "{\"test\":\"data\"}",
                "signature123"
        );
        delivery.setId(UUID.randomUUID());
        delivery.setNextRetryAt(Instant.now());
        
        when(webhookDeliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection timeout"));
        
        // When - Use reflection to call private method
        java.lang.reflect.Method method = WebhookService.class.getDeclaredMethod(
                "attemptDelivery", WebhookDelivery.class);
        method.setAccessible(true);
        method.invoke(webhookService, delivery);
        
        // Then - Verify exponential backoff is applied
        assertThat(delivery.getNextRetryAt()).isAfter(Instant.now().plusSeconds(50));
        assertThat(delivery.getNextRetryAt()).isBefore(Instant.now().plusSeconds(70));
    }
    
    @Test
    void testDeliveryTrackingRecordsHttpStatus() throws Exception {
        // Given
        WebhookDelivery delivery = new WebhookDelivery(
                merchant.getId(),
                payment.getId(),
                "PAYMENT_AUTHORIZED",
                merchant.getWebhookUrl(),
                "{\"test\":\"data\"}",
                "signature123"
        );
        delivery.setId(UUID.randomUUID()); // Set ID
        delivery.setNextRetryAt(Instant.now());
        
        when(webhookDeliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
        
        // When
        // Use reflection to call private method
        java.lang.reflect.Method method = WebhookService.class.getDeclaredMethod(
                "attemptDelivery", WebhookDelivery.class);
        method.setAccessible(true);
        method.invoke(webhookService, delivery);
        
        // Then
        assertThat(delivery.getHttpStatusCode()).isEqualTo(200);
        assertThat(delivery.getStatus()).isEqualTo("DELIVERED");
        assertThat(delivery.getDeliveredAt()).isNotNull();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
    }
    
    @Test
    void testDeliveryFailureHandling() throws Exception {
        // Given
        WebhookDelivery delivery = new WebhookDelivery(
                merchant.getId(),
                payment.getId(),
                "PAYMENT_AUTHORIZED",
                merchant.getWebhookUrl(),
                "{\"test\":\"data\"}",
                "signature123"
        );
        delivery.setId(UUID.randomUUID()); // Set ID
        delivery.setNextRetryAt(Instant.now());
        
        when(webhookDeliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection timeout"));
        
        // When
        java.lang.reflect.Method method = WebhookService.class.getDeclaredMethod(
                "attemptDelivery", WebhookDelivery.class);
        method.setAccessible(true);
        method.invoke(webhookService, delivery);
        
        // Then
        assertThat(delivery.getStatus()).isEqualTo("PENDING");
        assertThat(delivery.getErrorMessage()).contains("Connection timeout");
        assertThat(delivery.getNextRetryAt()).isAfter(Instant.now());
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
    }
    
    @Test
    void testDeliveryMarkedAsFailedAfterMaxAttempts() throws Exception {
        // Given
        WebhookDelivery delivery = new WebhookDelivery(
                merchant.getId(),
                payment.getId(),
                "PAYMENT_AUTHORIZED",
                merchant.getWebhookUrl(),
                "{\"test\":\"data\"}",
                "signature123"
        );
        delivery.setId(UUID.randomUUID()); // Set ID
        delivery.setNextRetryAt(Instant.now());
        delivery.setAttemptCount(9); // One more attempt will reach max (10)
        
        when(webhookDeliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection timeout"));
        
        // When
        java.lang.reflect.Method method = WebhookService.class.getDeclaredMethod(
                "attemptDelivery", WebhookDelivery.class);
        method.setAccessible(true);
        method.invoke(webhookService, delivery);
        
        // Then
        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        assertThat(delivery.getAttemptCount()).isEqualTo(10);
        assertThat(delivery.hasReachedMaxAttempts()).isTrue();
    }
}
