package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.audit.AuditLogEntry;
import com.paymentgateway.authorization.audit.AuditLogService;
import com.paymentgateway.authorization.domain.PaymentEvent;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Feature: payment-acquiring-gateway, Property 15: Audit Log Immutability
 * 
 * For any audit log entry created, subsequent queries for that entry should return 
 * identical data - audit logs cannot be modified.
 * 
 * Validates: Requirements 8.1
 */
@JqwikSpringSupport
@SpringBootTest
@ActiveProfiles("test")
class AuditLogImmutabilityPropertyTest {
    
    @Autowired
    private AuditLogService auditLogService;
    
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    
    /**
     * Property: Audit logs are immutable - reading the same log multiple times returns identical data.
     */
    @Property(tries = 100)
    @Transactional
    void auditLogImmutability(
            @ForAll("paymentIds") UUID paymentId,
            @ForAll("eventTypes") String eventType,
            @ForAll("eventStatuses") String eventStatus,
            @ForAll("descriptions") String description,
            @ForAll("actors") String actor) {
        
        // Create an audit log entry
        PaymentEvent originalEvent = auditLogService.createAuditLog(
            paymentId, eventType, eventStatus, description, actor
        );
        
        // Flush to ensure it's persisted
        paymentEventRepository.flush();
        
        // Read the audit log multiple times
        PaymentEvent firstRead = paymentEventRepository.findById(originalEvent.getId()).orElseThrow();
        PaymentEvent secondRead = paymentEventRepository.findById(originalEvent.getId()).orElseThrow();
        PaymentEvent thirdRead = paymentEventRepository.findById(originalEvent.getId()).orElseThrow();
        
        // Verify all reads return identical data
        assertAuditLogEquals(originalEvent, firstRead);
        assertAuditLogEquals(originalEvent, secondRead);
        assertAuditLogEquals(originalEvent, thirdRead);
        assertAuditLogEquals(firstRead, secondRead);
        assertAuditLogEquals(secondRead, thirdRead);
    }
    
    /**
     * Property: Audit logs retrieved by payment ID are immutable across multiple queries.
     */
    @Property(tries = 100)
    @Transactional
    void auditLogListImmutability(
            @ForAll("paymentIds") UUID paymentId,
            @ForAll("eventTypes") String eventType1,
            @ForAll("eventTypes") String eventType2,
            @ForAll("eventStatuses") String eventStatus,
            @ForAll("descriptions") String description,
            @ForAll("actors") String actor) {
        
        // Create multiple audit log entries for the same payment
        PaymentEvent event1 = auditLogService.createAuditLog(
            paymentId, eventType1, eventStatus, description + " 1", actor
        );
        
        PaymentEvent event2 = auditLogService.createAuditLog(
            paymentId, eventType2, eventStatus, description + " 2", actor
        );
        
        paymentEventRepository.flush();
        
        // Query the audit logs multiple times
        List<PaymentEvent> firstQuery = auditLogService.getAuditLogs(paymentId);
        List<PaymentEvent> secondQuery = auditLogService.getAuditLogs(paymentId);
        List<PaymentEvent> thirdQuery = auditLogService.getAuditLogs(paymentId);
        
        // Verify all queries return the same number of logs
        assert firstQuery.size() == secondQuery.size();
        assert secondQuery.size() == thirdQuery.size();
        assert firstQuery.size() >= 2; // At least the two we created
        
        // Verify the content is identical across queries
        for (int i = 0; i < firstQuery.size(); i++) {
            assertAuditLogEquals(firstQuery.get(i), secondQuery.get(i));
            assertAuditLogEquals(secondQuery.get(i), thirdQuery.get(i));
        }
    }
    
    /**
     * Property: Audit log timestamps remain constant across reads (immutability).
     */
    @Property(tries = 100)
    @Transactional
    void auditLogTimestampImmutability(
            @ForAll("paymentIds") UUID paymentId,
            @ForAll("eventTypes") String eventType,
            @ForAll("eventStatuses") String eventStatus,
            @ForAll("descriptions") String description,
            @ForAll("actors") String actor) {
        
        // Create an audit log entry
        PaymentEvent originalEvent = auditLogService.createAuditLog(
            paymentId, eventType, eventStatus, description, actor
        );
        
        Instant originalTimestamp = originalEvent.getCreatedAt();
        UUID originalCorrelationId = originalEvent.getCorrelationId();
        
        paymentEventRepository.flush();
        
        // Wait a bit to ensure time has passed
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Read the audit log again
        PaymentEvent readEvent = paymentEventRepository.findById(originalEvent.getId()).orElseThrow();
        
        // Verify timestamp and correlation ID haven't changed
        assert originalTimestamp.equals(readEvent.getCreatedAt()) : 
            "Audit log timestamp changed from " + originalTimestamp + " to " + readEvent.getCreatedAt();
        
        assert originalCorrelationId.equals(readEvent.getCorrelationId()) :
            "Audit log correlation ID changed from " + originalCorrelationId + " to " + readEvent.getCorrelationId();
    }
    
    // Helper method to assert two audit logs are equal
    private void assertAuditLogEquals(PaymentEvent expected, PaymentEvent actual) {
        assert expected.getId().equals(actual.getId()) : 
            "ID mismatch: " + expected.getId() + " vs " + actual.getId();
        
        assert expected.getPaymentId().equals(actual.getPaymentId()) :
            "Payment ID mismatch: " + expected.getPaymentId() + " vs " + actual.getPaymentId();
        
        assert expected.getEventType().equals(actual.getEventType()) :
            "Event type mismatch: " + expected.getEventType() + " vs " + actual.getEventType();
        
        assert expected.getEventStatus().equals(actual.getEventStatus()) :
            "Event status mismatch: " + expected.getEventStatus() + " vs " + actual.getEventStatus();
        
        assert expected.getCreatedAt().equals(actual.getCreatedAt()) :
            "Created at mismatch: " + expected.getCreatedAt() + " vs " + actual.getCreatedAt();
        
        assert expected.getCorrelationId().equals(actual.getCorrelationId()) :
            "Correlation ID mismatch: " + expected.getCorrelationId() + " vs " + actual.getCorrelationId();
        
        // Compare descriptions (handling nulls)
        if (expected.getDescription() != null) {
            assert expected.getDescription().equals(actual.getDescription()) :
                "Description mismatch: " + expected.getDescription() + " vs " + actual.getDescription();
        } else {
            assert actual.getDescription() == null : "Description should be null but was: " + actual.getDescription();
        }
    }
    
    // Arbitraries for generating test data
    
    @Provide
    Arbitrary<UUID> paymentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> eventTypes() {
        return Arbitraries.of("AUTHORIZATION", "CAPTURE", "REFUND", "VOID", "FRAUD_CHECK", "3DS_AUTH");
    }
    
    @Provide
    Arbitrary<String> eventStatuses() {
        return Arbitraries.of("SUCCESS", "FAILED", "PENDING", "DECLINED");
    }
    
    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(' ', '.', ',')
            .ofMinLength(10)
            .ofMaxLength(100);
    }
    
    @Provide
    Arbitrary<String> actors() {
        return Arbitraries.of("MERCHANT_001", "MERCHANT_002", "SYSTEM", "ADMIN", "PSP_STRIPE", "PSP_ADYEN");
    }
}
