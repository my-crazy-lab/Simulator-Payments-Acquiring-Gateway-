-- Performance optimization indexes for Payment Acquiring Gateway
-- Requirements: 20.4 - Optimized indexes for payment workflows

-- Composite index for settlement queries
-- Supports: findPaymentsForSettlement
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_settlement 
ON payments(merchant_id, status, captured_at) 
WHERE status = 'CAPTURED' AND settled_at IS NULL;

-- Composite index for merchant dashboard queries
-- Supports: getPaymentStatsByMerchant, getVolumeByMerchantAndCurrency
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_merchant_stats 
ON payments(merchant_id, created_at, status, currency, amount);

-- Index for fraud velocity checks
-- Supports: countRecentPaymentsByCard
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_card_velocity 
ON payments(card_token_id, created_at DESC);

-- Index for PSP reconciliation
-- Supports: findByPspTransactionId
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_psp_txn 
ON payments(psp_transaction_id) WHERE psp_transaction_id IS NOT NULL;

-- Index for idempotency checks
-- Supports: existsByReferenceIdAndMerchantId
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_reference 
ON payments(merchant_id, reference_id) WHERE reference_id IS NOT NULL;

-- Partial index for pending payments (frequently queried)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_pending 
ON payments(merchant_id, created_at DESC) 
WHERE status = 'PENDING';

-- Partial index for authorized payments awaiting capture
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_authorized 
ON payments(merchant_id, authorized_at DESC) 
WHERE status = 'AUTHORIZED';

-- Index for payment events audit trail
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_events_payment_created 
ON payment_events(payment_id, created_at DESC);

-- Index for payment events by type (for analytics)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_events_type_created 
ON payment_events(event_type, created_at DESC);

-- Index for refunds by status
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refunds_status_created 
ON refunds(status, created_at DESC);

-- Index for settlement batches by date
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_settlement_batches_date 
ON settlement_batches(settlement_date DESC, merchant_id);

-- Index for disputes by deadline (for SLA tracking)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_disputes_deadline 
ON disputes(deadline, status) 
WHERE status IN ('OPEN', 'PENDING_EVIDENCE');

-- Index for fraud alerts requiring review
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_alerts_review 
ON fraud_alerts(created_at DESC, status) 
WHERE status = 'OPEN';

-- Analyze tables to update statistics after index creation
ANALYZE payments;
ANALYZE payment_events;
ANALYZE refunds;
ANALYZE settlement_batches;
ANALYZE disputes;
ANALYZE fraud_alerts;
