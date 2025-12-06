package com.paymentgateway.authorization.domain;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    SETTLED,
    FAILED,
    CANCELLED,
    REFUNDED
}
