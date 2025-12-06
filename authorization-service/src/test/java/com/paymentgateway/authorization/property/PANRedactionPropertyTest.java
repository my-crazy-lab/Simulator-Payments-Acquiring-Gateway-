package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.audit.AuditLogEntry;
import com.paymentgateway.authorization.audit.AuditLogService;
import com.paymentgateway.authorization.domain.PaymentEvent;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import net.jqwik.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Feature: payment-acquiring-gateway, Property 16: PAN Redaction in Logs
 * 
 * For any audit log entry, the log content should not contain raw PAN or CVV data - 
 * only masked or tokenized values should appear.
 * 
 * Validates: Requirements 8.3
 */
@SpringBootTest
@ActiveProfiles("test")
class PANRedactionPropertyTest {
    
    @Autowired
    private AuditLogService auditLogService;
    
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    
    // Pattern to detect unredacted PAN (13-19 consecutive digits)
    private static final Pattern UNREDACTED_PAN_PATTERN = Pattern.compile("\\b\\d{13,19}\\b");
    
    // Pattern to detect unredacted CVV (3-4 digits in CVV context)
    private static final Pattern UNREDACTED_CVV_PATTERN = Pattern.compile("(cvv|cvc|security_code)[\"']?\\s*[:=]\\s*[\"']?\\d{3,4}", Pattern.CASE_INSENSITIVE);
    
    /**
     * Property: PAN in description is redacted in audit logs.
     */
    @Property(tries = 100)
    @Transactional
    void panRedactedInDescription(
            @ForAll("paymentIds") UUID paymentId,
            @ForAll("eventTypes") String eventType,
            @ForAll("eventStatuses") String eventStatus,
            @ForAll("validPANs") String pan,
            @ForAll("actors") String actor) {
        
        // Create description containing PAN
        String descriptionWithPAN = String.format("Processing payment with card %s for customer", pan);
        
        // Create audit log
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, eventType, eventStatus, descriptionWithPAN, actor
        );
        
        paymentEventRepository.flush();
        
        // Retrieve the audit log
        PaymentEvent savedEvent = paymentEventRepository.findById(event.getId()).orElseThrow();
        
        // Verify PAN is not present in raw form
        String description = savedEvent.getDescription();
        assert description != null : "Description should not be null";
        assert !description.contains(pan) : 
            "Raw PAN found in audit log description: " + description;
        
        // Verify no unredacted PAN pattern exists
        assert !UNREDACTED_PAN_PATTERN.matcher(description).find() :
            "Unredacted PAN pattern found in description: " + description;
        
        // Verify the description contains masked version (last 4 digits)
        String lastFour = pan.substring(pan.length() - 4);
        assert description.contains("****" + lastFour) :
            "Masked PAN not found in description. Expected ****" + lastFour + " in: " + description;
    }
    
    /**
     * Property: CVV in description is redacted in audit logs.
     */
    @Property(tries = 100)
    @Transactional
    void cvvRedactedInDescription(
            @ForAll("paymentIds") UUID paymentId,
            @ForAll("eventTypes") String eventType,
            @ForAll("eventStatuses") String eventStatus,
            @ForAll("cvvCodes") String cvv,
            @ForAll("actors") String actor) {
        
        // Create description containing CVV
        String descriptionWithCVV = String.format("Payment verification with CVV: %s completed", cvv);
        
        // Create audit log
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, eventType, eventStatus, descriptionWithCVV, actor
        );
        
        paymentEventRepository.flush();
        
        // Retrieve the audit log
        PaymentEvent savedEvent = paymentEventRepository.findById(event.getId()).orElseThrow();
        
        // Verify CVV is not present in raw form
        String description = savedEvent.getDescription();
        assert description != null : "Description should not be null";
        assert !description.contains("CVV: " + cvv) :
            "Raw CVV found in audit log description: " + description;
        
        // Verify CVV is redacted
        assert description.contains("***") :
            "CVV not redacted in description: " + description;
    }
    
    /**
     * Property: PAN in PSP response is redacted in audit logs.
     */
    @Property(tries = 100)
    @Transactional
    void panRedactedInPspResponse(
            @ForAll("paymentIds") UUID paymentId,
            @ForAll("eventTypes") String eventType,
            @ForAll("eventStatuses") String eventStatus,
            @ForAll("validPANs") String pan) {
        
        // Create PSP response containing PAN
        String pspResponseWithPAN = String.format("{\"card_number\": \"%s\", \"status\": \"approved\"}", pan);
        
        // Create detailed audit log entry
        AuditLogEntry entry = AuditLogEntry.builder()
            .paymentId(paymentId)
            .eventType(eventType)
            .eventStatus(eventStatus)
            .description("Payment processed")
            .pspResponse(pspResponseWithPAN)
            .build();
        
        PaymentEvent event = auditLogService.createDetailedAuditLog(entry);
        paymentEventRepository.flush();
        
        // Retrieve the audit log
        PaymentEvent savedEvent = paymentEventRepository.findById(event.getId()).orElseThrow();
        
        // Verify PAN is not present in raw form in PSP response
        String pspResponse = savedEvent.getPspResponse();
        if (pspResponse != null) {
            assert !pspResponse.contains(pan) :
                "Raw PAN found in PSP response: " + pspResponse;
            
            // Verify no unredacted PAN pattern exists
            assert !UNREDACTED_PAN_PATTERN.matcher(pspResponse).find() :
                "Unredacted PAN pattern found in PSP response: " + pspResponse;
        }
    }
    
    /**
     * Property: Multiple PANs in the same log entry are all redacted.
     */
    @Property(tries = 100)
    @Transactional
    void multiplePANsRedacted(
            @ForAll("paymentIds") UUID paymentId,
            @ForAll("eventTypes") String eventType,
            @ForAll("eventStatuses") String eventStatus,
            @ForAll("validPANs") String pan1,
            @ForAll("validPANs") String pan2,
            @ForAll("actors") String actor) {
        
        // Create description containing multiple PANs
        String descriptionWithMultiplePANs = String.format(
            "Transfer from card %s to card %s", pan1, pan2
        );
        
        // Create audit log
        PaymentEvent event = auditLogService.createAuditLog(
            paymentId, eventType, eventStatus, descriptionWithMultiplePANs, actor
        );
        
        paymentEventRepository.flush();
        
        // Retrieve the audit log
        PaymentEvent savedEvent = paymentEventRepository.findById(event.getId()).orElseThrow();
        
        // Verify neither PAN is present in raw form
        String description = savedEvent.getDescription();
        assert description != null : "Description should not be null";
        assert !description.contains(pan1) :
            "First raw PAN found in audit log description: " + description;
        assert !description.contains(pan2) :
            "Second raw PAN found in audit log description: " + description;
        
        // Verify no unredacted PAN patterns exist
        assert !UNREDACTED_PAN_PATTERN.matcher(description).find() :
            "Unredacted PAN pattern found in description: " + description;
    }
    
    /**
     * Property: Error messages containing PAN are redacted.
     */
    @Property(tries = 100)
    @Transactional
    void panRedactedInErrorMessages(
            @ForAll("paymentIds") UUID paymentId,
            @ForAll("eventTypes") String eventType,
            @ForAll("validPANs") String pan) {
        
        // Create error message containing PAN
        String errorMessageWithPAN = String.format("Invalid card number %s - failed Luhn check", pan);
        
        // Create detailed audit log entry
        AuditLogEntry entry = AuditLogEntry.builder()
            .paymentId(paymentId)
            .eventType(eventType)
            .eventStatus("FAILED")
            .description("Payment validation failed")
            .errorMessage(errorMessageWithPAN)
            .build();
        
        PaymentEvent event = auditLogService.createDetailedAuditLog(entry);
        paymentEventRepository.flush();
        
        // Retrieve the audit log
        PaymentEvent savedEvent = paymentEventRepository.findById(event.getId()).orElseThrow();
        
        // Verify PAN is not present in raw form in error message
        String errorMessage = savedEvent.getErrorMessage();
        if (errorMessage != null) {
            assert !errorMessage.contains(pan) :
                "Raw PAN found in error message: " + errorMessage;
            
            // Verify no unredacted PAN pattern exists
            assert !UNREDACTED_PAN_PATTERN.matcher(errorMessage).find() :
                "Unredacted PAN pattern found in error message: " + errorMessage;
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
    Arbitrary<String> actors() {
        return Arbitraries.of("MERCHANT_001", "MERCHANT_002", "SYSTEM", "ADMIN");
    }
    
    @Provide
    Arbitrary<String> validPANs() {
        // Generate valid test card numbers (16 digits)
        return Arbitraries.of(
            "4532015112830366", // Visa
            "5425233430109903", // Mastercard
            "374245455400126",  // Amex (15 digits)
            "6011000991001201", // Discover
            "3566002020360505"  // JCB
        );
    }
    
    @Provide
    Arbitrary<String> cvvCodes() {
        // Generate 3-digit CVV codes
        return Arbitraries.integers()
            .between(100, 999)
            .map(String::valueOf);
    }
}
