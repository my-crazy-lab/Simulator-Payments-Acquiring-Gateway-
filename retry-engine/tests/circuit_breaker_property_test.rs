use proptest::prelude::*;
use retry_engine::{CircuitBreakerConfig, circuit_breaker::{CircuitBreaker, CircuitState}};

/**
 * Feature: payment-acquiring-gateway, Property 18: Circuit Breaker Opens on Threshold
 * 
 * For any PSP that fails more than the configured threshold (e.g., 5 consecutive failures),
 * the circuit breaker should transition to OPEN state.
 * 
 * Validates: Requirements 9.2
 */

proptest! {
    #![proptest_config(ProptestConfig::with_cases(100))]
    
    #[test]
    fn circuit_opens_after_threshold_failures(
        failure_threshold in 2u32..10u32,
        success_threshold in 2u32..5u32,
        timeout_ms in 1000u64..10000u64,
    ) {
        let config = CircuitBreakerConfig {
            failure_threshold,
            success_threshold,
            timeout_duration_ms: timeout_ms,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Property: Circuit should be closed initially
        prop_assert_eq!(cb.get_state().state, CircuitState::Closed);
        prop_assert!(cb.can_proceed());
        
        // Property: Circuit should remain closed for failures < threshold
        for i in 1..failure_threshold {
            cb.record_failure();
            let state = cb.get_state();
            prop_assert_eq!(
                state.state, CircuitState::Closed,
                "Circuit should remain closed at failure {}/{}", i, failure_threshold
            );
            prop_assert_eq!(state.failure_count, i);
        }
        
        // Property: Circuit should open exactly at threshold
        cb.record_failure();
        let state = cb.get_state();
        prop_assert_eq!(
            state.state, CircuitState::Open,
            "Circuit should open at failure threshold {}", failure_threshold
        );
        prop_assert_eq!(state.failure_count, failure_threshold);
        
        // Property: Circuit should block requests when open
        prop_assert!(!cb.can_proceed(), "Circuit should block requests when open");
    }
    
    #[test]
    fn circuit_stays_closed_with_intermittent_successes(
        failure_threshold in 3u32..10u32,
        success_threshold in 2u32..5u32,
        num_cycles in 1usize..5usize,
    ) {
        let config = CircuitBreakerConfig {
            failure_threshold,
            success_threshold,
            timeout_duration_ms: 5000,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Property: If we never reach threshold consecutive failures, circuit stays closed
        for _ in 0..num_cycles {
            // Record failures up to (threshold - 1)
            for _ in 0..(failure_threshold - 1) {
                cb.record_failure();
            }
            
            // Record a success to reset
            cb.record_success();
            
            // Circuit should still be closed
            prop_assert_eq!(
                cb.get_state().state, CircuitState::Closed,
                "Circuit should remain closed with intermittent successes"
            );
        }
    }
    
    #[test]
    fn circuit_closes_after_success_threshold_in_half_open(
        failure_threshold in 2u32..5u32,
        success_threshold in 2u32..5u32,
    ) {
        let config = CircuitBreakerConfig {
            failure_threshold,
            success_threshold,
            timeout_duration_ms: 0, // Immediate timeout for testing
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Open the circuit
        for _ in 0..failure_threshold {
            cb.record_failure();
        }
        prop_assert_eq!(cb.get_state().state, CircuitState::Open);
        
        // Wait for timeout (immediate in this case)
        std::thread::sleep(std::time::Duration::from_millis(10));
        
        // Transition to half-open
        prop_assert!(cb.can_proceed());
        prop_assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
        
        // Property: Circuit should remain half-open for successes < threshold
        for i in 1..success_threshold {
            cb.record_success();
            let state = cb.get_state();
            prop_assert_eq!(
                state.state, CircuitState::HalfOpen,
                "Circuit should remain half-open at success {}/{}", i, success_threshold
            );
        }
        
        // Property: Circuit should close exactly at success threshold
        cb.record_success();
        prop_assert_eq!(
            cb.get_state().state, CircuitState::Closed,
            "Circuit should close at success threshold {}", success_threshold
        );
    }
    
    #[test]
    fn circuit_reopens_on_failure_in_half_open(
        failure_threshold in 2u32..5u32,
        success_threshold in 2u32..5u32,
        successes_before_failure in 0u32..3u32,
    ) {
        let config = CircuitBreakerConfig {
            failure_threshold,
            success_threshold,
            timeout_duration_ms: 0,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Open the circuit
        for _ in 0..failure_threshold {
            cb.record_failure();
        }
        
        // Transition to half-open
        std::thread::sleep(std::time::Duration::from_millis(10));
        prop_assert!(cb.can_proceed());
        prop_assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
        
        // Record some successes (but not enough to close)
        let successes = successes_before_failure.min(success_threshold - 1);
        for _ in 0..successes {
            cb.record_success();
        }
        
        // Property: Any failure in half-open should reopen the circuit
        cb.record_failure();
        prop_assert_eq!(
            cb.get_state().state, CircuitState::Open,
            "Circuit should reopen on failure in half-open state"
        );
    }
    
    #[test]
    fn circuit_blocks_requests_until_timeout(
        failure_threshold in 2u32..5u32,
        timeout_ms in 100u64..500u64,
    ) {
        let config = CircuitBreakerConfig {
            failure_threshold,
            success_threshold: 2,
            timeout_duration_ms: timeout_ms,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Open the circuit
        for _ in 0..failure_threshold {
            cb.record_failure();
        }
        prop_assert_eq!(cb.get_state().state, CircuitState::Open);
        
        // Property: Should block requests before timeout
        prop_assert!(!cb.can_proceed(), "Should block before timeout");
        
        // Wait for timeout
        std::thread::sleep(std::time::Duration::from_millis(timeout_ms + 50));
        
        // Property: Should allow requests after timeout (transitioning to half-open)
        prop_assert!(cb.can_proceed(), "Should allow requests after timeout");
        prop_assert_eq!(cb.get_state().state, CircuitState::HalfOpen);
    }
    
    #[test]
    fn failure_count_resets_on_success_in_closed_state(
        failure_threshold in 3u32..10u32,
        failures_before_success in 1u32..5u32,
    ) {
        let config = CircuitBreakerConfig {
            failure_threshold,
            success_threshold: 2,
            timeout_duration_ms: 5000,
        };
        
        let cb = CircuitBreaker::new(config);
        
        // Record some failures (but not enough to open)
        let failures = failures_before_success.min(failure_threshold - 1);
        for _ in 0..failures {
            cb.record_failure();
        }
        
        prop_assert_eq!(cb.get_state().failure_count, failures);
        
        // Property: Success should reset failure count in closed state
        cb.record_success();
        prop_assert_eq!(
            cb.get_state().failure_count, 0,
            "Failure count should reset to 0 after success in closed state"
        );
        prop_assert_eq!(cb.get_state().state, CircuitState::Closed);
    }
    
    #[test]
    fn circuit_state_transitions_are_deterministic(
        failure_threshold in 2u32..5u32,
        success_threshold in 2u32..4u32,
    ) {
        let config = CircuitBreakerConfig {
            failure_threshold,
            success_threshold,
            timeout_duration_ms: 0,
        };
        
        // Property: Same sequence of operations should produce same state transitions
        let cb1 = CircuitBreaker::new(config.clone());
        let cb2 = CircuitBreaker::new(config);
        
        // Open both circuits
        for _ in 0..failure_threshold {
            cb1.record_failure();
            cb2.record_failure();
        }
        
        prop_assert_eq!(cb1.get_state().state, cb2.get_state().state);
        
        // Transition to half-open
        std::thread::sleep(std::time::Duration::from_millis(10));
        cb1.can_proceed();
        cb2.can_proceed();
        
        prop_assert_eq!(cb1.get_state().state, cb2.get_state().state);
        
        // Close both circuits
        for _ in 0..success_threshold {
            cb1.record_success();
            cb2.record_success();
        }
        
        prop_assert_eq!(cb1.get_state().state, cb2.get_state().state);
        prop_assert_eq!(cb1.get_state().state, CircuitState::Closed);
    }
}
