package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.RefundRequest;
import com.paymentgateway.authorization.dto.RefundResponse;
import com.paymentgateway.authorization.psp.PSPClient;
import com.paymentgateway.authorization.psp.PSPRefundResponse;
import com.paymentgateway.authorization.psp.PSPRoutingService;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import com.paymentgateway.authorization.repository.PaymentRepository;
import com.paymentgateway.authorization.repository.RefundRepository;
import com.paymentgateway.authorization.service.RefundService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: payment-acquiring-gateway, Property 14: Refund Amount Constraint
 * 
 * For any refund request, the sum of all refunds for a transaction plus the new refund amount
 * must not exceed the original transaction amount.
 * 
 * Validates: Requirements 7.2
 */
class RefundAmountConstraintPropertyTest {
    
    /**
     * Property: For any payment and refund amount that together do not exceed the original amount,
     * the refund should be accepted.
     */
    @Property(tries = 100)
    void refundWithinLimitShouldBeAccepted(
            @ForAll @BigRange(min = "100.00", max = "10000.00") BigDecimal originalAmount,
            @ForAll @IntRange(min = 0, max = 100) int refundPercentage) {
        
        // Calculate refund amount as percentage of original (ensuring it's within limit)
        BigDecimal refundAmount = originalAmount
            .multiply(BigDecimal.valueOf(refundPercentage))
            .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
        
        // Skip if refund amount is zero
        if (refundAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        
        // Create a captured payment
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        // Create mocks
        RefundRepository refundRepository = mock(RefundRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentEventRepository paymentEventRepository = mock(PaymentEventRepository.class);
        PSPRoutingService pspRoutingService = mock(PSPRoutingService.class);
        PSPClient pspClient = mock(PSPClient.class);
        
        // Mock repository responses
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO); // No previous refunds
        when(refundRepository.save(any(Refund.class)))
            .thenAnswer(invocation -> {
                Refund refund = invocation.getArgument(0);
                refund.setId(UUID.randomUUID());
                return refund;
            });
        when(pspRoutingService.selectPSP(any(UUID.class)))
            .thenReturn(pspClient);
        
        // Mock successful PSP refund
        PSPRefundResponse pspResponse = new PSPRefundResponse();
        pspResponse.setSuccess(true);
        pspResponse.setPspRefundId("psp_ref_" + UUID.randomUUID());
        pspResponse.setRefundedAmount(refundAmount);
        when(pspClient.refund(any(), any(), any()))
            .thenReturn(pspResponse);
        
        // Create refund request
        RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
        
        // Process refund - should succeed
        com.paymentgateway.authorization.event.PaymentEventPublisher eventPublisher = 
            mock(com.paymentgateway.authorization.event.PaymentEventPublisher.class);
        RefundService service = new RefundService(refundRepository, paymentRepository, 
            paymentEventRepository, pspRoutingService, eventPublisher);
        RefundResponse response = service.processRefund(request, merchantId);
        
        // Verify refund was accepted
        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo(refundAmount);
        assertThat(response.getStatus()).isIn(RefundStatus.COMPLETED, RefundStatus.PENDING);
    }
    
    /**
     * Property: For any payment with existing refunds, attempting to refund more than the remaining
     * balance should be rejected.
     */
    @Property(tries = 100)
    void refundExceedingLimitShouldBeRejected(
            @ForAll @BigRange(min = "100.00", max = "10000.00") BigDecimal originalAmount,
            @ForAll @IntRange(min = 50, max = 90) int alreadyRefundedPercentage,
            @ForAll @IntRange(min = 20, max = 60) int additionalRefundPercentage) {
        
        // Calculate amounts
        BigDecimal alreadyRefunded = originalAmount
            .multiply(BigDecimal.valueOf(alreadyRefundedPercentage))
            .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
        
        BigDecimal additionalRefund = originalAmount
            .multiply(BigDecimal.valueOf(additionalRefundPercentage))
            .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
        
        // Ensure this would exceed the limit
        BigDecimal totalRefund = alreadyRefunded.add(additionalRefund);
        if (totalRefund.compareTo(originalAmount) <= 0) {
            return; // Skip this case as it doesn't test the constraint
        }
        
        // Create a captured payment
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        // Create mocks
        RefundRepository refundRepository = mock(RefundRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentEventRepository paymentEventRepository = mock(PaymentEventRepository.class);
        PSPRoutingService pspRoutingService = mock(PSPRoutingService.class);
        
        // Mock repository responses
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(alreadyRefunded); // Previous refunds exist
        
        // Create refund request
        RefundRequest request = new RefundRequest(payment.getPaymentId(), additionalRefund);
        
        // Process refund - should fail with IllegalArgumentException
        com.paymentgateway.authorization.event.PaymentEventPublisher eventPublisher = 
            mock(com.paymentgateway.authorization.event.PaymentEventPublisher.class);
        RefundService service = new RefundService(refundRepository, paymentRepository, 
            paymentEventRepository, pspRoutingService, eventPublisher);
        assertThatThrownBy(() -> service.processRefund(request, merchantId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds available balance");
    }
    
    /**
     * Property: For any payment, the sum of all completed refunds should never exceed
     * the original payment amount.
     */
    @Property(tries = 100)
    void totalRefundsShouldNeverExceedOriginalAmount(
            @ForAll @BigRange(min = "100.00", max = "10000.00") BigDecimal originalAmount,
            @ForAll("validRefundSequence") List<Integer> refundPercentages) {
        
        // Create a captured payment
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        // Create mocks
        RefundRepository refundRepository = mock(RefundRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentEventRepository paymentEventRepository = mock(PaymentEventRepository.class);
        PSPRoutingService pspRoutingService = mock(PSPRoutingService.class);
        PSPClient pspClient = mock(PSPClient.class);
        
        BigDecimal totalRefunded = BigDecimal.ZERO;
        
        for (Integer percentage : refundPercentages) {
            BigDecimal refundAmount = originalAmount
                .multiply(BigDecimal.valueOf(percentage))
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
            
            if (refundAmount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            
            BigDecimal potentialTotal = totalRefunded.add(refundAmount);
            
            // Mock repository responses
            when(paymentRepository.findByPaymentId(payment.getPaymentId()))
                .thenReturn(Optional.of(payment));
            
            BigDecimal currentRefunded = totalRefunded;
            when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
                .thenReturn(currentRefunded);
            
            if (potentialTotal.compareTo(originalAmount) <= 0) {
                // Should succeed
                when(refundRepository.save(any(Refund.class)))
                    .thenAnswer(invocation -> {
                        Refund refund = invocation.getArgument(0);
                        refund.setId(UUID.randomUUID());
                        return refund;
                    });
                when(pspRoutingService.selectPSP(any(UUID.class)))
                    .thenReturn(pspClient);
                
                PSPRefundResponse pspResponse = new PSPRefundResponse();
                pspResponse.setSuccess(true);
                pspResponse.setPspRefundId("psp_ref_" + UUID.randomUUID());
                when(pspClient.refund(any(), any(), any()))
                    .thenReturn(pspResponse);
                
                RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
                com.paymentgateway.authorization.event.PaymentEventPublisher eventPublisher = 
                    mock(com.paymentgateway.authorization.event.PaymentEventPublisher.class);
                RefundService service = new RefundService(refundRepository, paymentRepository, 
                    paymentEventRepository, pspRoutingService, eventPublisher);
                RefundResponse response = service.processRefund(request, merchantId);
                
                assertThat(response).isNotNull();
                totalRefunded = totalRefunded.add(refundAmount);
            } else {
                // Should fail
                RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
                com.paymentgateway.authorization.event.PaymentEventPublisher eventPublisher = 
                    mock(com.paymentgateway.authorization.event.PaymentEventPublisher.class);
                RefundService service = new RefundService(refundRepository, paymentRepository, 
                    paymentEventRepository, pspRoutingService, eventPublisher);
                assertThatThrownBy(() -> service.processRefund(request, merchantId))
                    .isInstanceOf(IllegalArgumentException.class);
                break; // Stop after first rejection
            }
        }
        
        // Verify total refunded never exceeds original
        assertThat(totalRefunded).isLessThanOrEqualTo(originalAmount);
    }
    
    @Provide
    Arbitrary<List<Integer>> validRefundSequence() {
        return Arbitraries.integers().between(10, 40)
            .list().ofMinSize(1).ofMaxSize(5);
    }
    
    private Payment createCapturedPayment(BigDecimal amount) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setPaymentId("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        payment.setMerchantId(UUID.randomUUID());
        payment.setAmount(amount);
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setPspTransactionId("psp_" + UUID.randomUUID());
        payment.setCreatedAt(Instant.now());
        payment.setCapturedAt(Instant.now());
        return payment;
    }
}
