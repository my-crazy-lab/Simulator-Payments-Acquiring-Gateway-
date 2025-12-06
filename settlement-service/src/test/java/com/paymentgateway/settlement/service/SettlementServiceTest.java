package com.paymentgateway.settlement.service;

import com.paymentgateway.settlement.domain.Payment;
import com.paymentgateway.settlement.domain.SettlementBatch;
import com.paymentgateway.settlement.domain.SettlementStatus;
import com.paymentgateway.settlement.domain.SettlementTransaction;
import com.paymentgateway.settlement.repository.PaymentRepository;
import com.paymentgateway.settlement.repository.SettlementBatchRepository;
import com.paymentgateway.settlement.repository.SettlementTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {
    
    @Mock
    private SettlementBatchRepository batchRepository;
    
    @Mock
    private SettlementTransactionRepository settlementTransactionRepository;
    
    @Mock
    private PaymentRepository paymentRepository;
    
    private SettlementService settlementService;
    
    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(
            batchRepository, settlementTransactionRepository, paymentRepository
        );
    }
    
    @Test
    void shouldCreateBatchForPayments() {
        // Given
        UUID merchantId = UUID.randomUUID();
        String currency = "USD";
        LocalDate settlementDate = LocalDate.now();
        
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Payment payment = new Payment();
            payment.setId(UUID.randomUUID());
            payment.setPaymentId("pay_" + i);
            payment.setMerchantId(merchantId);
            payment.setAmount(new BigDecimal("100.00"));
            payment.setCurrency(currency);
            payment.setStatus("CAPTURED");
            payments.add(payment);
        }
        
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(invocation -> {
            SettlementBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(UUID.randomUUID());
            }
            return batch;
        });
        when(settlementTransactionRepository.save(any(SettlementTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        SettlementBatch batch = settlementService.createBatchForPayments(
            merchantId, currency, settlementDate, payments
        );
        
        // Then
        assertThat(batch).isNotNull();
        assertThat(batch.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(batch.getTransactionCount()).isEqualTo(3);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PENDING);
        
        verify(batchRepository).save(any(SettlementBatch.class));
        verify(settlementTransactionRepository, times(3)).save(any(SettlementTransaction.class));
        verify(paymentRepository, times(3)).save(any(Payment.class));
    }
    
    @Test
    void shouldSubmitBatchToAcquirer() {
        // Given
        String batchId = "bat_test123";
        SettlementBatch batch = new SettlementBatch();
        batch.setId(UUID.randomUUID());
        batch.setBatchId(batchId);
        batch.setStatus(SettlementStatus.PENDING);
        batch.setMerchantId(UUID.randomUUID());
        batch.setCurrency("USD");
        batch.setTotalAmount(new BigDecimal("100.00"));
        batch.setTransactionCount(1);
        batch.setSettlementDate(LocalDate.now());
        
        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any(SettlementBatch.class))).thenReturn(batch);
        when(settlementTransactionRepository.findByBatchId(batch.getId())).thenReturn(new ArrayList<>());
        
        // When
        settlementService.submitBatchToAcquirer(batchId);
        
        // Then
        verify(batchRepository).save(argThat(b -> 
            b.getStatus() == SettlementStatus.PROCESSING &&
            b.getAcquirerBatchId() != null
        ));
    }
    
    @Test
    void shouldFailToSubmitNonPendingBatch() {
        // Given
        String batchId = "bat_test123";
        SettlementBatch batch = new SettlementBatch();
        batch.setId(UUID.randomUUID());
        batch.setBatchId(batchId);
        batch.setStatus(SettlementStatus.SETTLED);
        
        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.of(batch));
        
        // When/Then
        assertThatThrownBy(() -> settlementService.submitBatchToAcquirer(batchId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not in PENDING status");
    }
    
    @Test
    void shouldReconcileBatch() {
        // Given
        String batchId = "bat_test123";
        UUID batchUuid = UUID.randomUUID();
        
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchUuid);
        batch.setBatchId(batchId);
        batch.setStatus(SettlementStatus.PROCESSING);
        batch.setTotalAmount(new BigDecimal("300.00"));
        
        List<SettlementTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            SettlementTransaction tx = new SettlementTransaction();
            tx.setBatchId(batchUuid);
            tx.setGrossAmount(new BigDecimal("100.00"));
            transactions.add(tx);
        }
        
        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.of(batch));
        when(settlementTransactionRepository.findByBatchId(batchUuid)).thenReturn(transactions);
        when(batchRepository.save(any(SettlementBatch.class))).thenReturn(batch);
        
        // When
        settlementService.reconcileBatch(batchId, "acquirer_report");
        
        // Then
        verify(batchRepository).save(argThat(b -> b.getStatus() == SettlementStatus.SETTLED));
    }
    
    @Test
    void shouldGetBatch() {
        // Given
        String batchId = "bat_test123";
        SettlementBatch batch = new SettlementBatch();
        batch.setBatchId(batchId);
        
        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.of(batch));
        
        // When
        SettlementBatch result = settlementService.getBatch(batchId);
        
        // Then
        assertThat(result).isEqualTo(batch);
    }
    
    @Test
    void shouldThrowExceptionWhenBatchNotFound() {
        // Given
        String batchId = "bat_nonexistent";
        when(batchRepository.findByBatchId(batchId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> settlementService.getBatch(batchId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Batch not found");
    }
}
