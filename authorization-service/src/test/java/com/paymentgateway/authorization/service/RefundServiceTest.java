package com.paymentgateway.authorization.service;

import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.RefundRequest;
import com.paymentgateway.authorization.dto.RefundResponse;
import com.paymentgateway.authorization.psp.PSPClient;
import com.paymentgateway.authorization.psp.PSPRefundResponse;
import com.paymentgateway.authorization.psp.PSPRoutingService;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import com.paymentgateway.authorization.repository.PaymentRepository;
import com.paymentgateway.authorization.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class RefundServiceTest {
    
    @Mock
    private RefundRepository refundRepository;
    
    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private PaymentEventRepository paymentEventRepository;
    
    @Mock
    private PSPRoutingService pspRoutingService;
    
    @Mock
    private PSPClient pspClient;
    
    @Mock
    private com.paymentgateway.authorization.event.PaymentEventPublisher eventPublisher;
    
    private RefundService refundService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        refundService = new RefundService(
            refundRepository,
            paymentRepository,
            paymentEventRepository,
            pspRoutingService,
            eventPublisher
        );
    }
    
    @Test
    void shouldProcessPartialRefundSuccessfully() {
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal refundAmount = new BigDecimal("30.00");
        
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO);
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
        pspResponse.setPspRefundId("psp_ref_123");
        pspResponse.setRefundedAmount(refundAmount);
        when(pspClient.refund(any(), any(), any()))
            .thenReturn(pspResponse);
        
        RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount, "Customer request");
        
        // When
        RefundResponse response = refundService.processRefund(request, merchantId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRefundId()).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(response.getAmount()).isEqualByComparingTo(refundAmount);
        assertThat(response.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(response.getReason()).isEqualTo("Customer request");
        
        verify(refundRepository, times(2)).save(any(Refund.class));
        verify(pspClient).refund(payment.getPspTransactionId(), refundAmount, payment.getCurrency());
        verify(paymentEventRepository, times(2)).save(any(PaymentEvent.class));
    }
    
    @Test
    void shouldProcessFullRefundSuccessfully() {
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO)
            .thenReturn(originalAmount); // After refund
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
        pspResponse.setPspRefundId("psp_ref_123");
        pspResponse.setRefundedAmount(originalAmount);
        when(pspClient.refund(any(), any(), any()))
            .thenReturn(pspResponse);
        
        RefundRequest request = new RefundRequest(payment.getPaymentId(), originalAmount);
        
        // When
        RefundResponse response = refundService.processRefund(request, merchantId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo(originalAmount);
        assertThat(response.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        
        // Verify payment status updated to REFUNDED
        verify(paymentRepository).save(argThat(p -> 
            p.getStatus() == PaymentStatus.REFUNDED
        ));
    }
    
    @Test
    void shouldHandleRefundFailureFromPSP() {
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal refundAmount = new BigDecimal("50.00");
        
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any(Refund.class)))
            .thenAnswer(invocation -> {
                Refund refund = invocation.getArgument(0);
                refund.setId(UUID.randomUUID());
                return refund;
            });
        when(pspRoutingService.selectPSP(any(UUID.class)))
            .thenReturn(pspClient);
        
        PSPRefundResponse pspResponse = new PSPRefundResponse();
        pspResponse.setSuccess(false);
        pspResponse.setErrorCode("INSUFFICIENT_FUNDS");
        pspResponse.setErrorMessage("Insufficient funds for refund");
        when(pspClient.refund(any(), any(), any()))
            .thenReturn(pspResponse);
        
        RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
        
        // When
        RefundResponse response = refundService.processRefund(request, merchantId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.getErrorMessage()).isEqualTo("Insufficient funds for refund");
        
        verify(refundRepository, times(2)).save(any(Refund.class));
        verify(paymentEventRepository, times(2)).save(any(PaymentEvent.class));
    }
    
    @Test
    void shouldRejectRefundForNonExistentPayment() {
        // Given
        String nonExistentPaymentId = "pay_nonexistent";
        BigDecimal refundAmount = new BigDecimal("50.00");
        UUID merchantId = UUID.randomUUID();
        
        when(paymentRepository.findByPaymentId(nonExistentPaymentId))
            .thenReturn(Optional.empty());
        
        RefundRequest request = new RefundRequest(nonExistentPaymentId, refundAmount);
        
        // When/Then
        assertThatThrownBy(() -> refundService.processRefund(request, merchantId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Payment not found");
    }
    
    @Test
    void shouldRejectRefundForWrongMerchant() {
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal refundAmount = new BigDecimal("50.00");
        
        Payment payment = createCapturedPayment(originalAmount);
        UUID wrongMerchantId = UUID.randomUUID();
        
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        
        RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
        
        // When/Then
        assertThatThrownBy(() -> refundService.processRefund(request, wrongMerchantId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong to merchant");
    }
    
    @Test
    void shouldRejectRefundForNonRefundablePaymentStatus() {
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal refundAmount = new BigDecimal("50.00");
        
        Payment payment = createCapturedPayment(originalAmount);
        payment.setStatus(PaymentStatus.PENDING); // Not refundable
        UUID merchantId = payment.getMerchantId();
        
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        
        RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
        
        // When/Then
        assertThatThrownBy(() -> refundService.processRefund(request, merchantId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not in a refundable state");
    }
    
    @Test
    void shouldRejectRefundWithZeroAmount() {
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal refundAmount = BigDecimal.ZERO;
        
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        
        RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
        
        // When/Then
        assertThatThrownBy(() -> refundService.processRefund(request, merchantId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be greater than zero");
    }
    
    @Test
    void shouldRejectRefundExceedingOriginalAmount() {
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal refundAmount = new BigDecimal("150.00");
        
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO);
        
        RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
        
        // When/Then
        assertThatThrownBy(() -> refundService.processRefund(request, merchantId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds available balance");
    }
    
    @Test
    void shouldHandleExceptionDuringRefundProcessing() {
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal refundAmount = new BigDecimal("50.00");
        
        Payment payment = createCapturedPayment(originalAmount);
        UUID merchantId = payment.getMerchantId();
        
        when(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .thenReturn(Optional.of(payment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any(Refund.class)))
            .thenAnswer(invocation -> {
                Refund refund = invocation.getArgument(0);
                refund.setId(UUID.randomUUID());
                return refund;
            });
        when(pspRoutingService.selectPSP(any(UUID.class)))
            .thenThrow(new RuntimeException("PSP service unavailable"));
        
        RefundRequest request = new RefundRequest(payment.getPaymentId(), refundAmount);
        
        // When
        RefundResponse response = refundService.processRefund(request, merchantId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getErrorMessage()).contains("PSP service unavailable");
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
