package com.paymentgateway.authorization.service;

import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.RefundRequest;
import com.paymentgateway.authorization.dto.RefundResponse;
import com.paymentgateway.authorization.psp.PSPClient;
import com.paymentgateway.authorization.psp.PSPRefundResponse;
import com.paymentgateway.authorization.psp.PSPRoutingService;
import com.paymentgateway.authorization.repository.PaymentRepository;
import com.paymentgateway.authorization.repository.RefundRepository;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class RefundService {
    
    private static final Logger logger = LoggerFactory.getLogger(RefundService.class);
    
    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PSPRoutingService pspRoutingService;
    
    public RefundService(RefundRepository refundRepository,
                        PaymentRepository paymentRepository,
                        PaymentEventRepository paymentEventRepository,
                        PSPRoutingService pspRoutingService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.pspRoutingService = pspRoutingService;
    }
    
    @Transactional
    public RefundResponse processRefund(RefundRequest request, UUID merchantId) {
        logger.info("Processing refund for payment: {}, amount: {}", request.getPaymentId(), request.getAmount());
        
        // 1. Validate that the original transaction exists and is refundable
        Payment payment = paymentRepository.findByPaymentId(request.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + request.getPaymentId()));
        
        // Verify merchant owns this payment
        if (!payment.getMerchantId().equals(merchantId)) {
            throw new IllegalArgumentException("Payment does not belong to merchant");
        }
        
        // Verify payment is in a refundable state
        if (!isRefundable(payment)) {
            throw new IllegalStateException("Payment is not in a refundable state: " + payment.getStatus());
        }
        
        // 2. Validate refund amount constraint
        validateRefundAmount(payment, request.getAmount());
        
        // 3. Create refund record
        Refund refund = createRefund(payment, request);
        refund = refundRepository.save(refund);
        
        // Log refund creation event
        logRefundEvent(refund, "REFUND_CREATED", "Refund created");
        
        try {
            // 4. Submit refund to PSP
            PSPClient pspClient = pspRoutingService.selectPSP(payment.getMerchantId());
            PSPRefundResponse pspResponse = pspClient.refund(
                payment.getPspTransactionId(),
                request.getAmount(),
                payment.getCurrency()
            );
            
            // 5. Update refund with PSP response
            if (pspResponse.isSuccess()) {
                refund.setStatus(RefundStatus.COMPLETED);
                refund.setPspRefundId(pspResponse.getPspRefundId());
                refund.setProcessedAt(Instant.now());
                
                // 6. Update original payment record
                updatePaymentWithRefund(payment, refund);
                
                logRefundEvent(refund, "REFUND_COMPLETED", "Refund completed successfully");
                logger.info("Refund completed successfully: {}", refund.getRefundId());
            } else {
                refund.setStatus(RefundStatus.FAILED);
                refund.setErrorCode(pspResponse.getErrorCode());
                refund.setErrorMessage(pspResponse.getErrorMessage());
                
                logRefundEvent(refund, "REFUND_FAILED", "Refund failed: " + pspResponse.getErrorMessage());
                logger.error("Refund failed: {}, error: {}", refund.getRefundId(), pspResponse.getErrorMessage());
            }
            
            refund = refundRepository.save(refund);
            
        } catch (Exception e) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setErrorCode("INTERNAL_ERROR");
            refund.setErrorMessage(e.getMessage());
            refund = refundRepository.save(refund);
            
            logRefundEvent(refund, "REFUND_FAILED", "Refund failed with exception: " + e.getMessage());
            logger.error("Refund processing failed: {}", refund.getRefundId(), e);
        }
        
        return mapToResponse(refund, payment);
    }
    
    private boolean isRefundable(Payment payment) {
        return payment.getStatus() == PaymentStatus.CAPTURED ||
               payment.getStatus() == PaymentStatus.SETTLED;
    }
    
    private void validateRefundAmount(Payment payment, BigDecimal refundAmount) {
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        
        // Calculate total refunded amount (including pending and completed refunds)
        List<RefundStatus> countableStatuses = Arrays.asList(
            RefundStatus.PENDING,
            RefundStatus.PROCESSING,
            RefundStatus.COMPLETED
        );
        
        BigDecimal totalRefunded = refundRepository.sumRefundedAmountByPaymentIdAndStatuses(
            payment.getId(),
            countableStatuses
        );
        
        BigDecimal totalAfterRefund = totalRefunded.add(refundAmount);
        
        if (totalAfterRefund.compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException(
                String.format("Refund amount exceeds available balance. Original: %s, Already refunded: %s, Requested: %s",
                    payment.getAmount(), totalRefunded, refundAmount)
            );
        }
    }
    
    private Refund createRefund(Payment payment, RefundRequest request) {
        Refund refund = new Refund();
        refund.setRefundId("ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        refund.setPaymentId(payment.getId());
        refund.setMerchantId(payment.getMerchantId());
        refund.setAmount(request.getAmount());
        refund.setCurrency(payment.getCurrency());
        refund.setReason(request.getReason());
        refund.setStatus(RefundStatus.PENDING);
        refund.setPspTransactionId(payment.getPspTransactionId());
        return refund;
    }
    
    private void updatePaymentWithRefund(Payment payment, Refund refund) {
        // Calculate total refunded amount
        List<RefundStatus> completedStatuses = Arrays.asList(RefundStatus.COMPLETED);
        BigDecimal totalRefunded = refundRepository.sumRefundedAmountByPaymentIdAndStatuses(
            payment.getId(),
            completedStatuses
        );
        
        // If fully refunded, update payment status
        if (totalRefunded.compareTo(payment.getAmount()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            logger.info("Payment fully refunded: {}", payment.getPaymentId());
        }
    }
    
    private void logRefundEvent(Refund refund, String eventType, String description) {
        PaymentEvent event = new PaymentEvent();
        event.setPaymentId(refund.getPaymentId());
        event.setEventType(eventType);
        event.setEventStatus(refund.getStatus().toString());
        event.setAmount(refund.getAmount());
        event.setCurrency(refund.getCurrency());
        event.setDescription(description);
        event.setGatewayResponse(String.format("{\"refund_id\":\"%s\",\"status\":\"%s\"}",
            refund.getRefundId(), refund.getStatus()));
        paymentEventRepository.save(event);
    }
    
    private RefundResponse mapToResponse(Refund refund, Payment payment) {
        RefundResponse response = new RefundResponse();
        response.setRefundId(refund.getRefundId());
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(refund.getStatus());
        response.setAmount(refund.getAmount());
        response.setCurrency(refund.getCurrency());
        response.setReason(refund.getReason());
        response.setCreatedAt(refund.getCreatedAt());
        response.setProcessedAt(refund.getProcessedAt());
        response.setErrorCode(refund.getErrorCode());
        response.setErrorMessage(refund.getErrorMessage());
        return response;
    }
    
    public RefundResponse getRefund(String refundId) {
        Refund refund = refundRepository.findByRefundId(refundId)
            .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
        
        Payment payment = paymentRepository.findById(refund.getPaymentId())
            .orElseThrow(() -> new IllegalStateException("Payment not found for refund"));
        
        return mapToResponse(refund, payment);
    }
}
