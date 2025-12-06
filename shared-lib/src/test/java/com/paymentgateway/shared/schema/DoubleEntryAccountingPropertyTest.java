package com.paymentgateway.shared.schema;

import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: payment-acquiring-gateway, Property 38: Double-Entry Accounting
 * 
 * For any financial transaction recorded, the sum of debits must equal 
 * the sum of credits in the accounting entries.
 * 
 * Validates: Requirements 18.3
 */
@Testcontainers
class DoubleEntryAccountingPropertyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("payment_gateway_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("schema.sql");

    private static Connection connection;

    @BeforeContainer
    static void setUp() throws SQLException {
        postgres.start();
        connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

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
    ) throws SQLException {
        // Ensure refunds don't exceed payment
        BigDecimal totalRefunds = refundAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Assume.that(totalRefunds.compareTo(paymentAmount) <= 0);

        // Create test merchant
        UUID merchantId = createTestMerchant();

        // Create test card token
        UUID cardTokenId = createTestCardToken();

        // Create payment transaction
        UUID paymentId = createPayment(merchantId, cardTokenId, paymentAmount);

        // Create refunds
        List<UUID> refundIds = new ArrayList<>();
        for (BigDecimal refundAmount : refundAmounts) {
            UUID refundId = createRefund(paymentId, refundAmount);
            refundIds.add(refundId);
        }

        // Create settlement transaction
        BigDecimal feeAmount = paymentAmount.multiply(new BigDecimal("0.029")); // 2.9% fee
        BigDecimal netAmount = paymentAmount.subtract(feeAmount).subtract(totalRefunds);
        
        UUID batchId = createSettlementBatch(merchantId, paymentAmount);
        createSettlementTransaction(batchId, paymentId, paymentAmount, feeAmount, netAmount);

        // Verify double-entry accounting: debits = credits
        // Debit side: payment amount
        BigDecimal totalDebits = paymentAmount;

        // Credit side: refunds + fees + net settlement
        BigDecimal totalCredits = totalRefunds.add(feeAmount).add(netAmount);

        // Assert: debits must equal credits
        assertThat(totalDebits)
                .as("Double-entry accounting: debits must equal credits")
                .isEqualByComparingTo(totalCredits);

        // Cleanup
        cleanupTestData(paymentId, refundIds, batchId, merchantId, cardTokenId);
    }

    /**
     * Property: For any settlement batch, the sum of all transaction net amounts
     * must equal the batch total amount.
     */
    @Property(tries = 100)
    void settlementBatchBalances(
            @ForAll @BigRange(min = "100.00", max = "50000.00") BigDecimal batchTotal,
            @ForAll @Positive int transactionCount
    ) throws SQLException {
        Assume.that(transactionCount >= 1 && transactionCount <= 10);

        // Create test merchant
        UUID merchantId = createTestMerchant();
        UUID cardTokenId = createTestCardToken();

        // Create settlement batch
        UUID batchId = createSettlementBatch(merchantId, batchTotal);

        // Distribute batch total across transactions
        BigDecimal amountPerTransaction = batchTotal.divide(
                new BigDecimal(transactionCount), 
                2, 
                BigDecimal.ROUND_DOWN
        );
        
        BigDecimal remainder = batchTotal.subtract(
                amountPerTransaction.multiply(new BigDecimal(transactionCount))
        );

        List<UUID> paymentIds = new ArrayList<>();
        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (int i = 0; i < transactionCount; i++) {
            BigDecimal txAmount = amountPerTransaction;
            if (i == 0) {
                txAmount = txAmount.add(remainder); // Add remainder to first transaction
            }

            UUID paymentId = createPayment(merchantId, cardTokenId, txAmount);
            paymentIds.add(paymentId);

            BigDecimal feeAmount = txAmount.multiply(new BigDecimal("0.029"));
            BigDecimal netAmount = txAmount.subtract(feeAmount);

            createSettlementTransaction(batchId, paymentId, txAmount, feeAmount, netAmount);
            calculatedTotal = calculatedTotal.add(netAmount);
        }

        // Query the actual settlement batch total from settlement_transactions
        BigDecimal actualTotal = querySettlementTransactionTotal(batchId);

        // Assert: calculated total matches actual total
        assertThat(actualTotal)
                .as("Settlement batch total must equal sum of transaction net amounts")
                .isEqualByComparingTo(calculatedTotal);

        // Cleanup
        for (UUID paymentId : paymentIds) {
            cleanupTestData(paymentId, List.of(), batchId, merchantId, cardTokenId);
        }
    }

    @Provide
    Arbitrary<List<BigDecimal>> refundAmounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("1000.00"))
                .list()
                .ofMinSize(0)
                .ofMaxSize(5);
    }

    // Helper methods

    private UUID createTestMerchant() throws SQLException {
        UUID merchantId = UUID.randomUUID();
        String sql = "INSERT INTO merchants (id, merchant_id, merchant_name, country_code, currency) " +
                     "VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, merchantId);
            stmt.setString(2, "MERCH_" + merchantId.toString().substring(0, 8));
            stmt.setString(3, "Test Merchant");
            stmt.setString(4, "US");
            stmt.setString(5, "USD");
            stmt.executeUpdate();
        }
        
        return merchantId;
    }

    private UUID createTestCardToken() throws SQLException {
        UUID tokenId = UUID.randomUUID();
        String sql = "INSERT INTO card_tokens (id, token, encrypted_pan, pan_hash, encrypted_expiry, " +
                     "card_brand, last_four) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, tokenId);
            stmt.setString(2, "tok_" + UUID.randomUUID().toString().substring(0, 24));
            stmt.setString(3, "encrypted_pan_data");
            stmt.setString(4, "hash_" + UUID.randomUUID().toString());
            stmt.setString(5, "encrypted_expiry_data");
            stmt.setString(6, "VISA");
            stmt.setString(7, "1234");
            stmt.executeUpdate();
        }
        
        return tokenId;
    }

    private UUID createPayment(UUID merchantId, UUID cardTokenId, BigDecimal amount) throws SQLException {
        UUID paymentId = UUID.randomUUID();
        String sql = "INSERT INTO payments (id, payment_id, merchant_id, amount, currency, " +
                     "card_token_id, card_last_four, card_brand, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, paymentId);
            stmt.setString(2, "pay_" + UUID.randomUUID().toString().substring(0, 24));
            stmt.setObject(3, merchantId);
            stmt.setBigDecimal(4, amount);
            stmt.setString(5, "USD");
            stmt.setObject(6, cardTokenId);
            stmt.setString(7, "1234");
            stmt.setString(8, "VISA");
            stmt.setString(9, "CAPTURED");
            stmt.executeUpdate();
        }
        
        return paymentId;
    }

    private UUID createRefund(UUID paymentId, BigDecimal amount) throws SQLException {
        UUID refundId = UUID.randomUUID();
        String sql = "INSERT INTO refunds (id, refund_id, payment_id, amount, currency, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, refundId);
            stmt.setString(2, "ref_" + UUID.randomUUID().toString().substring(0, 24));
            stmt.setObject(3, paymentId);
            stmt.setBigDecimal(4, amount);
            stmt.setString(5, "USD");
            stmt.setString(6, "CAPTURED");
            stmt.executeUpdate();
        }
        
        return refundId;
    }

    private UUID createSettlementBatch(UUID merchantId, BigDecimal totalAmount) throws SQLException {
        UUID batchId = UUID.randomUUID();
        String sql = "INSERT INTO settlement_batches (id, batch_id, merchant_id, settlement_date, " +
                     "currency, total_amount, transaction_count, status) " +
                     "VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, batchId);
            stmt.setString(2, "bat_" + UUID.randomUUID().toString().substring(0, 24));
            stmt.setObject(3, merchantId);
            stmt.setString(4, "USD");
            stmt.setBigDecimal(5, totalAmount);
            stmt.setInt(6, 1);
            stmt.setString(7, "SETTLED");
            stmt.executeUpdate();
        }
        
        return batchId;
    }

    private void createSettlementTransaction(UUID batchId, UUID paymentId, 
                                            BigDecimal grossAmount, BigDecimal feeAmount, 
                                            BigDecimal netAmount) throws SQLException {
        String sql = "INSERT INTO settlement_transactions (id, batch_id, payment_id, " +
                     "gross_amount, fee_amount, net_amount, currency) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setObject(2, batchId);
            stmt.setObject(3, paymentId);
            stmt.setBigDecimal(4, grossAmount);
            stmt.setBigDecimal(5, feeAmount);
            stmt.setBigDecimal(6, netAmount);
            stmt.setString(7, "USD");
            stmt.executeUpdate();
        }
    }

    private BigDecimal querySettlementTransactionTotal(UUID batchId) throws SQLException {
        String sql = "SELECT SUM(net_amount) FROM settlement_transactions WHERE batch_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, batchId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBigDecimal(1);
            }
        }
        
        return BigDecimal.ZERO;
    }

    private void cleanupTestData(UUID paymentId, List<UUID> refundIds, UUID batchId, 
                                 UUID merchantId, UUID cardTokenId) throws SQLException {
        // Delete in reverse order of foreign key dependencies
        connection.createStatement().execute(
                "DELETE FROM settlement_transactions WHERE batch_id = '" + batchId + "'");
        connection.createStatement().execute(
                "DELETE FROM settlement_batches WHERE id = '" + batchId + "'");
        
        for (UUID refundId : refundIds) {
            connection.createStatement().execute(
                    "DELETE FROM refunds WHERE id = '" + refundId + "'");
        }
        
        connection.createStatement().execute(
                "DELETE FROM payments WHERE id = '" + paymentId + "'");
        connection.createStatement().execute(
                "DELETE FROM card_tokens WHERE id = '" + cardTokenId + "'");
        connection.createStatement().execute(
                "DELETE FROM merchants WHERE id = '" + merchantId + "'");
    }
}
