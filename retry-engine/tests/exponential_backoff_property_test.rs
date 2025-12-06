use proptest::prelude::*;
use retry_engine::{RetryConfig, retry_policy::RetryPolicy};

/**
 * Feature: payment-acquiring-gateway, Property 17: Exponential Backoff Timing
 * 
 * For any retry sequence, the delay between retry attempts should increase 
 * exponentially (e.g., 1s, 2s, 4s, 8s) up to a maximum delay.
 * 
 * Validates: Requirements 9.1
 */

proptest! {
    #![proptest_config(ProptestConfig::with_cases(100))]
    
    #[test]
    fn exponential_backoff_increases_exponentially(
        initial_delay in 100u64..5000u64,
        max_delay in 10000u64..100000u64,
        multiplier in 1.5f64..3.0f64,
        max_attempts in 3u32..10u32,
    ) {
        // Create config without jitter for predictable testing
        let config = RetryConfig {
            max_attempts,
            initial_delay_ms: initial_delay,
            max_delay_ms: max_delay,
            backoff_multiplier: multiplier,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Property: Each delay should be approximately multiplier times the previous delay
        // (until we hit the max_delay cap)
        let mut prev_delay = 0u64;
        
        for attempt in 1..max_attempts {
            let current_delay = policy.calculate_delay(attempt);
            
            // First attempt should be initial_delay
            if attempt == 1 {
                prop_assert_eq!(current_delay, initial_delay);
            } else {
                // Current delay should be either:
                // 1. prev_delay * multiplier (if under max)
                // 2. max_delay (if capped)
                let expected_delay = (prev_delay as f64 * multiplier) as u64;
                
                if expected_delay <= max_delay {
                    // Should be exponentially larger
                    prop_assert!(
                        current_delay >= prev_delay,
                        "Delay should increase: attempt={}, prev={}, current={}", 
                        attempt, prev_delay, current_delay
                    );
                    
                    // Should be approximately multiplier times previous
                    // Allow 5ms tolerance for rounding (floating point arithmetic)
                    let tolerance = 5;
                    prop_assert!(
                        current_delay >= expected_delay.saturating_sub(tolerance) &&
                        current_delay <= expected_delay + tolerance,
                        "Delay should be exponential: attempt={}, expected={}, actual={}", 
                        attempt, expected_delay, current_delay
                    );
                } else {
                    // Should be capped at max_delay
                    prop_assert_eq!(
                        current_delay, max_delay,
                        "Delay should be capped at max_delay: attempt={}", attempt
                    );
                }
            }
            
            prev_delay = current_delay;
        }
    }
    
    #[test]
    fn exponential_backoff_respects_max_delay(
        initial_delay in 100u64..1000u64,
        max_delay in 2000u64..10000u64,
        multiplier in 2.0f64..3.0f64,
    ) {
        let config = RetryConfig {
            max_attempts: 20,
            initial_delay_ms: initial_delay,
            max_delay_ms: max_delay,
            backoff_multiplier: multiplier,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Property: No delay should ever exceed max_delay
        for attempt in 1..20 {
            let delay = policy.calculate_delay(attempt);
            prop_assert!(
                delay <= max_delay,
                "Delay should never exceed max_delay: attempt={}, delay={}, max={}", 
                attempt, delay, max_delay
            );
        }
    }
    
    #[test]
    fn exponential_backoff_with_jitter_stays_reasonable(
        initial_delay in 100u64..1000u64,
        max_delay in 5000u64..20000u64,
        multiplier in 2.0f64..2.5f64,
        attempt in 1u32..10u32,
    ) {
        let config = RetryConfig {
            max_attempts: 10,
            initial_delay_ms: initial_delay,
            max_delay_ms: max_delay,
            backoff_multiplier: multiplier,
            jitter: true,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Calculate expected delay without jitter
        let base_delay = if attempt == 0 {
            0
        } else {
            let exp_delay = initial_delay as f64 * multiplier.powi((attempt - 1) as i32);
            exp_delay.min(max_delay as f64) as u64
        };
        
        // Property: With jitter, delay should be within ±20% of base delay
        // (and still respect max_delay)
        let delay = policy.calculate_delay(attempt);
        
        if base_delay > 0 {
            let jitter_range = (base_delay as f64 * 0.2) as u64;
            let min_expected = base_delay.saturating_sub(jitter_range);
            let max_expected = (base_delay + jitter_range).min(max_delay);
            
            prop_assert!(
                delay >= min_expected && delay <= max_expected,
                "Delay with jitter should be within range: attempt={}, delay={}, base={}, range=[{}, {}]",
                attempt, delay, base_delay, min_expected, max_expected
            );
        }
    }
    
    #[test]
    fn first_retry_uses_initial_delay(
        initial_delay in 100u64..10000u64,
        max_delay in 10000u64..100000u64,
        multiplier in 1.5f64..3.0f64,
    ) {
        let config = RetryConfig {
            max_attempts: 10,
            initial_delay_ms: initial_delay,
            max_delay_ms: max_delay,
            backoff_multiplier: multiplier,
            jitter: false,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Property: First retry (attempt 1) should always use initial_delay
        let delay = policy.calculate_delay(1);
        prop_assert_eq!(delay, initial_delay);
    }
    
    #[test]
    fn zero_attempt_has_zero_delay(
        initial_delay in 100u64..10000u64,
        max_delay in 10000u64..100000u64,
        multiplier in 1.5f64..3.0f64,
        jitter in proptest::bool::ANY,
    ) {
        let config = RetryConfig {
            max_attempts: 10,
            initial_delay_ms: initial_delay,
            max_delay_ms: max_delay,
            backoff_multiplier: multiplier,
            jitter,
        };
        
        let policy = RetryPolicy::new(config);
        
        // Property: Attempt 0 should always have 0 delay
        let delay = policy.calculate_delay(0);
        prop_assert_eq!(delay, 0);
    }
}
