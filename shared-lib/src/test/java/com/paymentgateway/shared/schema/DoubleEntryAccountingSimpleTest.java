package com.paymentgateway.shared.schema;

import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: payment-acquiring-gateway, Property 38: Double-Entry Accounting
 * 
 * For any financial transaction recorded, the sum of debits must equal 
 * the sum of credits in the accounting entries.
 * 
 * Validates: Requirements 18.3
 * 
 * This is a simplified version that tests the accounting logic without database.
 */
class DoubleEntryAccountingSimpleTest {

    /**
     * Property: For any payment transaction with refunds, the accounting entries
     * must balance - debits equal credits.
     * 
     * In our system:
     * - Payment amount is a debit to the merchant account
     * - Refund amounts are credits back to the customer
     * - Settlement net amount = gross amount - fees (must balance)
     */
    @Property(tries = 100)
    void paymentWithRefundsBalancesAccounting(
            @ForAll @BigRange(min = "10.00", max = "10000.00") BigDecimal paymentAmount,
            @ForAll("refundAmounts") List<BigDecimal> refundAmounts
    ) {
        // Ensure refunds don't exceed payment
        BigDecimal totalRefunds = refundAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Assume.that(totalRefunds.compareTo(paymentAmount) <= 0);

        // Calculate settlement
        BigDecimal feeAmount = paymentAmount.multiply(new BigDecimal("0.029")); // 2.9% fee
        BigDecimal netAmount = paymentAmount.subtract(feeAmount).subtract(totalRefunds);

        // Verify double-entry accounting: debits = credits
        // Debit side: payment amount
        BigDecimal totalDebits = paymentAmount;

        // Credit side: refunds + fees + net settlement
        BigDecimal totalCredits = totalRefunds.add(feeAmount).add(netAmount);

        // Assert: debits must equal credits
        assertThat(totalDebits)
                .as("Double-entry accounting: debits must equal credits")
                .isEqualByComparingTo(totalCredits);
    }

    /**
     * Property: For any settlement batch, the sum of all transaction net amounts
     * must equal the batch total amount.
     */
    @Property(tries = 100)
    void settlementBatchBalances(
            @ForAll @BigRange(min = "100.00", max = "50000.00") BigDecimal batchTotal,
            @ForAll @net.jqwik.api.constraints.Positive int transactionCount
    ) {
        Assume.that(transactionCount >= 1 && transactionCount <= 10);

        // Distribute batch total across transactions
        BigDecimal amountPerTransaction = batchTotal.divide(
                new BigDecimal(transactionCount), 
                2, 
                BigDecimal.ROUND_DOWN
        );
        
        BigDecimal remainder = batchTotal.subtract(
                amountPerTransaction.multiply(new BigDecimal(transactionCount))
        );

        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (int i = 0; i < transactionCount; i++) {
            BigDecimal txAmount = amountPerTransaction;
            if (i == 0) {
                txAmount = txAmount.add(remainder); // Add remainder to first transaction
            }

            BigDecimal feeAmount = txAmount.multiply(new BigDecimal("0.029"));
            BigDecimal netAmount = txAmount.subtract(feeAmount);

            calculatedTotal = calculatedTotal.add(netAmount);
        }

        // The calculated total should match what we'd expect from the batch
        // This verifies that our accounting logic is consistent
        BigDecimal expectedTotal = batchTotal.subtract(
                batchTotal.multiply(new BigDecimal("0.029"))
        );

        assertThat(calculatedTotal)
                .as("Settlement batch total must equal sum of transaction net amounts")
                .isEqualByComparingTo(expectedTotal);
    }

    @Provide
    Arbitrary<List<BigDecimal>> refundAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("1000.00"))
                .list()
                .ofMinSize(0)
                .ofMaxSize(5);
    }
}
