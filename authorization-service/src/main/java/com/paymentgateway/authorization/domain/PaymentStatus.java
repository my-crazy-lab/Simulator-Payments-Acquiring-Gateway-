package com.paymentgateway.authorization.domain;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    DECLINED,
    CAPTURED,
    SETTLED,
    FAILED,
    CANCELLED,
    REFUNDED
}
