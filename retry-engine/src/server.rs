use crate::circuit_breaker::{CircuitBreaker, CircuitState};
use crate::dlq::{DLQEntry, DeadLetterQueue};
use crate::retry_policy::RetryPolicy;
use crate::{CircuitBreakerConfig, RetryConfig};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};
use tonic::{Request, Response, Status};

fn current_timestamp_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64
}

pub mod retry {
    tonic::include_proto!("retry");
}

use retry::retry_engine_server::RetryEngine;
use retry::{
    CircuitRequest, CircuitResponse, CircuitState as ProtoCircuitState, RetryRequest,
    RetryResponse, RetryStatusRequest, RetryStatusResponse,
};

#[derive(Clone)]
struct RetryState {
    attempt_count: u32,
    last_error: String,
    last_attempt_at_ms: u64,
}

pub struct RetryEngineService {
    retry_policy: Arc<RetryPolicy>,
    circuit_breakers: Arc<Mutex<HashMap<String, CircuitBreaker>>>,
    dlq: Arc<DeadLetterQueue>,
    retry_states: Arc<Mutex<HashMap<String, RetryState>>>,
    circuit_config: CircuitBreakerConfig,
}

impl RetryEngineService {
    pub fn new(retry_config: RetryConfig, circuit_config: CircuitBreakerConfig) -> Self {
        Self {
            retry_policy: Arc::new(RetryPolicy::new(retry_config)),
            circuit_breakers: Arc::new(Mutex::new(HashMap::new())),
            dlq: Arc::new(DeadLetterQueue::new()),
            retry_states: Arc::new(Mutex::new(HashMap::new())),
            circuit_config,
        }
    }

    fn get_or_create_circuit_breaker(&self, psp_name: &str) -> CircuitBreaker {
        let mut breakers = self.circuit_breakers.lock().unwrap();
        breakers
            .entry(psp_name.to_string())
            .or_insert_with(|| CircuitBreaker::new(self.circuit_config.clone()))
            .clone()
    }

    fn convert_circuit_state(state: CircuitState) -> ProtoCircuitState {
        match state {
            CircuitState::Closed => ProtoCircuitState::Closed,
            CircuitState::Open => ProtoCircuitState::Open,
            CircuitState::HalfOpen => ProtoCircuitState::HalfOpen,
        }
    }
}

#[tonic::async_trait]
impl RetryEngine for RetryEngineService {
    async fn schedule_retry(
        &self,
        request: Request<RetryRequest>,
    ) -> Result<Response<RetryResponse>, Status> {
        let req = request.into_inner();
        let transaction_id = req.transaction_id.clone();
        let psp_name = req.psp_name.clone();
        let attempt = req.attempt_number as u32;

        // Check if already in DLQ
        if self.dlq.contains(&transaction_id) {
            return Ok(Response::new(RetryResponse {
                retry_id: transaction_id.clone(),
                scheduled: false,
                next_retry_at_ms: 0,
                message: "Transaction already in dead letter queue".to_string(),
            }));
        }

        // Check circuit breaker
        let circuit_breaker = self.get_or_create_circuit_breaker(&psp_name);
        if !circuit_breaker.can_proceed() {
            return Ok(Response::new(RetryResponse {
                retry_id: transaction_id.clone(),
                scheduled: false,
                next_retry_at_ms: 0,
                message: format!("Circuit breaker open for PSP: {}", psp_name),
            }));
        }

        // Check if we should retry
        if !self.retry_policy.should_retry(attempt) {
            // Move to DLQ
            let dlq_entry = DLQEntry {
                transaction_id: transaction_id.clone(),
                psp_name: psp_name.clone(),
                payload: req.payload.clone(),
                attempt_count: attempt,
                last_error: "Max retry attempts exceeded".to_string(),
                timestamp_ms: current_timestamp_ms(),
            };
            self.dlq.add_entry(dlq_entry);

            return Ok(Response::new(RetryResponse {
                retry_id: transaction_id.clone(),
                scheduled: false,
                next_retry_at_ms: 0,
                message: "Max retries exceeded, moved to DLQ".to_string(),
            }));
        }

        // Calculate next retry delay
        let delay_ms = self.retry_policy.calculate_delay(attempt);
        let next_retry_at_ms = current_timestamp_ms() + delay_ms;

        // Update retry state
        let mut states = self.retry_states.lock().unwrap();
        states.insert(
            transaction_id.clone(),
            RetryState {
                attempt_count: attempt,
                last_error: String::new(),
                last_attempt_at_ms: current_timestamp_ms(),
            },
        );

        Ok(Response::new(RetryResponse {
            retry_id: transaction_id,
            scheduled: true,
            next_retry_at_ms: next_retry_at_ms as i64,
            message: format!("Retry scheduled for attempt {}", attempt + 1),
        }))
    }

    async fn get_circuit_status(
        &self,
        request: Request<CircuitRequest>,
    ) -> Result<Response<CircuitResponse>, Status> {
        let req = request.into_inner();
        let circuit_breaker = self.get_or_create_circuit_breaker(&req.psp_name);
        let state = circuit_breaker.get_state();

        Ok(Response::new(CircuitResponse {
            psp_name: req.psp_name,
            state: Self::convert_circuit_state(state.state) as i32,
            failure_count: state.failure_count as i32,
            success_count: state.success_count as i32,
            last_failure_at_ms: state.last_failure_at_ms as i64,
            next_attempt_at_ms: state.next_attempt_at_ms as i64,
        }))
    }

    async fn get_retry_status(
        &self,
        request: Request<RetryStatusRequest>,
    ) -> Result<Response<RetryStatusResponse>, Status> {
        let req = request.into_inner();
        let transaction_id = req.transaction_id;

        // Check if in DLQ
        if let Some(dlq_entry) = self.dlq.get_entry(&transaction_id) {
            return Ok(Response::new(RetryStatusResponse {
                transaction_id: transaction_id.clone(),
                attempt_count: dlq_entry.attempt_count as i32,
                status: "IN_DLQ".to_string(),
                last_error: dlq_entry.last_error,
                in_dlq: true,
            }));
        }

        // Check retry state
        let states = self.retry_states.lock().unwrap();
        if let Some(state) = states.get(&transaction_id) {
            return Ok(Response::new(RetryStatusResponse {
                transaction_id: transaction_id.clone(),
                attempt_count: state.attempt_count as i32,
                status: "RETRYING".to_string(),
                last_error: state.last_error.clone(),
                in_dlq: false,
            }));
        }

        Ok(Response::new(RetryStatusResponse {
            transaction_id: transaction_id.clone(),
            attempt_count: 0,
            status: "NOT_FOUND".to_string(),
            last_error: String::new(),
            in_dlq: false,
        }))
    }
}
