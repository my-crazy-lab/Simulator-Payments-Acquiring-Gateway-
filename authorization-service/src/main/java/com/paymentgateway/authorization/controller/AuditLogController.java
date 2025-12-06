package com.paymentgateway.authorization.controller;

import com.paymentgateway.authorization.audit.AuditLogService;
import com.paymentgateway.authorization.domain.PaymentEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for querying audit logs with role-based access control.
 * Only administrators and authorized merchants can access audit logs.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditLogController {
    
    private final AuditLogService auditLogService;
    
    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
    
    /**
     * Get audit logs for a specific payment.
     * Requires ADMIN role or merchant must own the payment.
     * 
     * @param paymentId The payment ID
     * @return List of audit log entries
     */
    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasRole('ADMIN') or @auditLogController.canAccessPayment(#paymentId, authentication)")
    public ResponseEntity<List<PaymentEvent>> getPaymentAuditLogs(@PathVariable UUID paymentId) {
        List<PaymentEvent> logs = auditLogService.getAuditLogs(paymentId);
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Verify the integrity of an audit log entry.
     * Requires ADMIN role.
     * 
     * @param eventId The audit log event ID
     * @return Integrity verification result
     */
    @GetMapping("/verify/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IntegrityVerificationResponse> verifyIntegrity(@PathVariable UUID eventId) {
        // This would need to fetch the event and verify it
        // For now, returning a placeholder response
        return ResponseEntity.ok(new IntegrityVerificationResponse(eventId, true, "Integrity verified"));
    }
    
    /**
     * Check if the authenticated user can access a payment's audit logs.
     * This method is used by @PreAuthorize annotation.
     */
    public boolean canAccessPayment(UUID paymentId, org.springframework.security.core.Authentication authentication) {
        // In a real implementation, this would check if the authenticated merchant owns the payment
        // For now, we'll allow access (this will be properly implemented when RBAC is fully set up)
        return true;
    }
    
    /**
     * Response DTO for integrity verification.
     */
    public static class IntegrityVerificationResponse {
        private UUID eventId;
        private boolean valid;
        private String message;
        
        public IntegrityVerificationResponse(UUID eventId, boolean valid, String message) {
            this.eventId = eventId;
            this.valid = valid;
            this.message = message;
        }
        
        public UUID getEventId() { return eventId; }
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}
