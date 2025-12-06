package com.paymentgateway.settlement.service;

import com.paymentgateway.settlement.domain.Dispute;
import com.paymentgateway.settlement.domain.Payment;
import com.paymentgateway.settlement.repository.DisputeRepository;
import com.paymentgateway.settlement.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {
    
    @Mock
    private DisputeRepository disputeRepository;
    
    @Mock
    private PaymentRepository paymentRepository;
    
    private DisputeService disputeService;
    
    @BeforeEach
    void setUp() {
        disputeService = new DisputeService(disputeRepository, paymentRepository);
    }
    
    @Test
    void shouldCreateDisputeFromChargeback() {
        // Given
        String paymentId = "pay_test123";
        UUID paymentUuid = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        
        Payment payment = new Payment();
        payment.setId(paymentUuid);
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        
        Dispute savedDispute = new Dispute();
        savedDispute.setId(UUID.randomUUID());
        savedDispute.setDisputeId("dis_test123");
        savedDispute.setPaymentId(paymentUuid);
        savedDispute.setMerchantId(merchantId);
        savedDispute.setAmount(new BigDecimal("100.00"));
        savedDispute.setCurrency("USD");
        savedDispute.setStatus("OPEN");
        
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(payment));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(savedDispute);
        
        // When
        Dispute dispute = disputeService.createDisputeFromChargeback(
            paymentId,
            "CB_123",
            "FRAUD",
            "Customer claims unauthorized transaction",
            OffsetDateTime.now().plusDays(7)
        );
        
        // Then
        assertThat(dispute).isNotNull();
        assertThat(dispute.getPaymentId()).isEqualTo(paymentUuid);
        assertThat(dispute.getMerchantId()).isEqualTo(merchantId);
        assertThat(dispute.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(dispute.getStatus()).isEqualTo("OPEN");
        
        verify(disputeRepository).save(any(Dispute.class));
    }
    
    @Test
    void shouldThrowExceptionWhenPaymentNotFoundForDispute() {
        // Given
        String paymentId = "pay_nonexistent";
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> disputeService.createDisputeFromChargeback(
            paymentId, "CB_123", "FRAUD", "Test", OffsetDateTime.now()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Payment not found");
    }
    
    @Test
    void shouldSubmitEvidence() {
        // Given
        String disputeId = "dis_test123";
        Dispute dispute = new Dispute();
        dispute.setDisputeId(disputeId);
        dispute.setStatus("OPEN");
        
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(dispute);
        
        // When
        disputeService.submitEvidence(disputeId, "Evidence document");
        
        // Then
        verify(disputeRepository).save(argThat(d -> 
            "PENDING_EVIDENCE".equals(d.getStatus()) &&
            d.getEvidenceSubmittedAt() != null
        ));
    }
    
    @Test
    void shouldNotSubmitEvidenceForNonOpenDispute() {
        // Given
        String disputeId = "dis_test123";
        Dispute dispute = new Dispute();
        dispute.setDisputeId(disputeId);
        dispute.setStatus("WON");
        
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(dispute));
        
        // When/Then
        assertThatThrownBy(() -> disputeService.submitEvidence(disputeId, "Evidence"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not in OPEN status");
    }
    
    @Test
    void shouldResolveDisputeAsMerchantWon() {
        // Given
        String disputeId = "dis_test123";
        Dispute dispute = new Dispute();
        dispute.setDisputeId(disputeId);
        dispute.setStatus("PENDING_EVIDENCE");
        
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(dispute);
        
        // When
        disputeService.resolveDispute(disputeId, "Merchant provided sufficient evidence", true);
        
        // Then
        verify(disputeRepository).save(argThat(d -> 
            "WON".equals(d.getStatus()) &&
            d.getResolvedAt() != null &&
            d.getResolution() != null
        ));
    }
    
    @Test
    void shouldResolveDisputeAsMerchantLost() {
        // Given
        String disputeId = "dis_test123";
        Dispute dispute = new Dispute();
        dispute.setDisputeId(disputeId);
        dispute.setPaymentId(UUID.randomUUID());
        dispute.setAmount(new BigDecimal("100.00"));
        dispute.setStatus("PENDING_EVIDENCE");
        
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(dispute);
        
        // When
        disputeService.resolveDispute(disputeId, "Insufficient evidence", false);
        
        // Then
        verify(disputeRepository).save(argThat(d -> 
            "LOST".equals(d.getStatus()) &&
            d.getResolvedAt() != null
        ));
    }
    
    @Test
    void shouldGetDispute() {
        // Given
        String disputeId = "dis_test123";
        Dispute dispute = new Dispute();
        dispute.setDisputeId(disputeId);
        
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(dispute));
        
        // When
        Dispute result = disputeService.getDispute(disputeId);
        
        // Then
        assertThat(result).isEqualTo(dispute);
    }
}
