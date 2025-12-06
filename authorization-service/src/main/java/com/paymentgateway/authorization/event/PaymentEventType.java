package com.paymentgateway.authorization.event;

public enum PaymentEventType {
    PAYMENT_CREATED,
    PAYMENT_AUTHORIZED,
    PAYMENT_DECLINED,
    PAYMENT_CAPTURED,
    PAYMENT_CANCELLED,
    PAYMENT_REFUNDED,
    PAYMENT_FAILED
}
