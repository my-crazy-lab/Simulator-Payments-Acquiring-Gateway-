package com.paymentgateway.authorization.integration;

import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.*;
import com.paymentgateway.authorization.event.PaymentEventPublisher;
import com.paymentgateway.authorization.idempotency.IdempotencyService;
import com.paymentgateway.authorization.psp.*;
import com.paymentgateway.authorization.repository.*;
import com.paymentgateway.authorization.service.PaymentService;
import com.paymentgateway.authorization.service.RefundService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for critical payment flows.
 * Tests the complete payment authorization, capture, refund, and void flows.
 * 
 * Requirements: All requirements - validates end-to-end payment processing
 */
class PaymentFlowIntegrationTest {
    
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentEventRepository paymentEventRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private PSPRoutingService pspRoutingService;
    @Mock private PSPClient pspClient;
    @Mock private Tracer tracer;
    @Mock private IdempotencyService idempotencyService;
    @Mock private PaymentEventPublisher eventPublisher;
    
    private PaymentService paymentService;
    private RefundService refundService;
    private AutoCloseable mocks;
    
    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        
        // Setup tracer mock
        Span span = mock(Span.class);
        SpanBuilder spanBuilder = mock(SpanBuilder.class);
        SpanContext spanContext = mock(SpanContext.class);
        Scope scope = mock(Scope.class);
        
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);
        when(span.getSpanContext()).thenReturn(spanContext);
        when(spanContext.getTraceId()).thenReturn("test-trace-id");
        
        paymentService = new PaymentService(
            paymentRepository,
            paymentEventRepository,
            pspRoutingService,
            tracer,
            idempotencyService,
            eventPublisher
        );
        
        refundService = new RefundService(
            refundRepository,
            paymentRepository,
            paymentEventRepository,
            pspRoutingService,
            eventPublisher
        );
    }
    
    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    
    // ==================== Payment Authorization Flow Tests ====================
    
    /**
     * Test complete payment authorization flow.
     * Validates: Requirements 1.1, 16.1 - Payment processing and service orchestration
     */
    @Test
    @DisplayName("Complete payment authorization flow should succeed")
    void shouldCompletePaymentAuthorizationFlow() {
        // Given - Valid payment request
        PaymentRequest request = createValidPaymentRequest();
        UUID merchantId = UUID.randomUUID();
        
        // Mock PSP authorization success
        PSPAuthorizationResponse pspResponse = new PSPAuthorizationResponse();
        pspResponse.setSuccess(true);
        pspResponse.setPspTransactionId("psp_txn_" + UUID.randomUUID());
        pspResponse.setStatus("AUTHORIZED");
        
        when(pspRoutingService.authorizeWithFailover(any(PSPAuthorizationRequest.class)))
            .thenReturn(pspResponse);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                p.setCreatedAt(Instant.now());
                return p;
            });
        when(paymentEventRepository.save(any(PaymentEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        PaymentResponse response = paymentService.processPayment(request, merchantId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isNotNull().startsWith("pay_");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(response.getAmount()).isEqualByComparingTo(request.getAmount());
        assertThat(response.getCurrency()).isEqualTo(request.getCurrency());
        assertThat(response.getCardLastFour()).isEqualTo("4242");
        
        // Verify service orchestration sequence
        verify(pspRoutingService).authorizeWithFailover(any(PSPAuthorizationRequest.class));
        verify(paymentRepository).save(any(Payment.class));
        verify(paymentEventRepository).save(any(PaymentEvent.class));
        verify(eventPublisher).publishPaymentEvent(any(Payment.class), any());
    }
    
    /**
     * Test payment authorization with idempotency key.
     * Validates: Requirements 21.1, 21.2 - Idempotency handling
     */
    @Test
    @DisplayName("Payment with idempotency key should return cached result on duplicate")
    void shouldReturnCachedResultForDuplicateIdempotencyKey() {
        // Given
        PaymentRequest request = createValidPaymentRequest();
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "idem_" + UUID.randomUUID();
        
        PaymentResponse cachedResponse = new PaymentResponse();
        cachedResponse.setPaymentId("pay_cached123");
        cachedResponse.setStatus(PaymentStatus.AUTHORIZED);
        cachedResponse.setAmount(request.getAmount());
        
        when(idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class))
            .thenReturn(cachedResponse);
        
        // When
        PaymentResponse response = paymentService.processPayment(request, merchantId, idempotencyKey);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo("pay_cached123");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        
        // Verify no PSP call was made
        verify(pspRoutingService, never()).authorizeWithFailover(any());
        verify(paymentRepository, never()).save(any());
    }
    
    /**
     * Test payment authorization failure from PSP.
     * Validates: Requirements 3.4 - PSP response handling
     */
    @Test
    @DisplayName("Payment should be declined when PSP returns failure")
    void shouldDeclinePaymentWhenPSPFails() {
        // Given
        PaymentRequest request = createValidPaymentRequest();
        UUID merchantId = UUID.randomUUID();
        
        PSPAuthorizationResponse pspResponse = new PSPAuthorizationResponse();
        pspResponse.setSuccess(false);
        pspResponse.setDeclineCode("INSUFFICIENT_FUNDS");
        pspResponse.setDeclineMessage("Card has insufficient funds");
        
        when(pspRoutingService.authorizeWithFailover(any(PSPAuthorizationRequest.class)))
            .thenReturn(pspResponse);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
        when(paymentEventRepository.save(any(PaymentEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        PaymentResponse response = paymentService.processPayment(request, merchantId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    }
    
    // ==================== Payment Capture Flow Tests ====================
    
    /**
     * Test payment capture flow.
     * Validates: Requirements 1.1 - Payment capture processing
     */
    @Test
    @DisplayName("Capture should succeed for authorized payment")
    void shouldCaptureAuthorizedPayment() {
        // Given
        Payment authorizedPayment = createAuthorizedPayment();
        String paymentId = authorizedPayment.getPaymentId();
        
        when(paymentRepository.findByPaymentId(paymentId))
            .thenReturn(Optional.of(authorizedPayment));
        when(pspRoutingService.selectPSP(any(UUID.class)))
            .thenReturn(pspClient);
        
        PSPCaptureResponse captureResponse = new PSPCaptureResponse();
        captureResponse.setSuccess(true);
        captureResponse.setPspTransactionId("cap_" + UUID.randomUUID());
        when(pspClient.capture(anyString(), any(BigDecimal.class), anyString()))
            .thenReturn(captureResponse);
        
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        PaymentResponse response = paymentService.capturePayment(paymentId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        
        verify(pspClient).capture(
            eq(authorizedPayment.getPspTransactionId()),
            eq(authorizedPayment.getAmount()),
            eq(authorizedPayment.getCurrency())
        );
        verify(eventPublisher).publishPaymentEvent(any(Payment.class), any());
    }
    
    /**
     * Test capture fails for non-authorized payment.
     */
    @Test
    @DisplayName("Capture should fail for non-authorized payment")
    void shouldFailCaptureForNonAuthorizedPayment() {
        // Given
        Payment pendingPayment = createAuthorizedPayment();
        pendingPayment.setStatus(PaymentStatus.PENDING);
        String paymentId = pendingPayment.getPaymentId();
        
        when(paymentRepository.findByPaymentId(paymentId))
            .thenReturn(Optional.of(pendingPayment));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.capturePayment(paymentId))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("AUTHORIZED");
    }

    
    // ==================== Refund Flow Tests ====================
    
    /**
     * Test complete refund flow.
     * Validates: Requirements 7.1, 7.2, 7.3, 7.4 - Refund processing
     */
    @Test
    @DisplayName("Complete refund flow should succeed for captured payment")
    void shouldCompleteRefundFlowForCapturedPayment() {
        // Given
        Payment capturedPayment = createCapturedPayment();
        UUID merchantId = capturedPayment.getMerchantId();
        BigDecimal refundAmount = new BigDecimal("50.00");
        
        when(paymentRepository.findByPaymentId(capturedPayment.getPaymentId()))
            .thenReturn(Optional.of(capturedPayment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any(Refund.class)))
            .thenAnswer(invocation -> {
                Refund r = invocation.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });
        when(pspRoutingService.selectPSP(any(UUID.class)))
            .thenReturn(pspClient);
        
        PSPRefundResponse pspResponse = new PSPRefundResponse();
        pspResponse.setSuccess(true);
        pspResponse.setPspRefundId("ref_" + UUID.randomUUID());
        pspResponse.setRefundedAmount(refundAmount);
        when(pspClient.refund(anyString(), any(BigDecimal.class), anyString()))
            .thenReturn(pspResponse);
        
        when(paymentEventRepository.save(any(PaymentEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        RefundRequest request = new RefundRequest(capturedPayment.getPaymentId(), refundAmount, "Customer request");
        
        // When
        RefundResponse response = refundService.processRefund(request, merchantId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRefundId()).isNotNull().startsWith("ref_");
        assertThat(response.getPaymentId()).isEqualTo(capturedPayment.getPaymentId());
        assertThat(response.getAmount()).isEqualByComparingTo(refundAmount);
        assertThat(response.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        
        verify(pspClient).refund(
            eq(capturedPayment.getPspTransactionId()),
            eq(refundAmount),
            eq(capturedPayment.getCurrency())
        );
    }
    
    /**
     * Test refund amount constraint.
     * Validates: Requirements 7.2 - Refund amount must not exceed original
     */
    @Test
    @DisplayName("Refund should fail when amount exceeds original transaction")
    void shouldFailRefundWhenAmountExceedsOriginal() {
        // Given
        Payment capturedPayment = createCapturedPayment();
        UUID merchantId = capturedPayment.getMerchantId();
        BigDecimal excessiveRefundAmount = new BigDecimal("200.00"); // Original is 100.00
        
        when(paymentRepository.findByPaymentId(capturedPayment.getPaymentId()))
            .thenReturn(Optional.of(capturedPayment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO);
        
        RefundRequest request = new RefundRequest(capturedPayment.getPaymentId(), excessiveRefundAmount);
        
        // When/Then
        assertThatThrownBy(() -> refundService.processRefund(request, merchantId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds available balance");
    }
    
    /**
     * Test partial refund with existing refunds.
     * Validates: Requirements 7.2 - Cumulative refund constraint
     */
    @Test
    @DisplayName("Partial refund should fail when cumulative amount exceeds original")
    void shouldFailPartialRefundWhenCumulativeExceedsOriginal() {
        // Given
        Payment capturedPayment = createCapturedPayment(); // Amount: 100.00
        UUID merchantId = capturedPayment.getMerchantId();
        BigDecimal existingRefunds = new BigDecimal("80.00");
        BigDecimal newRefundAmount = new BigDecimal("30.00"); // Total would be 110.00
        
        when(paymentRepository.findByPaymentId(capturedPayment.getPaymentId()))
            .thenReturn(Optional.of(capturedPayment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(existingRefunds);
        
        RefundRequest request = new RefundRequest(capturedPayment.getPaymentId(), newRefundAmount);
        
        // When/Then
        assertThatThrownBy(() -> refundService.processRefund(request, merchantId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds available balance");
    }
    
    /**
     * Test refund for non-existent payment.
     * Validates: Requirements 7.1 - Payment validation
     */
    @Test
    @DisplayName("Refund should fail for non-existent payment")
    void shouldFailRefundForNonExistentPayment() {
        // Given
        String nonExistentPaymentId = "pay_nonexistent";
        UUID merchantId = UUID.randomUUID();
        
        when(paymentRepository.findByPaymentId(nonExistentPaymentId))
            .thenReturn(Optional.empty());
        
        RefundRequest request = new RefundRequest(nonExistentPaymentId, new BigDecimal("50.00"));
        
        // When/Then
        assertThatThrownBy(() -> refundService.processRefund(request, merchantId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Payment not found");
    }
    
    // ==================== Void Flow Tests ====================
    
    /**
     * Test payment void flow.
     * Validates: Requirements 1.1 - Payment void processing
     */
    @Test
    @DisplayName("Void should succeed for authorized payment")
    void shouldVoidAuthorizedPayment() {
        // Given
        Payment authorizedPayment = createAuthorizedPayment();
        String paymentId = authorizedPayment.getPaymentId();
        
        when(paymentRepository.findByPaymentId(paymentId))
            .thenReturn(Optional.of(authorizedPayment));
        when(pspRoutingService.selectPSP(any(UUID.class)))
            .thenReturn(pspClient);
        
        PSPVoidResponse voidResponse = new PSPVoidResponse();
        voidResponse.setSuccess(true);
        when(pspClient.voidTransaction(anyString()))
            .thenReturn(voidResponse);
        
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        PaymentResponse response = paymentService.voidPayment(paymentId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        
        verify(pspClient).voidTransaction(authorizedPayment.getPspTransactionId());
        verify(eventPublisher).publishPaymentEvent(any(Payment.class), any());
    }

    
    // ==================== End-to-End Flow Tests ====================
    
    /**
     * Test complete payment lifecycle: authorize -> capture -> refund.
     * Validates: All payment lifecycle requirements
     */
    @Test
    @DisplayName("Complete payment lifecycle should succeed")
    void shouldCompleteFullPaymentLifecycle() {
        // Given
        PaymentRequest request = createValidPaymentRequest();
        UUID merchantId = UUID.randomUUID();
        
        // Step 1: Authorization
        PSPAuthorizationResponse authResponse = new PSPAuthorizationResponse();
        authResponse.setSuccess(true);
        authResponse.setPspTransactionId("psp_txn_lifecycle");
        
        when(pspRoutingService.authorizeWithFailover(any(PSPAuthorizationRequest.class)))
            .thenReturn(authResponse);
        
        Payment savedPayment = new Payment();
        savedPayment.setId(UUID.randomUUID());
        savedPayment.setPaymentId("pay_lifecycle123");
        savedPayment.setMerchantId(merchantId);
        savedPayment.setAmount(request.getAmount());
        savedPayment.setCurrency(request.getCurrency());
        savedPayment.setStatus(PaymentStatus.AUTHORIZED);
        savedPayment.setPspTransactionId("psp_txn_lifecycle");
        savedPayment.setCardBrand(CardBrand.VISA);
        savedPayment.setCardLastFour("4242");
        savedPayment.setCreatedAt(Instant.now());
        
        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(savedPayment);
        when(paymentEventRepository.save(any(PaymentEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute authorization
        PaymentResponse authResult = paymentService.processPayment(request, merchantId);
        assertThat(authResult.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        
        // Step 2: Capture
        when(paymentRepository.findByPaymentId("pay_lifecycle123"))
            .thenReturn(Optional.of(savedPayment));
        when(pspRoutingService.selectPSP(merchantId))
            .thenReturn(pspClient);
        
        PSPCaptureResponse captureResponse = new PSPCaptureResponse();
        captureResponse.setSuccess(true);
        when(pspClient.capture(anyString(), any(BigDecimal.class), anyString()))
            .thenReturn(captureResponse);
        
        Payment capturedPayment = new Payment();
        capturedPayment.setId(savedPayment.getId());
        capturedPayment.setPaymentId(savedPayment.getPaymentId());
        capturedPayment.setMerchantId(merchantId);
        capturedPayment.setAmount(savedPayment.getAmount());
        capturedPayment.setCurrency(savedPayment.getCurrency());
        capturedPayment.setStatus(PaymentStatus.CAPTURED);
        capturedPayment.setPspTransactionId(savedPayment.getPspTransactionId());
        capturedPayment.setCapturedAt(Instant.now());
        
        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(capturedPayment);
        
        PaymentResponse captureResult = paymentService.capturePayment("pay_lifecycle123");
        assertThat(captureResult.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        
        // Step 3: Refund
        when(paymentRepository.findByPaymentId("pay_lifecycle123"))
            .thenReturn(Optional.of(capturedPayment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatuses(any(UUID.class), anyList()))
            .thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any(Refund.class)))
            .thenAnswer(invocation -> {
                Refund r = invocation.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });
        
        PSPRefundResponse refundResponse = new PSPRefundResponse();
        refundResponse.setSuccess(true);
        refundResponse.setPspRefundId("ref_lifecycle");
        refundResponse.setRefundedAmount(request.getAmount());
        when(pspClient.refund(anyString(), any(BigDecimal.class), anyString()))
            .thenReturn(refundResponse);
        
        RefundRequest refundRequest = new RefundRequest("pay_lifecycle123", request.getAmount());
        RefundResponse refundResult = refundService.processRefund(refundRequest, merchantId);
        
        assertThat(refundResult.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refundResult.getAmount()).isEqualByComparingTo(request.getAmount());
    }
    
    /**
     * Test payment query flow.
     * Validates: Requirements 15.1, 15.2 - Transaction query
     */
    @Test
    @DisplayName("Payment query should return masked card data")
    void shouldReturnMaskedCardDataOnQuery() {
        // Given
        Payment payment = createCapturedPayment();
        String paymentId = payment.getPaymentId();
        
        when(paymentRepository.findByPaymentId(paymentId))
            .thenReturn(Optional.of(payment));
        
        // When
        PaymentResponse response = paymentService.getPayment(paymentId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getCardLastFour()).isEqualTo("4242");
        assertThat(response.getCardLastFour()).hasSize(4);
        // Verify no full PAN is exposed
        assertThat(response.getCardLastFour()).doesNotContain("4242424242424242");
    }
    
    /**
     * Test payment not found scenario.
     * Validates: Requirements 15.5 - Error handling for non-existent transactions
     */
    @Test
    @DisplayName("Payment query should fail for non-existent payment")
    void shouldFailQueryForNonExistentPayment() {
        // Given
        String nonExistentPaymentId = "pay_nonexistent";
        
        when(paymentRepository.findByPaymentId(nonExistentPaymentId))
            .thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> paymentService.getPayment(nonExistentPaymentId))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Payment not found");
    }
    
    // ==================== Helper Methods ====================
    
    private PaymentRequest createValidPaymentRequest() {
        PaymentRequest request = new PaymentRequest();
        request.setCardNumber("4242424242424242");
        request.setExpiryMonth(12);
        request.setExpiryYear(2026);
        request.setCvv("123");
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setDescription("Test payment");
        request.setBillingStreet("123 Test St");
        request.setBillingCity("Test City");
        request.setBillingState("TS");
        request.setBillingZip("12345");
        request.setBillingCountry("US");
        return request;
    }
    
    private Payment createAuthorizedPayment() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setPaymentId("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        payment.setMerchantId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setPspTransactionId("psp_" + UUID.randomUUID());
        payment.setCardTokenId(UUID.randomUUID());
        payment.setCardLastFour("4242");
        payment.setCardBrand(CardBrand.VISA);
        payment.setCreatedAt(Instant.now());
        payment.setAuthorizedAt(Instant.now());
        return payment;
    }
    
    private Payment createCapturedPayment() {
        Payment payment = createAuthorizedPayment();
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());
        return payment;
    }
}
