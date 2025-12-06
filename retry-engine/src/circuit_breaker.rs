use crate::CircuitBreakerConfig;
use serde::{Deserialize, Serialize};
use std::sync::{Arc, Mutex};

fn current_timestamp_ms() -> u64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CircuitState {
    Closed,
    Open,
    HalfOpen,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CircuitBreakerState {
    pub state: CircuitState,
    pub failure_count: u32,
    pub success_count: u32,
    pub last_failure_at_ms: u64,
    pub next_attempt_at_ms: u64,
}

impl Default for CircuitBreakerState {
    fn default() -> Self {
        Self {
            state: CircuitState::Closed,
            failure_count: 0,
            success_count: 0,
            last_failure_at_ms: 0,
            next_attempt_at_ms: 0,
        }
    }
}

#[derive(Clone)]
pub struct CircuitBreaker {
    config: CircuitBreakerConfig,
    state: Arc<Mutex<CircuitBreakerState>>,
}

impl CircuitBreaker {
    pub fn new(config: CircuitBreakerConfig) -> Self {
        Self {
            config,
            state: Arc::new(Mutex::new(CircuitBreakerState::default())),
        }
    }

    pub fn with_state(config: CircuitBreakerConfig, state: CircuitBreakerState) -> Self {
        Self {
            config,
            state: Arc::new(Mutex::new(state)),
        }
    }

    /// Check if a request can proceed
    pub fn can_proceed(&self) -> bool {
        let mut state = self.state.lock().unwrap();
        let now = current_timestamp_ms();

        match state.state {
            CircuitState::Closed => true,
            CircuitState::Open => {
                // Check if timeout has expired
                if now >= state.next_attempt_at_ms {
                    // Transition to half-open
                    state.state = CircuitState::HalfOpen;
                    state.success_count = 0;
                    true
                } else {
                    false
                }
            }
            CircuitState::HalfOpen => true,
        }
    }

    /// Record a successful operation
    pub fn record_success(&self) {
        let mut state = self.state.lock().unwrap();

        match state.state {
            CircuitState::Closed => {
                // Reset failure count on success
                state.failure_count = 0;
            }
            CircuitState::HalfOpen => {
                state.success_count += 1;
                // If we reach success threshold, close the circuit
                if state.success_count >= self.config.success_threshold {
                    state.state = CircuitState::Closed;
                    state.failure_count = 0;
                    state.success_count = 0;
                }
            }
            CircuitState::Open => {
                // Should not happen, but reset if it does
                state.state = CircuitState::Closed;
                state.failure_count = 0;
                state.success_count = 0;
            }
        }
    }

    /// Record a failed operation
    pub fn record_failure(&self) {
        let mut state = self.state.lock().unwrap();
        let now = current_timestamp_ms();

        state.last_failure_at_ms = now;

        match state.state {
            CircuitState::Closed => {
                state.failure_count += 1;
                // If we reach failure threshold, open the circuit
                if state.failure_count >= self.config.failure_threshold {
                    state.state = CircuitState::Open;
                    state.next_attempt_at_ms = now + self.config.timeout_duration_ms;
                }
            }
            CircuitState::HalfOpen => {
                // Any failure in half-open state reopens the circuit
                state.state = CircuitState::Open;
                state.failure_count = self.config.failure_threshold;
                state.success_count = 0;
                state.next_attempt_at_ms = now + self.config.timeout_duration_ms;
            }
            CircuitState::Open => {
                // Already open, just update timestamp
                state.next_attempt_at_ms = now + self.config.timeout_duration_ms;
            }
        }
    }

    /// Get current state
    pub fn get_state(&self) -> CircuitBreakerState {
        self.state.lock().unwrap().clone()
    }

    /// Reset the circuit breaker
    pub fn reset(&self) {
        let mut state = self.state.lock().unwrap();
        *state = CircuitBreakerState::default();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_circuit_starts_closed() {
        let config = CircuitBreakerConfig::default();
        let cb = CircuitBreaker::new(config);

        assert!(cb.can_proceed());
        assert_eq!(cb.get_state().state, CircuitState::Closed);
    }

    #[test]
    fn test_circuit_opens_on_threshold() {
        let config = CircuitBreakerConfig {
            failure_threshold: 3,
            success_threshold: 2,
            timeout_duration_ms: 1000,
        };
        let cb = CircuitBreaker::new(config);

        // Record failures
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Closed);
        assert_eq!(cb.get_state().failure_count, 1);

        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Closed);
        assert_eq!(cb.get_state().failure_count, 2);

        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Open);
        assert_eq!(cb.get_state().failure_count, 3);
    }

    #[test]
    fn test_circuit_blocks_when_open() {
        let config = CircuitBreakerConfig {
            failure_threshold: 2,
            success_threshold: 2,
            timeout_duration_ms: 10000,
        };
        let cb = CircuitBreaker::new(config);

        // Open the circuit
        cb.record_failure();
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Open);

        // Should not proceed
        assert!(!cb.can_proceed());
    }

    #[test]
    fn test_circuit_closes_after_successes() {
        let config = CircuitBreakerConfig {
            failure_threshold: 2,
            success_threshold: 2,
            timeout_duration_ms: 0, // Immediate timeout for testing
        };
        let cb = CircuitBreaker::new(config);

        // Open the circuit
        cb.record_failure();
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Open);

        // Wait for timeout (immediate in this case)
        std::thread::sleep(std::time::Duration::from_millis(10));

        // Should transition to half-open
        assert!(cb.can_proceed());
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);

        // Record successes
        cb.record_success();
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);

        cb.record_success();
        assert_eq!(cb.get_state().state, CircuitState::Closed);
    }

    #[test]
    fn test_half_open_reopens_on_failure() {
        let config = CircuitBreakerConfig {
            failure_threshold: 2,
            success_threshold: 2,
            timeout_duration_ms: 0,
        };
        let cb = CircuitBreaker::new(config);

        // Open the circuit
        cb.record_failure();
        cb.record_failure();

        // Transition to half-open
        std::thread::sleep(std::time::Duration::from_millis(10));
        assert!(cb.can_proceed());
        assert_eq!(cb.get_state().state, CircuitState::HalfOpen);

        // Failure in half-open reopens
        cb.record_failure();
        assert_eq!(cb.get_state().state, CircuitState::Open);
    }
}
