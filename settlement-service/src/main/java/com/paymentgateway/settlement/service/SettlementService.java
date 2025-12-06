package com.paymentgateway.settlement.service;

import com.paymentgateway.settlement.domain.*;
import com.paymentgateway.settlement.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SettlementService {
    
    private static final Logger logger = LoggerFactory.getLogger(SettlementService.class);
    
    private final SettlementBatchRepository batchRepository;
    private final SettlementTransactionRepository settlementTransactionRepository;
    private final PaymentRepository paymentRepository;
    
    public SettlementService(SettlementBatchRepository batchRepository,
                           SettlementTransactionRepository settlementTransactionRepository,
                           PaymentRepository paymentRepository) {
        this.batchRepository = batchRepository;
        this.settlementTransactionRepository = settlementTransactionRepository;
        this.paymentRepository = paymentRepository;
    }
    
    /**
     * Scheduled job to process settlement batches daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void processSettlementBatches() {
        logger.info("Starting scheduled settlement batch processing");
        try {
            createSettlementBatches();
            logger.info("Settlement batch processing completed successfully");
        } catch (Exception e) {
            logger.error("Error processing settlement batches", e);
        }
    }
    
    /**
     * Create settlement batches for all unsettled captured payments
     */
    @Transactional
    public List<SettlementBatch> createSettlementBatches() {
        OffsetDateTime cutoffTime = OffsetDateTime.now();
        List<Payment> unsettledPayments = paymentRepository.findUnsettledCapturedPayments(cutoffTime);
        
        if (unsettledPayments.isEmpty()) {
            logger.info("No unsettled payments found");
            return Collections.emptyList();
        }
        
        // Group payments by merchant and currency
        Map<String, List<Payment>> groupedPayments = unsettledPayments.stream()
            .collect(Collectors.groupingBy(p -> p.getMerchantId() + "_" + p.getCurrency()));
        
        List<SettlementBatch> batches = new ArrayList<>();
        LocalDate settlementDate = LocalDate.now();
        
        for (Map.Entry<String, List<Payment>> entry : groupedPayments.entrySet()) {
            List<Payment> payments = entry.getValue();
            if (payments.isEmpty()) continue;
            
            UUID merchantId = payments.get(0).getMerchantId();
            String currency = payments.get(0).getCurrency();
            
            SettlementBatch batch = createBatchForPayments(merchantId, currency, settlementDate, payments);
            batches.add(batch);
        }
        
        logger.info("Created {} settlement batches", batches.size());
        return batches;
    }
    
    /**
     * Create a settlement batch for a group of payments
     */
    @Transactional
    public SettlementBatch createBatchForPayments(UUID merchantId, String currency, 
                                                  LocalDate settlementDate, List<Payment> payments) {
        // Calculate totals
        BigDecimal totalAmount = payments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int transactionCount = payments.size();
        
        // Generate batch ID
        String batchId = "bat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        
        // Create batch
        SettlementBatch batch = new SettlementBatch(
            batchId, merchantId, settlementDate, currency, totalAmount, transactionCount
        );
        batch.setStatus(SettlementStatus.PENDING);
        batch = batchRepository.save(batch);
        
        // Create settlement transactions
        for (Payment payment : payments) {
            BigDecimal grossAmount = payment.getAmount();
            BigDecimal feeAmount = calculateFee(grossAmount);
            BigDecimal netAmount = grossAmount.subtract(feeAmount);
            
            SettlementTransaction settlementTx = new SettlementTransaction(
                batch.getId(), payment.getId(), grossAmount, feeAmount, netAmount, currency
            );
            settlementTransactionRepository.save(settlementTx);
            
            // Mark payment as settled
            payment.setStatus("SETTLED");
            payment.setSettledAt(OffsetDateTime.now());
            paymentRepository.save(payment);
        }
        
        logger.info("Created settlement batch {} with {} transactions totaling {}", 
                   batchId, transactionCount, totalAmount);
        
        return batch;
    }
    
    /**
     * Calculate processing fee (simplified - 2.9% + $0.30)
     */
    private BigDecimal calculateFee(BigDecimal amount) {
        BigDecimal percentageFee = amount.multiply(new BigDecimal("0.029"));
        BigDecimal fixedFee = new BigDecimal("0.30");
        return percentageFee.add(fixedFee);
    }
    
    /**
     * Submit settlement batch to acquirer
     */
    @Transactional
    public void submitBatchToAcquirer(String batchId) {
        SettlementBatch batch = batchRepository.findByBatchId(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        
        if (batch.getStatus() != SettlementStatus.PENDING) {
            throw new IllegalStateException("Batch is not in PENDING status");
        }
        
        try {
            // Generate settlement file
            String settlementFile = generateSettlementFile(batch);
            
            // Submit to acquirer (simulated)
            String acquirerBatchId = submitToAcquirer(settlementFile);
            
            // Update batch status
            batch.setStatus(SettlementStatus.PROCESSING);
            batch.setAcquirerBatchId(acquirerBatchId);
            batch.setProcessedAt(OffsetDateTime.now());
            batchRepository.save(batch);
            
            logger.info("Submitted batch {} to acquirer with ID {}", batchId, acquirerBatchId);
        } catch (Exception e) {
            batch.setStatus(SettlementStatus.FAILED);
            batchRepository.save(batch);
            logger.error("Failed to submit batch {} to acquirer", batchId, e);
            throw new RuntimeException("Failed to submit batch to acquirer", e);
        }
    }
    
    /**
     * Generate settlement file in acquirer format
     */
    private String generateSettlementFile(SettlementBatch batch) {
        List<SettlementTransaction> transactions = settlementTransactionRepository.findByBatchId(batch.getId());
        
        StringBuilder file = new StringBuilder();
        file.append("BATCH_ID,MERCHANT_ID,SETTLEMENT_DATE,CURRENCY,TOTAL_AMOUNT,TRANSACTION_COUNT\n");
        file.append(String.format("%s,%s,%s,%s,%s,%d\n",
            batch.getBatchId(),
            batch.getMerchantId(),
            batch.getSettlementDate(),
            batch.getCurrency(),
            batch.getTotalAmount(),
            batch.getTransactionCount()
        ));
        
        file.append("\nTRANSACTIONS\n");
        file.append("PAYMENT_ID,GROSS_AMOUNT,FEE_AMOUNT,NET_AMOUNT\n");
        
        for (SettlementTransaction tx : transactions) {
            Payment payment = paymentRepository.findById(tx.getPaymentId()).orElse(null);
            if (payment != null) {
                file.append(String.format("%s,%s,%s,%s\n",
                    payment.getPaymentId(),
                    tx.getGrossAmount(),
                    tx.getFeeAmount(),
                    tx.getNetAmount()
                ));
            }
        }
        
        return file.toString();
    }
    
    /**
     * Submit settlement file to acquirer (simulated)
     */
    private String submitToAcquirer(String settlementFile) {
        // In production, this would use SFTP or API to submit to acquirer
        // For now, simulate successful submission
        return "ACQ_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Reconcile settlement batch with acquirer report
     */
    @Transactional
    public void reconcileBatch(String batchId, String acquirerReport) {
        SettlementBatch batch = batchRepository.findByBatchId(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        
        // Parse acquirer report and compare with our records
        // This is simplified - in production would parse actual acquirer format
        List<SettlementTransaction> ourTransactions = settlementTransactionRepository.findByBatchId(batch.getId());
        BigDecimal ourTotal = ourTransactions.stream()
            .map(SettlementTransaction::getGrossAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Verify totals match
        if (ourTotal.compareTo(batch.getTotalAmount()) == 0) {
            batch.setStatus(SettlementStatus.SETTLED);
            batch.setUpdatedAt(OffsetDateTime.now());
            batchRepository.save(batch);
            logger.info("Batch {} reconciled successfully", batchId);
        } else {
            logger.error("Reconciliation mismatch for batch {}: expected {}, got {}", 
                        batchId, batch.getTotalAmount(), ourTotal);
            throw new IllegalStateException("Reconciliation mismatch");
        }
    }
    
    /**
     * Get settlement batch by ID
     */
    public SettlementBatch getBatch(String batchId) {
        return batchRepository.findByBatchId(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
    }
    
    /**
     * Get all settlement transactions for a batch
     */
    public List<SettlementTransaction> getBatchTransactions(String batchId) {
        SettlementBatch batch = getBatch(batchId);
        return settlementTransactionRepository.findByBatchId(batch.getId());
    }
}
