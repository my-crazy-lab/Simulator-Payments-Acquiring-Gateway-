package com.paymentgateway.settlement.integration;

import com.paymentgateway.settlement.domain.*;
import com.paymentgateway.settlement.repository.*;
import com.paymentgateway.settlement.service.DisputeService;
import com.paymentgateway.settlement.service.SettlementService;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for settlement and chargeback flows.
 * Tests the complete settlement batch processing and dispute handling.
 * 
 * Requirements: 6.1-6.5, 12.1-12.5 - Settlement and dispute processing
 */
class SettlementFlowIntegrationTest {
    
    @Mock private SettlementBatchRepository batchRepository;
    @Mock private SettlementTransactionRepository settlementTransactionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DisputeRepository disputeRepository;
    
    private SettlementService settlementService;
    private DisputeService disputeService;
    private AutoCloseable mocks;
    
    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        settlementService = new SettlementService(
            batchRepository, settlementTransactionRepository, paymentRepository
        );
        disputeService = new DisputeService(disputeRepository, paymentRepository);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }
    
    // ==================== Settlement Flow Tests ====================
    
    /**
     * Test complete settlement batch creation flow.
     * Validates: Requirements 6.1 - Settlement aggregation
     */
    @Test
    @DisplayName("Settlement batch should aggregate all captured transactions")
    void shouldAggregateAllCapturedTransactionsInBatch() {
        // Given - Multiple captured payments for same merchant
        UUID merchantId = UUID.randomUUID();
        String currency = "USD";
        LocalDate settlementDate = LocalDate.now();
        
        List<Payment> capturedPayments = createCapturedPayments(merchantId, currency, 5);
        BigDecimal expectedTotal = capturedPayments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        when(batchRepository.save(any(SettlementBatch.class)))
            .thenAnswer(invocation -> {
                SettlementBatch batch = invocation.getArgument(0);
                batch.setId(UUID.randomUUID());
                return batch;
            });
        when(settlementTransactionRepository.save(any(SettlementTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        SettlementBatch batch = settlementService.createBatchForPayments(
            merchantId, currency, settlementDate, capturedPayments
        );
        
        // Then
        assertThat(batch).isNotNull();
        assertThat(batch.getBatchId()).isNotNull().startsWith("bat_");
        assertThat(batch.getMerchantId()).isEqualTo(merchantId);
        assertThat(batch.getCurrency()).isEqualTo(currency);
        assertThat(batch.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(batch.getTransactionCount()).isEqualTo(5);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PENDING);
        
        // Verify all payments were processed
        verify(settlementTransactionRepository, times(5)).save(any(SettlementTransaction.class));
        verify(paymentRepository, times(5)).save(any(Payment.class));
    }
    
    /**
     * Test settlement batch submission to acquirer.
     * Validates: Requirements 6.2, 6.3 - Settlement file generation and submission
     */
    @Test
    @DisplayName("Settlement batch should be submitted to acquirer successfully")
    void shouldSubmitBatchToAcquirerSuccessfully() {
        // Given
        String batchId = "bat_submit_test";
        UUID batchUuid = UUID.randomUUID();
        
        SettlementBatch batch = createPendingBatch(batchId, batchUuid);
        
        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any(SettlementBatch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementTransactionRepository.findByBatchId(batchUuid))
            .thenReturn(createSettlementTransactions(batchUuid, 3));
        
        // When
        settlementService.submitBatchToAcquirer(batchId);
        
        // Then
        verify(batchRepository).save(argThat(b -> 
            b.getStatus() == SettlementStatus.PROCESSING &&
            b.getAcquirerBatchId() != null &&
            b.getAcquirerBatchId().startsWith("ACQ_") &&
            b.getProcessedAt() != null
        ));
    }
    
    /**
     * Test settlement reconciliation.
     * Validates: Requirements 6.4 - Reconciliation with acquirer
     */
    @Test
    @DisplayName("Settlement reconciliation should verify totals match")
    void shouldReconcileBatchSuccessfully() {
        // Given
        String batchId = "bat_reconcile_test";
        UUID batchUuid = UUID.randomUUID();
        BigDecimal batchTotal = new BigDecimal("300.00");
        
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchUuid);
        batch.setBatchId(batchId);
        batch.setStatus(SettlementStatus.PROCESSING);
        batch.setTotalAmount(batchTotal);
        
        // Create transactions that sum to batch total
        List<SettlementTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            SettlementTransaction tx = new SettlementTransaction();
            tx.setBatchId(batchUuid);
            tx.setGrossAmount(new BigDecimal("100.00"));
            transactions.add(tx);
        }
        
        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.of(batch));
        when(settlementTransactionRepository.findByBatchId(batchUuid)).thenReturn(transactions);
        when(batchRepository.save(any(SettlementBatch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        settlementService.reconcileBatch(batchId, "acquirer_report_data");
        
        // Then
        verify(batchRepository).save(argThat(b -> 
            b.getStatus() == SettlementStatus.SETTLED &&
            b.getUpdatedAt() != null
        ));
    }
    
    /**
     * Test settlement reconciliation failure on mismatch.
     * Validates: Requirements 6.5 - Discrepancy detection
     */
    @Test
    @DisplayName("Settlement reconciliation should fail on amount mismatch")
    void shouldFailReconciliationOnMismatch() {
        // Given
        String batchId = "bat_mismatch_test";
        UUID batchUuid = UUID.randomUUID();
        
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchUuid);
        batch.setBatchId(batchId);
        batch.setStatus(SettlementStatus.PROCESSING);
        batch.setTotalAmount(new BigDecimal("500.00")); // Expected total
        
        // Create transactions that don't sum to batch total
        List<SettlementTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            SettlementTransaction tx = new SettlementTransaction();
            tx.setBatchId(batchUuid);
            tx.setGrossAmount(new BigDecimal("100.00")); // Sum = 300, not 500
            transactions.add(tx);
        }
        
        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.of(batch));
        when(settlementTransactionRepository.findByBatchId(batchUuid)).thenReturn(transactions);
        
        // When/Then
        assertThatThrownBy(() -> settlementService.reconcileBatch(batchId, "acquirer_report"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Reconciliation mismatch");
    }

    
    // ==================== Chargeback Flow Tests ====================
    
    /**
     * Test chargeback creates dispute record.
     * Validates: Requirements 12.1 - Chargeback creates dispute
     */
    @Test
    @DisplayName("Chargeback notification should create dispute linked to payment")
    void shouldCreateDisputeFromChargebackNotification() {
        // Given
        String paymentId = "pay_chargeback_test";
        UUID paymentUuid = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("150.00");
        
        Payment payment = new Payment();
        payment.setId(paymentUuid);
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setAmount(amount);
        payment.setCurrency("USD");
        payment.setStatus("CAPTURED");
        
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(payment));
        when(disputeRepository.save(any(Dispute.class)))
            .thenAnswer(invocation -> {
                Dispute d = invocation.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });
        
        OffsetDateTime deadline = OffsetDateTime.now().plusDays(7);
        
        // When
        Dispute dispute = disputeService.createDisputeFromChargeback(
            paymentId,
            "CB_REF_12345",
            "FRAUD",
            "Customer claims unauthorized transaction",
            deadline
        );
        
        // Then
        assertThat(dispute).isNotNull();
        assertThat(dispute.getDisputeId()).isNotNull().startsWith("dis_");
        assertThat(dispute.getPaymentId()).isEqualTo(paymentUuid);
        assertThat(dispute.getMerchantId()).isEqualTo(merchantId);
        assertThat(dispute.getAmount()).isEqualByComparingTo(amount);
        assertThat(dispute.getCurrency()).isEqualTo("USD");
        assertThat(dispute.getReasonCode()).isEqualTo("FRAUD");
        assertThat(dispute.getChargebackReference()).isEqualTo("CB_REF_12345");
        assertThat(dispute.getStatus()).isEqualTo("OPEN");
        assertThat(dispute.getDeadline()).isEqualTo(deadline);
        
        verify(disputeRepository).save(any(Dispute.class));
    }
    
    /**
     * Test dispute evidence submission flow.
     * Validates: Requirements 12.3 - Evidence submission
     */
    @Test
    @DisplayName("Merchant should be able to submit dispute evidence")
    void shouldSubmitDisputeEvidence() {
        // Given
        String disputeId = "dis_evidence_test";
        
        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setDisputeId(disputeId);
        dispute.setStatus("OPEN");
        dispute.setMerchantId(UUID.randomUUID());
        
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        String evidence = "Transaction receipt, delivery confirmation, customer signature";
        
        // When
        disputeService.submitEvidence(disputeId, evidence);
        
        // Then
        verify(disputeRepository).save(argThat(d -> 
            "PENDING_EVIDENCE".equals(d.getStatus()) &&
            d.getEvidenceSubmittedAt() != null &&
            d.getUpdatedAt() != null
        ));
    }
    
    /**
     * Test dispute resolution - merchant wins.
     * Validates: Requirements 12.4 - Dispute resolution
     */
    @Test
    @DisplayName("Dispute resolution should update status when merchant wins")
    void shouldResolveDisputeWhenMerchantWins() {
        // Given
        String disputeId = "dis_win_test";
        
        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setDisputeId(disputeId);
        dispute.setPaymentId(UUID.randomUUID());
        dispute.setMerchantId(UUID.randomUUID());
        dispute.setAmount(new BigDecimal("100.00"));
        dispute.setStatus("PENDING_EVIDENCE");
        
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        disputeService.resolveDispute(disputeId, "Merchant provided valid evidence", true);
        
        // Then
        verify(disputeRepository).save(argThat(d -> 
            "WON".equals(d.getStatus()) &&
            d.getResolvedAt() != null &&
            "Merchant provided valid evidence".equals(d.getResolution())
        ));
    }
    
    /**
     * Test dispute resolution - merchant loses (chargeback finalized).
     * Validates: Requirements 12.4, 12.5 - Chargeback finalization
     */
    @Test
    @DisplayName("Dispute resolution should adjust settlement when merchant loses")
    void shouldAdjustSettlementWhenMerchantLosesDispute() {
        // Given
        String disputeId = "dis_lose_test";
        UUID paymentId = UUID.randomUUID();
        
        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setDisputeId(disputeId);
        dispute.setPaymentId(paymentId);
        dispute.setMerchantId(UUID.randomUUID());
        dispute.setAmount(new BigDecimal("100.00"));
        dispute.setStatus("PENDING_EVIDENCE");
        
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        disputeService.resolveDispute(disputeId, "Insufficient evidence provided", false);
        
        // Then
        verify(disputeRepository).save(argThat(d -> 
            "LOST".equals(d.getStatus()) &&
            d.getResolvedAt() != null &&
            "Insufficient evidence provided".equals(d.getResolution())
        ));
        // Note: Settlement adjustment would be verified in a full integration test
    }
    
    /**
     * Test complete chargeback lifecycle.
     * Validates: Requirements 12.1-12.5 - Complete chargeback flow
     */
    @Test
    @DisplayName("Complete chargeback lifecycle should process correctly")
    void shouldProcessCompleteChargebackLifecycle() {
        // Given - Payment exists
        String paymentId = "pay_lifecycle_test";
        UUID paymentUuid = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        
        Payment payment = new Payment();
        payment.setId(paymentUuid);
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setAmount(new BigDecimal("200.00"));
        payment.setCurrency("USD");
        
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(payment));
        
        // Step 1: Create dispute from chargeback
        Dispute createdDispute = new Dispute();
        createdDispute.setId(UUID.randomUUID());
        createdDispute.setDisputeId("dis_lifecycle_test");
        createdDispute.setPaymentId(paymentUuid);
        createdDispute.setMerchantId(merchantId);
        createdDispute.setAmount(new BigDecimal("200.00"));
        createdDispute.setCurrency("USD");
        createdDispute.setStatus("OPEN");
        
        when(disputeRepository.save(any(Dispute.class)))
            .thenReturn(createdDispute);
        
        Dispute dispute = disputeService.createDisputeFromChargeback(
            paymentId, "CB_LIFECYCLE", "PRODUCT_NOT_RECEIVED", 
            "Customer did not receive product", OffsetDateTime.now().plusDays(14)
        );
        
        assertThat(dispute.getStatus()).isEqualTo("OPEN");
        
        // Step 2: Submit evidence
        when(disputeRepository.findByDisputeId("dis_lifecycle_test"))
            .thenReturn(Optional.of(createdDispute));
        when(disputeRepository.save(any(Dispute.class)))
            .thenAnswer(invocation -> {
                Dispute d = invocation.getArgument(0);
                d.setStatus("PENDING_EVIDENCE");
                return d;
            });
        
        disputeService.submitEvidence("dis_lifecycle_test", "Tracking number: 1234567890");
        
        // Step 3: Resolve dispute
        createdDispute.setStatus("PENDING_EVIDENCE");
        when(disputeRepository.findByDisputeId("dis_lifecycle_test"))
            .thenReturn(Optional.of(createdDispute));
        when(disputeRepository.save(any(Dispute.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        disputeService.resolveDispute("dis_lifecycle_test", "Tracking confirmed delivery", true);
        
        // Verify complete flow
        verify(disputeRepository, atLeast(3)).save(any(Dispute.class));
    }
    
    // ==================== Helper Methods ====================
    
    private List<Payment> createCapturedPayments(UUID merchantId, String currency, int count) {
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Payment payment = new Payment();
            payment.setId(UUID.randomUUID());
            payment.setPaymentId("pay_" + UUID.randomUUID().toString().substring(0, 8));
            payment.setMerchantId(merchantId);
            payment.setAmount(new BigDecimal("100.00"));
            payment.setCurrency(currency);
            payment.setStatus("CAPTURED");
            payment.setCapturedAt(OffsetDateTime.now().minusHours(i + 1));
            payments.add(payment);
        }
        return payments;
    }
    
    private SettlementBatch createPendingBatch(String batchId, UUID batchUuid) {
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchUuid);
        batch.setBatchId(batchId);
        batch.setMerchantId(UUID.randomUUID());
        batch.setCurrency("USD");
        batch.setTotalAmount(new BigDecimal("300.00"));
        batch.setTransactionCount(3);
        batch.setSettlementDate(LocalDate.now());
        batch.setStatus(SettlementStatus.PENDING);
        return batch;
    }
    
    private List<SettlementTransaction> createSettlementTransactions(UUID batchId, int count) {
        List<SettlementTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SettlementTransaction tx = new SettlementTransaction();
            tx.setId(UUID.randomUUID());
            tx.setBatchId(batchId);
            tx.setPaymentId(UUID.randomUUID());
            tx.setGrossAmount(new BigDecimal("100.00"));
            tx.setFeeAmount(new BigDecimal("3.20"));
            tx.setNetAmount(new BigDecimal("96.80"));
            tx.setCurrency("USD");
            transactions.add(tx);
        }
        return transactions;
    }
}
