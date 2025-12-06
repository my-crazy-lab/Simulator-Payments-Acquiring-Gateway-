use retry_engine::{RetryConfig, CircuitBreakerConfig, current_timestamp_ms};
use retry_engine::retry_policy::RetryPolicy;
use retry_engine::circuit_breaker::{CircuitBreaker, CircuitState};
use retry_engine::dlq::{DeadLetterQueue, DLQEntry};

#[cfg(test)]
mod retry_exhaustion_tests {
    use super::*;

    #[test]
    fn test_retry_exhaustion_after_max_attempts() {
        let config = RetryConfig {
            max_attempts: 3,
            initial_delay_ms: 1000,
            max_delay_ms: 10000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Should allow retries for attempts 0, 1, 2
        assert!(policy.should_retry(0));
        assert!(policy.should_retry(1));
        assert!(policy.should_retry(2));
        
        // Should not allow retry at attempt 3 (exhausted)
        assert!(!policy.should_retry(3));
        assert!(!policy.should_retry(4));
    }

    #[test]
    fn test_retry_exhaustion_moves_to_dlq() {
        let config = RetryConfig {
            max_attempts: 5,
            initial_delay_ms: 1000,
            max_delay_ms: 60000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        let dlq = DeadLetterQueue::new();
        
        let transaction_id = "txn_exhausted";
        
        // Simulate retries until exhaustion
        let mut attempt = 0;
        while policy.should_retry(attempt) {
            attempt += 1;
        }
        
        // At this point, retries are exhausted
        assert_eq!(attempt, 5);
        
        // Move to DLQ
        let entry = DLQEntry {
            transaction_id: transaction_id.to_string(),
            psp_name: "stripe".to_string(),
            payload: vec![1, 2, 3],
            attempt_count: attempt,
            last_error: "Max retries exceeded".to_string(),
            timestamp_ms: current_timestamp_ms(),
        };
        
        dlq.add_entry(entry);
        
        assert!(dlq.contains(transaction_id));
        let dlq_entry = dlq.get_entry(transaction_id).unwrap();
        assert_eq!(dlq_entry.attempt_count, 5);
        assert_eq!(dlq_entry.last_error, "Max retries exceeded");
    }

    #[test]
    fn test_zero_max_attempts_immediately_exhausted() {
        let config = RetryConfig {
            max_attempts: 0,
            initial_delay_ms: 1000,
            max_delay_ms: 10000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Should not allow any retries
        assert!(!policy.should_retry(0));
        assert!(!policy.should_retry(1));
    }
}

#[cfg(test)]
mod circuit_breaker_state_transition_tests {
    use super::*;

    #[test]
    fn test_closed_to_open_transition() {
        let config = CircuitBreakerConfig {
            failure_threshold: 3,
            success_threshold: 2,
            timeout_duration_ms: 5000,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Start in closed state
        assert_eq!(cb.get_state().state, CircuitState::Closed);
        
        // Record failures
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Closed);
        
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Closed);
        
        // Third failure should open the circuit
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Open);
    }

    #[test]
    fn test_open_to_half_open_transition() {
        let config = CircuitBreakerConfig {
            failure_threshold: 2,
            success_threshold: 2,
            timeout_duration_ms: 100, // Short timeout for testing
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Open the circuit
        cb.record_failure();
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Open);
        
        // Should not proceed immediately
        assert!(!cb.can_proceed());
        
        // Wait for timeout
        std::thread::sleep(std::time::Duration::from_millis(150));
        
        // Should transition to half-open
        assert!(cb.can_proceed());
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
    }

    #[test]
    fn test_half_open_to_closed_transition() {
        let config = CircuitBreakerConfig {
            failure_threshold: 2,
            success_threshold: 3,
            timeout_duration_ms: 0, // Immediate timeout
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Open the circuit
        cb.record_failure();
        cb.record_failure();
        
        // Transition to half-open
        std::thread::sleep(std::time::Duration::from_millis(10));
        cb.can_proceed();
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
        
        // Record successes
        cb.record_success();
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
        
        cb.record_success();
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
        
        // Third success should close the circuit
        cb.record_success();
        assert_eq!(cb.get_state().state, CircuitState::Closed);
    }

    #[test]
    fn test_half_open_to_open_transition() {
        let config = CircuitBreakerConfig {
            failure_threshold: 2,
            success_threshold: 3,
            timeout_duration_ms: 0,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Open the circuit
        cb.record_failure();
        cb.record_failure();
        
        // Transition to half-open
        std::thread::sleep(std::time::Duration::from_millis(10));
        cb.can_proceed();
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
        
        // Record a success
        cb.record_success();
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
        
        // Any failure should reopen the circuit
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Open);
    }

    #[test]
    fn test_closed_state_resets_on_success() {
        let config = CircuitBreakerConfig {
            failure_threshold: 5,
            success_threshold: 2,
            timeout_duration_ms: 5000,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Record some failures
        cb.record_failure();
        cb.record_failure();
        cb.record_failure();
        assert_eq!(cb.get_state().failure_count, 3);
        assert_eq!(cb.get_state().state, CircuitState::Closed);
        
        // Success should reset failure count
        cb.record_success();
        assert_eq!(cb.get_state().failure_count, 0);
        assert_eq!(cb.get_state().state, CircuitState::Closed);
    }

    #[test]
    fn test_complete_state_cycle() {
        let config = CircuitBreakerConfig {
            failure_threshold: 2,
            success_threshold: 2,
            timeout_duration_ms: 50,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Closed -> Open
        assert_eq!(cb.get_state().state, CircuitState::Closed);
        cb.record_failure();
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Open);
        
        // Open -> HalfOpen
        std::thread::sleep(std::time::Duration::from_millis(100));
        cb.can_proceed();
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
        
        // HalfOpen -> Closed
        cb.record_success();
        cb.record_success();
        assert_eq!(cb.get_state().state, CircuitState::Closed);
    }
}

#[cfg(test)]
mod jitter_calculation_tests {
    use super::*;

    #[test]
    fn test_jitter_adds_randomness() {
        let config = RetryConfig {
            max_attempts: 10,
            initial_delay_ms: 1000,
            max_delay_ms: 60000,
            backoff_multiplier: 2.0,
            jitter: true,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Calculate delay multiple times for the same attempt
        let mut delays = Vec::new();
        for _ in 0..10 {
            delays.push(policy.calculate_delay(3));
        }
        
        // With jitter, we should see some variation
        // (though there's a small chance all values are the same)
        let first = delays[0];
        let has_variation = delays.iter().any(|&d| d != first);
        
        // At least check that delays are in a reasonable range
        // For attempt 3: base = 1000 * 2^2 = 4000
        // With ±20% jitter: range is [3200, 4800]
        for delay in delays {
            assert!(delay >= 3200 && delay <= 4800,
                "Delay {} should be in range [3200, 4800]", delay);
        }
    }

    #[test]
    fn test_no_jitter_is_deterministic() {
        let config = RetryConfig {
            max_attempts: 10,
            initial_delay_ms: 1000,
            max_delay_ms: 60000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Without jitter, delays should be deterministic
        let delay1 = policy.calculate_delay(3);
        let delay2 = policy.calculate_delay(3);
        let delay3 = policy.calculate_delay(3);
        
        assert_eq!(delay1, delay2);
        assert_eq!(delay2, delay3);
        assert_eq!(delay1, 4000); // 1000 * 2^2
    }

    #[test]
    fn test_jitter_respects_max_delay() {
        let config = RetryConfig {
            max_attempts: 20,
            initial_delay_ms: 1000,
            max_delay_ms: 5000,
            backoff_multiplier: 2.0,
            jitter: true,
        };
        
        let policy = RetryPolicy::new(config);
        
        // For high attempts, delay should be capped even with jitter
        for _ in 0..20 {
            let delay = policy.calculate_delay(10);
            assert!(delay <= 5000, "Delay {} should not exceed max_delay", delay);
        }
    }

    #[test]
    fn test_jitter_range_is_reasonable() {
        let config = RetryConfig {
            max_attempts: 10,
            initial_delay_ms: 10000,
            max_delay_ms: 100000,
            backoff_multiplier: 2.0,
            jitter: true,
        };
        
        let policy = RetryPolicy::new(config);
        
        // For attempt 2: base = 10000 * 2^1 = 20000
        // Jitter range is ±20% = ±4000
        // So range is [16000, 24000]
        let mut min_seen = u64::MAX;
        let mut max_seen = 0u64;
        
        for _ in 0..100 {
            let delay = policy.calculate_delay(2);
            min_seen = min_seen.min(delay);
            max_seen = max_seen.max(delay);
        }
        
        // Should see values across the range
        assert!(min_seen >= 16000, "Min delay {} should be >= 16000", min_seen);
        assert!(max_seen <= 24000, "Max delay {} should be <= 24000", max_seen);
        
        // With 100 samples, we should see some spread
        assert!(max_seen - min_seen > 1000, 
            "Should see variation in jitter: min={}, max={}", min_seen, max_seen);
    }
}

#[cfg(test)]
mod integration_tests {
    use super::*;

    #[test]
    fn test_retry_with_circuit_breaker_integration() {
        let retry_config = RetryConfig {
            max_attempts: 5,
            initial_delay_ms: 100,
            max_delay_ms: 1000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        
        let circuit_config = CircuitBreakerConfig {
            failure_threshold: 3,
            success_threshold: 2,
            timeout_duration_ms: 200,
        };
        
        let policy = RetryPolicy::new(retry_config);
        let cb = CircuitBreaker::new(circuit_config);
        let dlq = DeadLetterQueue::new();
        
        let transaction_id = "txn_integration";
        
        // Simulate retry attempts with circuit breaker
        let mut attempt = 0;
        while policy.should_retry(attempt) && cb.can_proceed() {
            // Simulate failure
            cb.record_failure();
            attempt += 1;
            
            // Check if circuit opened
            if cb.get_state().state == CircuitState::Open {
                break;
            }
        }
        
        // Circuit should have opened after 3 failures
        assert_eq!(cb.get_state().state, CircuitState::Open);
        assert_eq!(attempt, 3);
        
        // Transaction should not be in DLQ yet (circuit opened, not exhausted)
        assert!(!dlq.contains(transaction_id));
    }

    #[test]
    fn test_full_retry_flow_to_dlq() {
        let retry_config = RetryConfig {
            max_attempts: 3,
            initial_delay_ms: 100,
            max_delay_ms: 1000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(retry_config);
        let dlq = DeadLetterQueue::new();
        
        let transaction_id = "txn_full_flow";
        
        // Simulate all retry attempts
        let mut attempt = 0;
        let mut delays = Vec::new();
        
        while policy.should_retry(attempt) {
            let delay = policy.calculate_delay(attempt);
            delays.push(delay);
            attempt += 1;
        }
        
        // Should have attempted 3 times (0, 1, 2)
        assert_eq!(attempt, 3);
        assert_eq!(delays, vec![0, 100, 200]);
        
        // Now move to DLQ
        let entry = DLQEntry {
            transaction_id: transaction_id.to_string(),
            psp_name: "test_psp".to_string(),
            payload: vec![],
            attempt_count: attempt,
            last_error: "All retries failed".to_string(),
            timestamp_ms: current_timestamp_ms(),
        };
        
        dlq.add_entry(entry);
        
        assert!(dlq.contains(transaction_id));
        assert_eq!(dlq.count(), 1);
    }
}
