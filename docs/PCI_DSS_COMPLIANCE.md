# PCI DSS Compliance Procedures

This document outlines the PCI DSS compliance procedures for the Payment Acquiring Gateway.

## Table of Contents

1. [PCI DSS Overview](#pci-dss-overview)
2. [Cardholder Data Environment (CDE)](#cardholder-data-environment)
3. [Data Protection](#data-protection)
4. [Access Control](#access-control)
5. [Network Security](#network-security)
6. [Monitoring and Logging](#monitoring-and-logging)
7. [Vulnerability Management](#vulnerability-management)
8. [Incident Response](#incident-response)
9. [Compliance Validation](#compliance-validation)

## PCI DSS Overview

The Payment Acquiring Gateway is designed for PCI DSS Level 1 compliance, the highest level of certification required for organizations processing over 6 million card transactions annually.

### PCI DSS Requirements Mapping

| Requirement | Description | Implementation |
|-------------|-------------|----------------|
| 1 | Install and maintain firewall | Network policies, WAF |
| 2 | No vendor-supplied defaults | Custom configurations |
| 3 | Protect stored cardholder data | Tokenization, AES-256 encryption |
| 4 | Encrypt transmission | TLS 1.3, mTLS |
| 5 | Protect against malware | Container scanning, runtime protection |
| 6 | Develop secure systems | SAST, DAST, code review |
| 7 | Restrict access | RBAC, least privilege |
| 8 | Identify and authenticate | MFA, API keys, JWT |
| 9 | Restrict physical access | Cloud provider controls |
| 10 | Track and monitor access | Audit logging, SIEM |
| 11 | Test security systems | Penetration testing, vulnerability scans |
| 12 | Maintain security policy | Documentation, training |

## Cardholder Data Environment (CDE)

### CDE Scope

The following components are in PCI DSS scope:

**In-Scope Services:**
- Tokenization Service (handles raw PAN)
- HSM Simulator (manages encryption keys)
- Encrypted database fields (card_tokens table)

**Out-of-Scope Services:**
- Authorization Service (uses tokens only)
- Fraud Detection Service (no card data)
- 3D Secure Service (no card data)
- Settlement Service (uses tokens only)
- Retry Engine (no card data)

### Network Segmentation

```
┌─────────────────────────────────────────────────────────────┐
│                    Public Internet                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    DMZ (WAF, Load Balancer)                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Non-PCI Zone (payment-gateway namespace)        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │Authorization│  │   Fraud     │  │    3DS      │          │
│  │   Service   │  │  Detection  │  │   Service   │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
└─────────────────────────────────────────────────────────────┘
                              │
                    (mTLS, restricted ports)
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              PCI Zone (payment-gateway-pci namespace)        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │Tokenization │  │    HSM      │  │   Vault     │          │
│  │   Service   │  │  Simulator  │  │  (Secrets)  │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

### Network Policies

```yaml
# PCI namespace network policy
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: pci-zone-policy
  namespace: payment-gateway-pci
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: payment-gateway
        - podSelector:
            matchLabels:
              pci-access: "true"
      ports:
        - protocol: TCP
          port: 8444  # HSM
        - protocol: TCP
          port: 8445  # Tokenization
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              name: payment-gateway-pci
    - to:
        - ipBlock:
            cidr: 10.0.0.0/8  # Internal only
```

## Data Protection

### Cardholder Data Handling

| Data Element | Storage | Transmission | Display |
|--------------|---------|--------------|---------|
| PAN | Encrypted (AES-256-GCM) | TLS 1.3 | Masked (****1234) |
| CVV | Never stored | TLS 1.3 | Never displayed |
| Expiry Date | Encrypted | TLS 1.3 | Full display OK |
| Cardholder Name | Encrypted | TLS 1.3 | Full display OK |

### Tokenization Process

1. **Card Submission**: Merchant submits card data via TLS 1.3
2. **Tokenization**: Tokenization Service generates unique token
3. **Encryption**: PAN encrypted with AES-256-GCM using HSM key
4. **Storage**: Encrypted PAN stored with token mapping
5. **Response**: Token returned to merchant (PAN never leaves PCI zone)

```
Merchant → [TLS 1.3] → Authorization Service → [mTLS] → Tokenization Service
                                                              │
                                                              ▼
                                                         HSM (encrypt)
                                                              │
                                                              ▼
                                                    Encrypted Storage
                                                              │
                                                              ▼
                                              Token returned to merchant
```

### Encryption Standards

**At Rest:**
- Algorithm: AES-256-GCM
- Key Management: HSM with FIPS 140-2 Level 3 (simulated)
- Key Rotation: Quarterly (with backward compatibility)

**In Transit:**
- Protocol: TLS 1.3 (external), mTLS (internal)
- Cipher Suites: TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256
- Certificate Validity: 90 days (auto-renewed)

### Key Management

```bash
# Key hierarchy
Master Key (KEK) - HSM protected, never exported
    │
    └── Data Encryption Keys (DEK) - Encrypted by KEK
            │
            ├── PAN Encryption Key (version 1, 2, 3...)
            ├── Token Generation Key
            └── Audit Log Signing Key
```

**Key Rotation Procedure:**
1. Generate new DEK version in HSM
2. Update active key version in configuration
3. New encryptions use new key
4. Old keys retained for decryption (minimum 7 years)
5. Document rotation in audit log

## Access Control

### Role-Based Access Control (RBAC)

| Role | Permissions |
|------|-------------|
| `payment:read` | Query transactions, view masked data |
| `payment:write` | Create payments, capture, void |
| `refund:write` | Process refunds |
| `admin:read` | View audit logs, metrics |
| `admin:write` | Manage merchants, API keys |
| `pci:admin` | Access PCI zone (restricted) |

### Authentication Requirements

**API Authentication:**
- API Key: `X-API-Key` header (format: `pk_live_xxx` or `pk_test_xxx`)
- OAuth 2.0: JWT tokens with merchant claims
- Token Expiry: 1 hour (configurable)

**Administrative Access:**
- Multi-Factor Authentication (MFA) required
- Session timeout: 15 minutes
- IP allowlisting for admin endpoints

### Least Privilege Implementation

```yaml
# Service account with minimal permissions
apiVersion: v1
kind: ServiceAccount
metadata:
  name: authorization-service
  namespace: payment-gateway
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: authorization-service-role
  namespace: payment-gateway
rules:
  - apiGroups: [""]
    resources: ["configmaps", "secrets"]
    verbs: ["get"]
    resourceNames: ["authorization-config", "db-credentials"]
```

## Network Security

### Firewall Rules

**Inbound (from Internet):**
- HTTPS (443) → Load Balancer → Authorization Service (8446)
- All other ports blocked

**Internal Communication:**
- Authorization → Tokenization: gRPC (8445), mTLS
- Authorization → Fraud: gRPC (9447), mTLS
- Authorization → 3DS: gRPC (9448), mTLS
- All services → Database: PostgreSQL (5432), TLS
- All services → Redis: Redis (6379), TLS
- All services → Kafka: Kafka (9092), SASL_SSL

### TLS Configuration

```yaml
# TLS 1.3 only configuration
server:
  ssl:
    enabled: true
    protocol: TLS
    enabled-protocols: TLSv1.3
    ciphers:
      - TLS_AES_256_GCM_SHA384
      - TLS_CHACHA20_POLY1305_SHA256
    client-auth: need  # mTLS for internal
```

### Security Headers

All API responses include:
```
Content-Security-Policy: default-src 'none'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Cache-Control: no-store
```

## Monitoring and Logging

### Audit Log Requirements

All audit logs include:
- Timestamp (UTC, ISO 8601)
- User/Service identity
- Action performed
- Resource affected
- Source IP address
- Correlation ID (trace ID)
- Success/failure status

### Logged Events

| Event Category | Events |
|----------------|--------|
| Authentication | Login, logout, failed attempts, token refresh |
| Authorization | Permission checks, access denied |
| Data Access | Card data access, detokenization requests |
| Administrative | User management, configuration changes |
| Security | Key rotation, certificate changes |
| Payment | Authorization, capture, void, refund |

### Log Retention

- Audit logs: 7 years (PCI DSS requirement)
- Application logs: 90 days
- Security logs: 1 year
- Metrics: 2 years

### PAN Redaction

All logs automatically redact sensitive data:
```
# Before redaction
{"cardNumber": "4532015112830366", "amount": 100}

# After redaction
{"cardNumber": "453201******0366", "amount": 100}
```

### SIEM Integration

Logs are forwarded to SIEM for:
- Real-time alerting on security events
- Correlation of events across services
- Anomaly detection
- Compliance reporting

## Vulnerability Management

### Scanning Schedule

| Scan Type | Frequency | Scope |
|-----------|-----------|-------|
| Container image scan | Every build | All images |
| Dependency scan | Daily | All dependencies |
| SAST (static analysis) | Every PR | Source code |
| DAST (dynamic analysis) | Weekly | Running services |
| Infrastructure scan | Weekly | Cloud resources |
| Penetration test | Quarterly | Full system |
| ASV scan | Quarterly | External perimeter |

### Vulnerability Response

| Severity | Response Time | Action |
|----------|---------------|--------|
| Critical | 24 hours | Immediate patch or mitigation |
| High | 7 days | Patch in next release |
| Medium | 30 days | Scheduled patch |
| Low | 90 days | Backlog |

### Patch Management

1. **Identification**: Automated scanning identifies vulnerabilities
2. **Assessment**: Security team assesses impact and priority
3. **Testing**: Patch tested in staging environment
4. **Deployment**: Patch deployed with rollback plan
5. **Verification**: Post-deployment scan confirms fix
6. **Documentation**: Update vulnerability register

## Incident Response

### Incident Classification

| Level | Description | Response Time |
|-------|-------------|---------------|
| P1 - Critical | Data breach, service down | 15 minutes |
| P2 - High | Security incident, degraded service | 1 hour |
| P3 - Medium | Potential vulnerability, minor issue | 4 hours |
| P4 - Low | Informational, improvement | 24 hours |

### Incident Response Procedure

1. **Detection**: Alert triggered or incident reported
2. **Triage**: Assess severity and impact
3. **Containment**: Isolate affected systems
4. **Investigation**: Determine root cause
5. **Eradication**: Remove threat
6. **Recovery**: Restore normal operations
7. **Post-Incident**: Document lessons learned

### Data Breach Response

If cardholder data breach is suspected:

1. **Immediate Actions (0-1 hour)**
   - Isolate affected systems
   - Preserve evidence (logs, memory dumps)
   - Notify incident response team
   - Engage forensics if needed

2. **Investigation (1-24 hours)**
   - Determine scope of breach
   - Identify compromised data
   - Identify attack vector
   - Document timeline

3. **Notification (24-72 hours)**
   - Notify card brands (Visa, Mastercard)
   - Notify acquiring bank
   - Notify affected merchants
   - Prepare regulatory notifications

4. **Remediation**
   - Patch vulnerabilities
   - Rotate compromised credentials
   - Enhance monitoring
   - Update security controls

## Compliance Validation

### Self-Assessment Questionnaire (SAQ)

For merchants using the gateway:
- SAQ A: Card-not-present, fully outsourced
- SAQ A-EP: E-commerce with website payment page

### Report on Compliance (ROC)

Annual ROC requirements:
- Qualified Security Assessor (QSA) audit
- Penetration test results
- Vulnerability scan results
- Policy and procedure review
- Evidence of controls

### Attestation of Compliance (AOC)

Provided to:
- Card brands (Visa, Mastercard, etc.)
- Acquiring banks
- Merchants (upon request)

### Compliance Calendar

| Month | Activity |
|-------|----------|
| January | Annual security training |
| February | Policy review |
| March | Q1 penetration test |
| April | ASV scan |
| May | Key rotation |
| June | Q2 penetration test |
| July | ASV scan |
| August | Key rotation |
| September | Q3 penetration test |
| October | ASV scan, QSA audit begins |
| November | Key rotation, QSA audit |
| December | ROC/AOC submission |
