package com.paymentgateway.settlement.service;

import com.paymentgateway.settlement.domain.Dispute;
import com.paymentgateway.settlement.domain.Payment;
import com.paymentgateway.settlement.repository.DisputeRepository;
import com.paymentgateway.settlement.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DisputeService {
    
    private static final Logger logger = LoggerFactory.getLogger(DisputeService.class);
    
    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    
    public DisputeService(DisputeRepository disputeRepository, PaymentRepository paymentRepository) {
        this.disputeRepository = disputeRepository;
        this.paymentRepository = paymentRepository;
    }
    
    /**
     * Create a dispute from a chargeback notification
     * Property 23: Chargeback Creates Dispute
     */
    @Transactional
    public Dispute createDisputeFromChargeback(String paymentId, String chargebackReference, 
                                              String reasonCode, String reason, 
                                              OffsetDateTime deadline) {
        // Find the original payment
        Payment payment = paymentRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        
        // Generate dispute ID
        String disputeId = "dis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        
        // Create dispute record
        Dispute dispute = new Dispute(
            disputeId,
            payment.getId(),
            payment.getMerchantId(),
            payment.getAmount(),
            payment.getCurrency(),
            reasonCode,
            reason
        );
        
        dispute.setChargebackReference(chargebackReference);
        dispute.setDeadline(deadline);
        dispute.setStatus("OPEN");
        
        dispute = disputeRepository.save(dispute);
        
        logger.info("Created dispute {} for payment {} with chargeback reference {}", 
                   disputeId, paymentId, chargebackReference);
        
        // TODO: Send notification to merchant
        notifyMerchant(dispute);
        
        return dispute;
    }
    
    /**
     * Submit evidence for a dispute
     */
    @Transactional
    public void submitEvidence(String disputeId, String evidence) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));
        
        if (!"OPEN".equals(dispute.getStatus())) {
            throw new IllegalStateException("Dispute is not in OPEN status");
        }
        
        dispute.setStatus("PENDING_EVIDENCE");
        dispute.setEvidenceSubmittedAt(OffsetDateTime.now());
        dispute.setUpdatedAt(OffsetDateTime.now());
        disputeRepository.save(dispute);
        
        // Forward evidence to acquirer (simulated)
        forwardEvidenceToAcquirer(dispute, evidence);
        
        logger.info("Submitted evidence for dispute {}", disputeId);
    }
    
    /**
     * Resolve a dispute
     */
    @Transactional
    public void resolveDispute(String disputeId, String resolution, boolean merchantWon) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));
        
        dispute.setStatus(merchantWon ? "WON" : "LOST");
        dispute.setResolution(resolution);
        dispute.setResolvedAt(OffsetDateTime.now());
        dispute.setUpdatedAt(OffsetDateTime.now());
        disputeRepository.save(dispute);
        
        // If merchant lost, adjust settlement records
        if (!merchantWon) {
            adjustSettlementForChargeback(dispute);
        }
        
        // Notify merchant of resolution
        notifyMerchantOfResolution(dispute);
        
        logger.info("Resolved dispute {} with status {}", disputeId, dispute.getStatus());
    }
    
    /**
     * Adjust settlement records for finalized chargeback
     */
    private void adjustSettlementForChargeback(Dispute dispute) {
        // In production, this would create a debit entry in the settlement system
        logger.info("Adjusting settlement for chargeback on payment {}, amount {}", 
                   dispute.getPaymentId(), dispute.getAmount());
        
        // TODO: Create settlement adjustment record
    }
    
    /**
     * Notify merchant about new dispute
     */
    private void notifyMerchant(Dispute dispute) {
        // In production, this would send email/webhook to merchant
        logger.info("Notifying merchant {} about dispute {}", 
                   dispute.getMerchantId(), dispute.getDisputeId());
    }
    
    /**
     * Notify merchant about dispute resolution
     */
    private void notifyMerchantOfResolution(Dispute dispute) {
        logger.info("Notifying merchant {} about dispute {} resolution: {}", 
                   dispute.getMerchantId(), dispute.getDisputeId(), dispute.getStatus());
    }
    
    /**
     * Forward evidence to acquirer
     */
    private void forwardEvidenceToAcquirer(Dispute dispute, String evidence) {
        // In production, this would submit evidence via acquirer API
        logger.info("Forwarding evidence for dispute {} to acquirer", dispute.getDisputeId());
    }
    
    /**
     * Get dispute by ID
     */
    public Dispute getDispute(String disputeId) {
        return disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));
    }
    
    /**
     * Get all disputes for a payment
     */
    public List<Dispute> getDisputesForPayment(UUID paymentId) {
        return disputeRepository.findByPaymentId(paymentId);
    }
    
    /**
     * Get all disputes for a merchant
     */
    public List<Dispute> getDisputesForMerchant(UUID merchantId) {
        return disputeRepository.findByMerchantId(merchantId);
    }
}
