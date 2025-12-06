# Payment Acquiring Gateway Documentation

Welcome to the Payment Acquiring Gateway documentation. This documentation covers all aspects of deploying, operating, and integrating with the gateway.

## Documentation Index

### For Developers

| Document | Description |
|----------|-------------|
| [API Reference (OpenAPI)](api/openapi.yaml) | Complete API specification in OpenAPI 3.1 format |
| [Merchant Integration Guide](MERCHANT_INTEGRATION_GUIDE.md) | Step-by-step guide for merchants integrating with the API |

### For Operations

| Document | Description |
|----------|-------------|
| [Deployment Guide](DEPLOYMENT_GUIDE.md) | Instructions for deploying in various environments |
| [Operations Runbook](OPERATIONS_RUNBOOK.md) | Day-to-day operations procedures |
| [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md) | Common issues and solutions |

### For Compliance

| Document | Description |
|----------|-------------|
| [PCI DSS Compliance](PCI_DSS_COMPLIANCE.md) | PCI DSS compliance procedures and controls |

## Quick Links

### API Endpoints

| Environment | URL |
|-------------|-----|
| Production | `https://api.paymentgateway.example.com/api/v1` |
| Sandbox | `https://sandbox.paymentgateway.example.com/api/v1` |
| Local | `http://localhost:8446/api/v1` |

### Service Ports

| Service | Port | Protocol |
|---------|------|----------|
| Authorization Service | 8446 | HTTP/REST |
| Tokenization Service | 8445 | gRPC |
| HSM Simulator | 8444 | gRPC |
| Fraud Detection | 8447 | HTTP + gRPC |
| 3D Secure | 8448 | HTTP + gRPC |
| Settlement | 8449 | HTTP |
| Retry Engine | 8450 | gRPC |

### Monitoring

| Tool | URL | Purpose |
|------|-----|---------|
| Grafana | http://localhost:3000 | Dashboards |
| Prometheus | http://localhost:9090 | Metrics |
| Jaeger | http://localhost:16686 | Tracing |

## Getting Started

1. **New to the gateway?** Start with the [Merchant Integration Guide](MERCHANT_INTEGRATION_GUIDE.md)
2. **Deploying?** See the [Deployment Guide](DEPLOYMENT_GUIDE.md)
3. **Operating?** Check the [Operations Runbook](OPERATIONS_RUNBOOK.md)
4. **Having issues?** Consult the [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md)

## Support

- **Documentation Issues**: Open a GitHub issue
- **Technical Support**: support@paymentgateway.example.com
- **Emergency (P1)**: +1-800-PAY-HELP (24/7)
- **Status Page**: https://status.paymentgateway.example.com
