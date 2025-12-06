# Merchant Integration Guide

This guide helps merchants integrate with the Payment Acquiring Gateway API.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Authentication](#authentication)
3. [Payment Flow](#payment-flow)
4. [API Reference](#api-reference)
5. [Webhooks](#webhooks)
6. [Testing](#testing)
7. [Error Handling](#error-handling)
8. [Best Practices](#best-practices)

## Getting Started

### Prerequisites

1. **Merchant Account**: Contact sales to create a merchant account
2. **API Credentials**: Obtain API keys from the merchant dashboard
3. **Webhook Endpoint**: Set up an HTTPS endpoint to receive notifications

### Environment URLs

| Environment | Base URL |
|-------------|----------|
| Sandbox | `https://sandbox.paymentgateway.example.com/api/v1` |
| Production | `https://api.paymentgateway.example.com/api/v1` |

### API Keys

You'll receive two types of API keys:

- **Test Key** (`pk_test_xxx`): Use in sandbox for testing
- **Live Key** (`pk_live_xxx`): Use in production for real transactions

> ⚠️ Never expose your API keys in client-side code or public repositories.

## Authentication

### API Key Authentication

Include your API key in the `X-API-Key` header:

```bash
curl -X POST https://api.paymentgateway.example.com/api/v1/payments \
  -H "X-API-Key: pk_live_your_api_key" \
  -H "Content-Type: application/json" \
  -d '{"cardNumber":"4532015112830366",...}'
```

### OAuth 2.0 (Optional)

For enhanced security, use OAuth 2.0:

```bash
# Get access token
curl -X POST https://auth.paymentgateway.example.com/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET"

# Use token in requests
curl -X POST https://api.paymentgateway.example.com/api/v1/payments \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cardNumber":"4532015112830366",...}'
```

## Payment Flow

### Standard Payment Flow

```
┌─────────┐     ┌─────────────┐     ┌─────────────────┐     ┌─────────┐
│Customer │     │  Merchant   │     │ Payment Gateway │     │   PSP   │
└────┬────┘     └──────┬──────┘     └────────┬────────┘     └────┬────┘
     │                 │                     │                   │
     │ 1. Enter card   │                     │                   │
     │────────────────>│                     │                   │
     │                 │                     │                   │
     │                 │ 2. POST /payments   │                   │
     │                 │────────────────────>│                   │
     │                 │                     │                   │
     │                 │                     │ 3. Tokenize       │
     │                 │                     │ 4. Fraud check    │
     │                 │                     │ 5. 3DS (if needed)│
     │                 │                     │                   │
     │                 │                     │ 6. Authorize      │
     │                 │                     │──────────────────>│
     │                 │                     │                   │
     │                 │                     │ 7. Auth response  │
     │                 │                     │<──────────────────│
     │                 │                     │                   │
     │                 │ 8. Payment response │                   │
     │                 │<────────────────────│                   │
     │                 │                     │                   │
     │ 9. Confirmation │                     │                   │
     │<────────────────│                     │                   │
```

### Payment States

```
PENDING → AUTHORIZED → CAPTURED → SETTLED
    │          │           │
    │          │           └──→ REFUNDED
    │          │
    │          └──→ CANCELLED (void)
    │
    └──→ FAILED
```

## API Reference

### Create Payment

**Endpoint:** `POST /api/v1/payments`

**Request:**
```json
{
  "cardNumber": "4532015112830366",
  "expiryMonth": 12,
  "expiryYear": 2025,
  "cvv": "123",
  "amount": 100.00,
  "currency": "USD",
  "description": "Order #12345",
  "referenceId": "order-12345",
  "billingStreet": "123 Main St",
  "billingCity": "New York",
  "billingState": "NY",
  "billingZip": "10001",
  "billingCountry": "US"
}
```

**Response (201 Created):**
```json
{
  "paymentId": "pay_abc123def456ghi789jkl012",
  "status": "AUTHORIZED",
  "amount": 100.00,
  "currency": "USD",
  "cardLastFour": "0366",
  "cardBrand": "VISA",
  "createdAt": "2025-12-06T10:30:00Z",
  "authorizedAt": "2025-12-06T10:30:01Z"
}
```

### Get Payment

**Endpoint:** `GET /api/v1/payments/{paymentId}`

**Response:**
```json
{
  "paymentId": "pay_abc123def456ghi789jkl012",
  "status": "AUTHORIZED",
  "amount": 100.00,
  "currency": "USD",
  "cardLastFour": "0366",
  "cardBrand": "VISA",
  "createdAt": "2025-12-06T10:30:00Z",
  "authorizedAt": "2025-12-06T10:30:01Z"
}
```

### Capture Payment

**Endpoint:** `POST /api/v1/payments/{paymentId}/capture`

Captures an authorized payment. Call this when you're ready to charge the customer (e.g., when shipping an order).

**Request (optional - for partial capture):**
```json
{
  "amount": 50.00
}
```

**Response:**
```json
{
  "paymentId": "pay_abc123def456ghi789jkl012",
  "status": "CAPTURED",
  "amount": 100.00,
  "currency": "USD",
  "cardLastFour": "0366",
  "cardBrand": "VISA",
  "createdAt": "2025-12-06T10:30:00Z",
  "authorizedAt": "2025-12-06T10:30:01Z"
}
```

### Void Payment

**Endpoint:** `POST /api/v1/payments/{paymentId}/void`

Voids an authorized payment before capture. Use this to cancel an order before fulfillment.

**Response:**
```json
{
  "paymentId": "pay_abc123def456ghi789jkl012",
  "status": "CANCELLED",
  "amount": 100.00,
  "currency": "USD"
}
```

### Create Refund

**Endpoint:** `POST /api/v1/refunds`

Refunds a captured payment. Supports full and partial refunds.

**Request:**
```json
{
  "paymentId": "pay_abc123def456ghi789jkl012",
  "amount": 50.00,
  "reason": "Customer return"
}
```

**Response (201 Created):**
```json
{
  "refundId": "ref_xyz789abc123def456ghi012",
  "paymentId": "pay_abc123def456ghi789jkl012",
  "status": "COMPLETED",
  "amount": 50.00,
  "currency": "USD",
  "reason": "Customer return",
  "createdAt": "2025-12-06T11:00:00Z",
  "processedAt": "2025-12-06T11:00:02Z"
}
```

### Query Transactions

**Endpoint:** `GET /api/v1/transactions`

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| status | string | Filter by status (AUTHORIZED, CAPTURED, etc.) |
| currency | string | Filter by currency (USD, EUR, etc.) |
| minAmount | number | Minimum amount |
| maxAmount | number | Maximum amount |
| startDate | datetime | Start of date range |
| endDate | datetime | End of date range |
| cardLastFour | string | Filter by last 4 digits |
| page | integer | Page number (0-indexed) |
| size | integer | Page size (max 100) |
| sortBy | string | Sort field (createdAt, amount) |
| sortDirection | string | ASC or DESC |

**Example:**
```bash
curl "https://api.paymentgateway.example.com/api/v1/transactions?status=CAPTURED&startDate=2025-12-01T00:00:00Z&page=0&size=20" \
  -H "X-API-Key: pk_live_your_api_key"
```

**Response:**
```json
{
  "transactions": [
    {
      "paymentId": "pay_abc123...",
      "status": "CAPTURED",
      "amount": 100.00,
      "currency": "USD",
      "cardLastFour": "0366",
      "cardBrand": "VISA",
      "createdAt": "2025-12-06T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "hasNext": true,
  "hasPrevious": false
}
```

## Webhooks

### Setting Up Webhooks

1. Configure your webhook URL in the merchant dashboard
2. Generate a webhook secret for signature verification
3. Implement an HTTPS endpoint to receive events

### Webhook Events

| Event | Description |
|-------|-------------|
| `payment.authorized` | Payment successfully authorized |
| `payment.captured` | Payment captured |
| `payment.failed` | Payment failed |
| `payment.cancelled` | Payment voided |
| `refund.completed` | Refund processed |
| `refund.failed` | Refund failed |
| `dispute.created` | Chargeback initiated |
| `dispute.resolved` | Dispute resolved |

### Webhook Payload

```json
{
  "id": "evt_abc123",
  "type": "payment.authorized",
  "created": "2025-12-06T10:30:00Z",
  "data": {
    "paymentId": "pay_abc123def456ghi789jkl012",
    "status": "AUTHORIZED",
    "amount": 100.00,
    "currency": "USD",
    "cardLastFour": "0366",
    "cardBrand": "VISA"
  }
}
```

### Signature Verification

Verify webhook signatures to ensure authenticity:

```python
import hmac
import hashlib

def verify_webhook(payload, signature, secret):
    expected = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(f"sha256={expected}", signature)

# In your webhook handler
signature = request.headers.get('X-Webhook-Signature')
if not verify_webhook(request.body, signature, WEBHOOK_SECRET):
    return Response(status=401)
```

```javascript
const crypto = require('crypto');

function verifyWebhook(payload, signature, secret) {
  const expected = crypto
    .createHmac('sha256', secret)
    .update(payload)
    .digest('hex');
  return crypto.timingSafeEqual(
    Buffer.from(`sha256=${expected}`),
    Buffer.from(signature)
  );
}
```

### Webhook Best Practices

1. **Respond quickly**: Return 200 within 5 seconds
2. **Process asynchronously**: Queue events for processing
3. **Handle duplicates**: Events may be sent multiple times
4. **Verify signatures**: Always verify before processing
5. **Use HTTPS**: Webhook endpoints must use HTTPS

## Testing

### Test Cards

| Card Number | Behavior |
|-------------|----------|
| 4532015112830366 | Successful authorization |
| 4000000000000002 | Declined - insufficient funds |
| 4000000000000069 | Declined - expired card |
| 4000000000000127 | Declined - invalid CVV |
| 4000000000003220 | Requires 3D Secure |
| 4000000000009995 | Fraud - high risk |

### Test CVV

- `123` - Valid CVV
- `999` - Invalid CVV (triggers decline)

### Test Amounts

| Amount | Behavior |
|--------|----------|
| Any | Normal processing |
| 0.01 | Minimum amount |
| 999999.99 | Maximum amount |

### Sandbox Testing

```bash
# Test successful payment
curl -X POST https://sandbox.paymentgateway.example.com/api/v1/payments \
  -H "X-API-Key: pk_test_your_test_key" \
  -H "Content-Type: application/json" \
  -d '{
    "cardNumber": "4532015112830366",
    "expiryMonth": 12,
    "expiryYear": 2025,
    "cvv": "123",
    "amount": 100.00,
    "currency": "USD"
  }'

# Test declined payment
curl -X POST https://sandbox.paymentgateway.example.com/api/v1/payments \
  -H "X-API-Key: pk_test_your_test_key" \
  -H "Content-Type: application/json" \
  -d '{
    "cardNumber": "4000000000000002",
    "expiryMonth": 12,
    "expiryYear": 2025,
    "cvv": "123",
    "amount": 100.00,
    "currency": "USD"
  }'
```

## Error Handling

### Error Response Format

```json
{
  "error": "validation_error",
  "message": "Invalid card number",
  "code": "INVALID_CARD_NUMBER",
  "details": [
    {
      "field": "cardNumber",
      "message": "Card number failed Luhn check"
    }
  ],
  "traceId": "abc123-def456-ghi789"
}
```

### Error Codes

| Code | Description | Action |
|------|-------------|--------|
| `INVALID_CARD_NUMBER` | Card number invalid | Check card number |
| `CARD_EXPIRED` | Card has expired | Use different card |
| `INVALID_CVV` | CVV incorrect | Verify CVV |
| `INSUFFICIENT_FUNDS` | Not enough balance | Use different card |
| `CARD_DECLINED` | Generic decline | Contact card issuer |
| `FRAUD_SUSPECTED` | Fraud detected | Contact support |
| `RATE_LIMITED` | Too many requests | Retry after delay |
| `PAYMENT_NOT_FOUND` | Payment doesn't exist | Check payment ID |
| `INVALID_STATE` | Invalid operation | Check payment status |

### HTTP Status Codes

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad request - invalid input |
| 401 | Unauthorized - invalid credentials |
| 404 | Not found |
| 422 | Validation error |
| 429 | Rate limited |
| 500 | Server error |

### Retry Strategy

For transient errors (5xx, network issues):

```python
import time
import random

def make_request_with_retry(request_func, max_retries=3):
    for attempt in range(max_retries):
        try:
            response = request_func()
            if response.status_code < 500:
                return response
        except Exception as e:
            if attempt == max_retries - 1:
                raise
        
        # Exponential backoff with jitter
        delay = (2 ** attempt) + random.uniform(0, 1)
        time.sleep(delay)
    
    raise Exception("Max retries exceeded")
```

## Best Practices

### Security

1. **Never log card numbers**: Use masked values only
2. **Use HTTPS**: All API calls must use HTTPS
3. **Secure API keys**: Store in environment variables or secrets manager
4. **Validate webhooks**: Always verify signatures
5. **Use idempotency keys**: Prevent duplicate charges

### Idempotency

Include `Idempotency-Key` header for safe retries:

```bash
curl -X POST https://api.paymentgateway.example.com/api/v1/payments \
  -H "X-API-Key: pk_live_your_api_key" \
  -H "Idempotency-Key: order-12345-attempt-1" \
  -H "Content-Type: application/json" \
  -d '{"cardNumber":"4532015112830366",...}'
```

- Keys are valid for 24 hours
- Same key returns cached response
- Use unique keys per transaction attempt

### Performance

1. **Use connection pooling**: Reuse HTTP connections
2. **Set timeouts**: 30 seconds recommended
3. **Handle rate limits**: Implement backoff
4. **Cache responses**: Cache transaction queries

### PCI Compliance

As a merchant using our gateway:

1. **Never store card data**: Use tokens for recurring payments
2. **Use hosted payment page**: Reduces PCI scope
3. **Complete SAQ A**: Self-assessment questionnaire
4. **Train staff**: Security awareness training

### Support

- **Documentation**: https://docs.paymentgateway.example.com
- **API Status**: https://status.paymentgateway.example.com
- **Support Email**: support@paymentgateway.example.com
- **Emergency**: +1-800-PAY-HELP (24/7)
