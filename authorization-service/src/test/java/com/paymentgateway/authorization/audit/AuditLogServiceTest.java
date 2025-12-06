package com.paymentgateway.authorization.audit;

import com.paymentgateway.authorization.domain.PaymentEvent;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AuditLogService.
 * Tests log creation, integrity verification, and access control.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogServiceTest {
    
    @Autowired
    private AuditLogService auditLogService;
    
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    
    @Test
    void shouldCreateAuditLogWithBasicInfo() {
        // Given
        UUID paymentId = UUID.randomUUID();
        String eventType = "AUTHORIZATION";
        String eventStatus = "SUCCESS";
        String description = "Payment authorized successfully";
        String actor = "MERCHANT_001";
        
        // When
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, eventType, eventStatus, description, actor
        );
        
        // Then
        assertThat(event).isNotNull();
        assertThat(event.getId()).isNotNull();
        assertThat(event.getPaymentId()).isEqualTo(paymentId);
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getEventStatus()).isEqualTo(eventStatus);
        assertThat(event.getDescription()).isEqualTo(description);
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getCorrelationId()).isNotNull();
        assertThat(event.getErrorCode()).startsWith("HMAC:");
    }
    
    @Test
    void shouldCreateDetailedAuditLog() {
        // Given
        UUID paymentId = UUID.randomUUID();
        AuditLogEntry entry = AuditLogEntry.builder()
            .paymentId(paymentId)
            .eventType("CAPTURE")
            .eventStatus("SUCCESS")
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .description("Payment captured")
            .pspResponse("{\"status\": \"approved\"}")
            .gatewayResponse("{\"code\": \"200\"}")
            .processingTimeMs(150)
            .userAgent("Mozilla/5.0")
            .ipAddress("192.168.1.1")
            .build();
        
        // When
        PaymentEvent event = auditLogService.createDetailedAuditLog(entry);
        
        // Then
        assertThat(event).isNotNull();
        assertThat(event.getPaymentId()).isEqualTo(paymentId);
        assertThat(event.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(event.getCurrency()).isEqualTo("USD");
        assertThat(event.getProcessingTimeMs()).isEqualTo(150);
        assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.getIpAddress()).isEqualTo("192.168.1.1");
    }
    
    @Test
    void shouldRetrieveAuditLogsByPaymentId() {
        // Given
        UUID paymentId = UUID.randomUUID();
        
        auditLogService.createAuditLog(paymentId, "AUTHORIZATION", "SUCCESS", "Auth", "MERCHANT");
        auditLogService.createAuditLog(paymentId, "CAPTURE", "SUCCESS", "Capture", "MERCHANT");
        auditLogService.createAuditLog(paymentId, "REFUND", "PENDING", "Refund", "MERCHANT");
        
        paymentEventRepository.flush();
        
        // When
        List<PaymentEvent> logs = auditLogService.getAuditLogs(paymentId);
        
        // Then
        assertThat(logs).hasSize(3);
        assertThat(logs).extracting(PaymentEvent::getEventType)
            .containsExactlyInAnyOrder("AUTHORIZATION", "CAPTURE", "REFUND");
    }
    
    @Test
    void shouldVerifyIntegrityOfAuditLog() {
        // Given
        UUID paymentId = UUID.randomUUID();
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, "AUTHORIZATION", "SUCCESS", "Test", "MERCHANT"
        );
        
        paymentEventRepository.flush();
        
        // When
        PaymentEvent savedEvent = paymentEventRepository.findById(event.getId()).orElseThrow();
        boolean isValid = auditLogService.verifyIntegrity(savedEvent);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    void shouldDetectTamperedAuditLog() {
        // Given
        UUID paymentId = UUID.randomUUID();
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, "AUTHORIZATION", "SUCCESS", "Original description", "MERCHANT"
        );
        
        paymentEventRepository.flush();
        
        // When - tamper with the description
        PaymentEvent savedEvent = paymentEventRepository.findById(event.getId()).orElseThrow();
        savedEvent.setDescription("Tampered description");
        
        boolean isValid = auditLogService.verifyIntegrity(savedEvent);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void shouldRedactPANInDescription() {
        // Given
        UUID paymentId = UUID.randomUUID();
        String descriptionWithPAN = "Processing card 4532015112830366 for payment";
        
        // When
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, "AUTHORIZATION", "SUCCESS", descriptionWithPAN, "MERCHANT"
        );
        
        // Then
        assertThat(event.getDescription()).doesNotContain("4532015112830366");
        assertThat(event.getDescription()).contains("****0366");
    }
    
    @Test
    void shouldRedactCVVInDescription() {
        // Given
        UUID paymentId = UUID.randomUUID();
        String descriptionWithCVV = "Payment verification with CVV: 123 completed";
        
        // When
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, "AUTHORIZATION", "SUCCESS", descriptionWithCVV, "MERCHANT"
        );
        
        // Then
        assertThat(event.getDescription()).doesNotContain("CVV: 123");
        assertThat(event.getDescription()).contains("***");
    }
    
    @Test
    void shouldRedactPANInPspResponse() {
        // Given
        UUID paymentId = UUID.randomUUID();
        String pspResponseWithPAN = "{\"card_number\": \"4532015112830366\", \"status\": \"approved\"}";
        
        AuditLogEntry entry = AuditLogEntry.builder()
            .paymentId(paymentId)
            .eventType("AUTHORIZATION")
            .eventStatus("SUCCESS")
            .description("Payment processed")
            .pspResponse(pspResponseWithPAN)
            .build();
        
        // When
        PaymentEvent event = auditLogService.createDetailedAuditLog(entry);
        
        // Then
        assertThat(event.getPspResponse()).doesNotContain("4532015112830366");
        assertThat(event.getPspResponse()).contains("****0366");
    }
    
    @Test
    void shouldHandleNullDescription() {
        // Given
        UUID paymentId = UUID.randomUUID();
        
        // When
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, "AUTHORIZATION", "SUCCESS", null, "MERCHANT"
        );
        
        // Then
        assertThat(event).isNotNull();
        assertThat(event.getDescription()).isNull();
    }
    
    @Test
    void shouldHandleEmptyDescription() {
        // Given
        UUID paymentId = UUID.randomUUID();
        
        // When
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, "AUTHORIZATION", "SUCCESS", "", "MERCHANT"
        );
        
        // Then
        assertThat(event).isNotNull();
        assertThat(event.getDescription()).isEmpty();
    }
    
    @Test
    void shouldRedactMultiplePANsInSameLog() {
        // Given
        UUID paymentId = UUID.randomUUID();
        String descriptionWithMultiplePANs = "Transfer from 4532015112830366 to 5425233430109903";
        
        // When
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, "AUTHORIZATION", "SUCCESS", descriptionWithMultiplePANs, "MERCHANT"
        );
        
        // Then
        assertThat(event.getDescription()).doesNotContain("4532015112830366");
        assertThat(event.getDescription()).doesNotContain("5425233430109903");
        assertThat(event.getDescription()).contains("****0366");
        assertThat(event.getDescription()).contains("****9903");
    }
}
