/**
 * K6 Load Test Script for Payment Acquiring Gateway
 * 
 * Requirements: 27.1, 27.2, 27.3
 * - 99.99% uptime for payment authorization
 * - <500ms p95 response time
 * - 10,000 TPS throughput
 * 
 * Usage:
 *   k6 run --vus 100 --duration 5m load-tests/k6-payment-load-test.js
 *   k6 run --vus 500 --duration 30m load-tests/k6-payment-load-test.js  # Stress test
 *   k6 run --vus 200 --duration 24h load-tests/k6-payment-load-test.js  # Soak test
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomString, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// Custom metrics
const paymentSuccessRate = new Rate('payment_success_rate');
const paymentLatency = new Trend('payment_latency', true);
const captureLatency = new Trend('capture_latency', true);
const queryLatency = new Trend('query_latency', true);
const errorCounter = new Counter('errors');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8446';
const API_KEY = __ENV.API_KEY || 'pk_test_' + randomString(24);

// Test scenarios
export const options = {
    scenarios: {
        // Scenario 1: Constant load test at target TPS
        constant_load: {
            executor: 'constant-arrival-rate',
            rate: 10000, // 10K requests per second
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: 500,
            maxVUs: 1000,
        },
        // Scenario 2: Ramping load test
        ramping_load: {
            executor: 'ramping-arrival-rate',
            startRate: 100,
            timeUnit: '1s',
            preAllocatedVUs: 200,
            maxVUs: 1000,
            stages: [
                { duration: '2m', target: 1000 },   // Ramp up to 1K TPS
                { duration: '5m', target: 5000 },   // Ramp up to 5K TPS
                { duration: '10m', target: 10000 }, // Ramp up to 10K TPS
                { duration: '5m', target: 10000 },  // Stay at 10K TPS
                { duration: '3m', target: 0 },      // Ramp down
            ],
        },
    },
    thresholds: {
        // SLA thresholds
        'http_req_duration': ['p(95)<500', 'p(99)<1000'], // p95 < 500ms, p99 < 1s
        'http_req_failed': ['rate<0.01'],                  // Error rate < 1%
        'payment_success_rate': ['rate>0.99'],             // 99% success rate
        'payment_latency': ['p(95)<500'],                  // Payment p95 < 500ms
        'errors': ['count<100'],                           // Max 100 errors
    },
};

// Generate valid test card number (Luhn-valid)
function generateCardNumber() {
    const prefixes = ['4111111111111', '5500000000000', '340000000000'];
    const prefix = prefixes[randomIntBetween(0, prefixes.length - 1)];
    let number = prefix;
    
    // Add random digits
    while (number.length < 15) {
        number += randomIntBetween(0, 9);
    }
    
    // Calculate Luhn check digit
    let sum = 0;
    let isEven = true;
    for (let i = number.length - 1; i >= 0; i--) {
        let digit = parseInt(number[i]);
        if (isEven) {
            digit *= 2;
            if (digit > 9) digit -= 9;
        }
        sum += digit;
        isEven = !isEven;
    }
    const checkDigit = (10 - (sum % 10)) % 10;
    
    return number + checkDigit;
}

// Generate payment request
function generatePaymentRequest() {
    return {
        amount: randomIntBetween(100, 100000) / 100, // $1.00 to $1000.00
        currency: 'USD',
        cardNumber: generateCardNumber(),
        expiryMonth: randomIntBetween(1, 12).toString().padStart(2, '0'),
        expiryYear: (new Date().getFullYear() + randomIntBetween(1, 5)).toString(),
        cvv: randomIntBetween(100, 999).toString(),
        description: 'Load test payment ' + randomString(8),
        referenceId: 'ref_' + randomString(16),
        billingStreet: '123 Test Street',
        billingCity: 'Test City',
        billingState: 'TS',
        billingZip: '12345',
        billingCountry: 'US',
    };
}

// Headers
const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${API_KEY}`,
    'X-Idempotency-Key': '', // Will be set per request
};

export default function () {
    group('Payment Flow', function () {
        // Step 1: Create Payment
        const idempotencyKey = 'idem_' + randomString(24);
        headers['X-Idempotency-Key'] = idempotencyKey;
        
        const paymentRequest = generatePaymentRequest();
        const startTime = Date.now();
        
        const createResponse = http.post(
            `${BASE_URL}/api/v1/payments`,
            JSON.stringify(paymentRequest),
            { headers: headers, tags: { name: 'CreatePayment' } }
        );
        
        const createLatency = Date.now() - startTime;
        paymentLatency.add(createLatency);
        
        const createSuccess = check(createResponse, {
            'payment created': (r) => r.status === 200 || r.status === 201,
            'payment has id': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.paymentId !== undefined;
                } catch (e) {
                    return false;
                }
            },
            'latency under 500ms': () => createLatency < 500,
        });
        
        paymentSuccessRate.add(createSuccess);
        
        if (!createSuccess) {
            errorCounter.add(1);
            console.log(`Payment creation failed: ${createResponse.status} - ${createResponse.body}`);
            return;
        }
        
        let paymentId;
        try {
            paymentId = JSON.parse(createResponse.body).paymentId;
        } catch (e) {
            errorCounter.add(1);
            return;
        }
        
        // Small delay between operations
        sleep(0.1);
        
        // Step 2: Query Payment
        const queryStartTime = Date.now();
        const queryResponse = http.get(
            `${BASE_URL}/api/v1/payments/${paymentId}`,
            { headers: headers, tags: { name: 'QueryPayment' } }
        );
        
        queryLatency.add(Date.now() - queryStartTime);
        
        check(queryResponse, {
            'query successful': (r) => r.status === 200,
            'payment status correct': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.status === 'AUTHORIZED' || body.status === 'PENDING';
                } catch (e) {
                    return false;
                }
            },
        });
        
        // Step 3: Capture Payment (50% of the time)
        if (Math.random() > 0.5) {
            sleep(0.1);
            
            const captureStartTime = Date.now();
            const captureResponse = http.post(
                `${BASE_URL}/api/v1/payments/${paymentId}/capture`,
                null,
                { headers: headers, tags: { name: 'CapturePayment' } }
            );
            
            captureLatency.add(Date.now() - captureStartTime);
            
            check(captureResponse, {
                'capture successful': (r) => r.status === 200,
            });
        }
    });
    
    // Random delay between iterations (simulates real user behavior)
    sleep(randomIntBetween(1, 3) / 10);
}

// Setup function - runs once before the test
export function setup() {
    console.log('Starting load test against: ' + BASE_URL);
    
    // Health check
    const healthResponse = http.get(`${BASE_URL}/actuator/health`);
    if (healthResponse.status !== 200) {
        throw new Error('Service is not healthy: ' + healthResponse.body);
    }
    
    console.log('Service is healthy, starting load test...');
    return { startTime: Date.now() };
}

// Teardown function - runs once after the test
export function teardown(data) {
    const duration = (Date.now() - data.startTime) / 1000;
    console.log(`Load test completed in ${duration} seconds`);
}

// Handle summary
export function handleSummary(data) {
    return {
        'load-tests/results/summary.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}

function textSummary(data, options) {
    const metrics = data.metrics;
    let summary = '\n=== Payment Gateway Load Test Summary ===\n\n';
    
    summary += `Total Requests: ${metrics.http_reqs?.values?.count || 0}\n`;
    summary += `Success Rate: ${((1 - (metrics.http_req_failed?.values?.rate || 0)) * 100).toFixed(2)}%\n`;
    summary += `\nLatency (ms):\n`;
    summary += `  p50: ${metrics.http_req_duration?.values?.['p(50)']?.toFixed(2) || 'N/A'}\n`;
    summary += `  p95: ${metrics.http_req_duration?.values?.['p(95)']?.toFixed(2) || 'N/A'}\n`;
    summary += `  p99: ${metrics.http_req_duration?.values?.['p(99)']?.toFixed(2) || 'N/A'}\n`;
    summary += `\nPayment Metrics:\n`;
    summary += `  Success Rate: ${((metrics.payment_success_rate?.values?.rate || 0) * 100).toFixed(2)}%\n`;
    summary += `  p95 Latency: ${metrics.payment_latency?.values?.['p(95)']?.toFixed(2) || 'N/A'}ms\n`;
    summary += `  Errors: ${metrics.errors?.values?.count || 0}\n`;
    
    return summary;
}
