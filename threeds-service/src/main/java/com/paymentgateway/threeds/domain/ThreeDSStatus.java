package com.paymentgateway.threeds.domain;

public enum ThreeDSStatus {
    UNKNOWN,
    FRICTIONLESS,
    CHALLENGE_REQUIRED,
    AUTHENTICATED,
    FAILED,
    TIMEOUT
}
