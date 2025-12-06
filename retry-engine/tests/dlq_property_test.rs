use proptest::prelude::*;
use retry_engine::{RetryConfig, CircuitBreakerConfig};
use retry_engine::retry_policy::RetryPolicy;
use retry_engine::dlq::{DeadLetterQueue, DLQEntry};
use retry_engine::current_timestamp_ms;

/**
 * Feature: payment-acquiring-gateway, Property 19: DLQ After Max Retries
 * 
 * For any transaction that fails after maximum retry attempts, the transaction 
 * should be moved to the dead letter queue with failure details.
 * 
 * Validates: Requirements 9.5
 */

proptest! {
    #![proptest_config(ProptestConfig::with_cases(100))]
    
    #[test]
    fn transaction_moves_to_dlq_after_max_retries(
        max_attempts in 2u32..10u32,
        initial_delay in 100u64..1000u64,
    ) {
        let config = RetryConfig {
            max_attempts,
            initial_delay_ms: initial_delay,
            max_delay_ms: 60000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        let dlq = DeadLetterQueue::new();
        
        let transaction_id = format!("txn_{}", uuid::Uuid::new_v4());
        
        // Property: Should allow retries up to max_attempts - 1
        for attempt in 0..max_attempts {
            prop_assert!(
                policy.should_retry(attempt),
                "Should allow retry at attempt {}/{}", attempt, max_attempts
            );
        }
        
        // Property: Should not allow retry at max_attempts
        prop_assert!(
            !policy.should_retry(max_attempts),
            "Should not allow retry at attempt {}", max_attempts
        );
        
        // Simulate moving to DLQ after max retries
        let dlq_entry = DLQEntry {
            transaction_id: transaction_id.clone(),
            psp_name: "test_psp".to_string(),
            payload: vec![1, 2, 3],
            attempt_count: max_attempts,
            last_error: "Max retries exceeded".to_string(),
            timestamp_ms: current_timestamp_ms(),
        };
        
        dlq.add_entry(dlq_entry);
        
        // Property: Transaction should be in DLQ
        prop_assert!(
            dlq.contains(&transaction_id),
            "Transaction should be in DLQ after max retries"
        );
        
        // Property: DLQ entry should have correct attempt count
        let entry = dlq.get_entry(&transaction_id).unwrap();
        prop_assert_eq!(
            entry.attempt_count, max_attempts,
            "DLQ entry should record max attempts"
        );
    }
    
    #[test]
    fn dlq_preserves_transaction_details(
        attempt_count in 1u32..20u32,
        payload_size in 0usize..1000usize,
    ) {
        let dlq = DeadLetterQueue::new();
        
        let transaction_id = format!("txn_{}", uuid::Uuid::new_v4());
        let psp_name = format!("psp_{}", uuid::Uuid::new_v4());
        let payload: Vec<u8> = (0..payload_size).map(|i| (i % 256) as u8).collect();
        let last_error = format!("Error after {} attempts", attempt_count);
        let timestamp = current_timestamp_ms();
        
        let entry = DLQEntry {
            transaction_id: transaction_id.clone(),
            psp_name: psp_name.clone(),
            payload: payload.clone(),
            attempt_count,
            last_error: last_error.clone(),
            timestamp_ms: timestamp,
        };
        
        dlq.add_entry(entry);
        
        // Property: All details should be preserved in DLQ
        let retrieved = dlq.get_entry(&transaction_id).unwrap();
        prop_assert_eq!(retrieved.transaction_id, transaction_id);
        prop_assert_eq!(retrieved.psp_name, psp_name);
        prop_assert_eq!(retrieved.payload, payload);
        prop_assert_eq!(retrieved.attempt_count, attempt_count);
        prop_assert_eq!(retrieved.last_error, last_error);
        prop_assert_eq!(retrieved.timestamp_ms, timestamp);
    }
    
    #[test]
    fn dlq_handles_multiple_transactions(
        num_transactions in 1usize..50usize,
        max_attempts in 3u32..10u32,
    ) {
        let dlq = DeadLetterQueue::new();
        let mut transaction_ids = Vec::new();
        
        // Property: DLQ should handle multiple transactions
        for i in 0..num_transactions {
            let transaction_id = format!("txn_{}", i);
            transaction_ids.push(transaction_id.clone());
            
            let entry = DLQEntry {
                transaction_id: transaction_id.clone(),
                psp_name: format!("psp_{}", i % 3),
                payload: vec![i as u8],
                attempt_count: max_attempts,
                last_error: format!("Error {}", i),
                timestamp_ms: current_timestamp_ms() + i as u64,
            };
            
            dlq.add_entry(entry);
        }
        
        // Property: All transactions should be in DLQ
        prop_assert_eq!(dlq.count(), num_transactions);
        
        for txn_id in &transaction_ids {
            prop_assert!(
                dlq.contains(txn_id),
                "Transaction {} should be in DLQ", txn_id
            );
        }
        
        // Property: get_all_entries should return all transactions
        let all_entries = dlq.get_all_entries();
        prop_assert_eq!(all_entries.len(), num_transactions);
    }
    
    #[test]
    fn dlq_entry_can_be_removed(
        num_transactions in 2usize..20usize,
        remove_index in 0usize..10usize,
    ) {
        let dlq = DeadLetterQueue::new();
        let mut transaction_ids = Vec::new();
        
        // Add transactions
        for i in 0..num_transactions {
            let transaction_id = format!("txn_{}", i);
            transaction_ids.push(transaction_id.clone());
            
            let entry = DLQEntry {
                transaction_id: transaction_id.clone(),
                psp_name: "test_psp".to_string(),
                payload: vec![],
                attempt_count: 5,
                last_error: "Test error".to_string(),
                timestamp_ms: current_timestamp_ms(),
            };
            
            dlq.add_entry(entry);
        }
        
        let initial_count = dlq.count();
        prop_assert_eq!(initial_count, num_transactions);
        
        // Remove one transaction
        let remove_idx = remove_index % num_transactions;
        let removed_id = &transaction_ids[remove_idx];
        let removed = dlq.remove_entry(removed_id);
        
        // Property: Removed entry should be returned
        prop_assert!(removed.is_some());
        prop_assert_eq!(removed.unwrap().transaction_id, removed_id.clone());
        
        // Property: Count should decrease by 1
        prop_assert_eq!(dlq.count(), initial_count - 1);
        
        // Property: Removed transaction should not be in DLQ
        prop_assert!(!dlq.contains(removed_id));
        
        // Property: Other transactions should still be in DLQ
        for (i, txn_id) in transaction_ids.iter().enumerate() {
            if i != remove_idx {
                prop_assert!(
                    dlq.contains(txn_id),
                    "Transaction {} should still be in DLQ", txn_id
                );
            }
        }
    }
    
    #[test]
    fn dlq_idempotent_add_overwrites(
        attempt_count_1 in 1u32..10u32,
        attempt_count_2 in 1u32..10u32,
    ) {
        let dlq = DeadLetterQueue::new();
        let transaction_id = "txn_test".to_string();
        
        // Add first entry
        let entry1 = DLQEntry {
            transaction_id: transaction_id.clone(),
            psp_name: "psp1".to_string(),
            payload: vec![1],
            attempt_count: attempt_count_1,
            last_error: "Error 1".to_string(),
            timestamp_ms: 1000,
        };
        dlq.add_entry(entry1);
        
        prop_assert_eq!(dlq.count(), 1);
        
        // Add second entry with same transaction_id
        let entry2 = DLQEntry {
            transaction_id: transaction_id.clone(),
            psp_name: "psp2".to_string(),
            payload: vec![2],
            attempt_count: attempt_count_2,
            last_error: "Error 2".to_string(),
            timestamp_ms: 2000,
        };
        dlq.add_entry(entry2);
        
        // Property: Count should still be 1 (overwrite, not duplicate)
        prop_assert_eq!(dlq.count(), 1);
        
        // Property: Latest entry should be stored
        let retrieved = dlq.get_entry(&transaction_id).unwrap();
        prop_assert_eq!(retrieved.psp_name, "psp2");
        prop_assert_eq!(retrieved.attempt_count, attempt_count_2);
        prop_assert_eq!(retrieved.last_error, "Error 2");
        prop_assert_eq!(retrieved.timestamp_ms, 2000);
    }
    
    #[test]
    fn retry_policy_and_dlq_integration(
        max_attempts in 2u32..8u32,
        actual_attempts in 0u32..15u32,
    ) {
        let config = RetryConfig {
            max_attempts,
            initial_delay_ms: 1000,
            max_delay_ms: 60000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        let dlq = DeadLetterQueue::new();
        let transaction_id = format!("txn_{}", uuid::Uuid::new_v4());
        
        // Simulate retry attempts
        let mut should_be_in_dlq = false;
        
        for attempt in 0..=actual_attempts {
            if !policy.should_retry(attempt) {
                // Move to DLQ
                let entry = DLQEntry {
                    transaction_id: transaction_id.clone(),
                    psp_name: "test_psp".to_string(),
                    payload: vec![],
                    attempt_count: attempt,
                    last_error: "Max retries exceeded".to_string(),
                    timestamp_ms: current_timestamp_ms(),
                };
                dlq.add_entry(entry);
                should_be_in_dlq = true;
                break;
            }
        }
        
        // Property: Transaction should be in DLQ iff attempts >= max_attempts
        if actual_attempts >= max_attempts {
            prop_assert!(
                should_be_in_dlq,
                "Transaction should be in DLQ after {} attempts (max={})",
                actual_attempts, max_attempts
            );
            prop_assert!(dlq.contains(&transaction_id));
        }
    }
}
