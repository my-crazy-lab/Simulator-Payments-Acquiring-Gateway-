use retry_engine::{CircuitBreakerConfig, RetryConfig};
use tonic::transport::Server;
use tracing::{info, Level};
use tracing_subscriber;

mod circuit_breaker;
mod dlq;
mod retry_policy;
mod server;

use server::retry::retry_engine_server::RetryEngineServer;
use server::RetryEngineService;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::fmt()
        .with_max_level(Level::INFO)
        .init();

    let addr = "[::1]:8450".parse()?;

    let retry_config = RetryConfig::default();
    let circuit_config = CircuitBreakerConfig::default();

    let retry_service = RetryEngineService::new(retry_config, circuit_config);

    info!("Retry Engine starting on {}", addr);

    Server::builder()
        .add_service(RetryEngineServer::new(retry_service))
        .serve(addr)
        .await?;

    Ok(())
}
