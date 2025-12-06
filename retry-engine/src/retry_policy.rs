use crate::RetryConfig;
use rand::Rng;

pub struct RetryPolicy {
    config: RetryConfig,
}

impl RetryPolicy {
    pub fn new(config: RetryConfig) -> Self {
        Self { config }
    }

    /// Calculate the delay for the next retry attempt using exponential backoff
    pub fn calculate_delay(&self, attempt: u32) -> u64 {
        if attempt == 0 {
            return 0;
        }

        // Calculate exponential backoff: initial_delay * (multiplier ^ (attempt - 1))
        let base_delay = self.config.initial_delay_ms as f64
            * self.config.backoff_multiplier.powi((attempt - 1) as i32);

        // Cap at max delay
        let capped_delay = base_delay.min(self.config.max_delay_ms as f64) as u64;

        // Add jitter if enabled
        let delay_with_jitter = if self.config.jitter {
            self.add_jitter(capped_delay)
        } else {
            capped_delay
        };
        
        // Ensure we don't exceed max_delay even with jitter
        delay_with_jitter.min(self.config.max_delay_ms)
    }

    /// Add random jitter to prevent thundering herd
    fn add_jitter(&self, delay: u64) -> u64 {
        let mut rng = rand::thread_rng();
        let jitter_range = (delay as f64 * 0.2) as u64; // ±20% jitter
        let jitter = rng.gen_range(0..=jitter_range);
        
        if rng.gen_bool(0.5) {
            delay.saturating_add(jitter)
        } else {
            delay.saturating_sub(jitter)
        }
    }

    pub fn should_retry(&self, attempt: u32) -> bool {
        attempt < self.config.max_attempts
    }

    pub fn max_attempts(&self) -> u32 {
        self.config.max_attempts
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exponential_backoff_without_jitter() {
        let config = RetryConfig {
            max_attempts: 5,
            initial_delay_ms: 1000,
            max_delay_ms: 60000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        let policy = RetryPolicy::new(config);

        assert_eq!(policy.calculate_delay(0), 0);
        assert_eq!(policy.calculate_delay(1), 1000);
        assert_eq!(policy.calculate_delay(2), 2000);
        assert_eq!(policy.calculate_delay(3), 4000);
        assert_eq!(policy.calculate_delay(4), 8000);
        assert_eq!(policy.calculate_delay(5), 16000);
    }

    #[test]
    fn test_max_delay_cap() {
        let config = RetryConfig {
            max_attempts: 10,
            initial_delay_ms: 1000,
            max_delay_ms: 5000,
            backoff_multiplier: 2.0,
            jitter: false,
        };
        let policy = RetryPolicy::new(config);

        assert_eq!(policy.calculate_delay(1), 1000);
        assert_eq!(policy.calculate_delay(2), 2000);
        assert_eq!(policy.calculate_delay(3), 4000);
        assert_eq!(policy.calculate_delay(4), 5000); // Capped
        assert_eq!(policy.calculate_delay(5), 5000); // Capped
    }

    #[test]
    fn test_should_retry() {
        let config = RetryConfig {
            max_attempts: 3,
            ..Default::default()
        };
        let policy = RetryPolicy::new(config);

        assert!(policy.should_retry(0));
        assert!(policy.should_retry(1));
        assert!(policy.should_retry(2));
        assert!(!policy.should_retry(3));
        assert!(!policy.should_retry(4));
    }
}
