package com.paymentgateway.authorization.saga;

/**
 * Exception thrown when a saga step fails during execution.
 */
public class SagaStepException extends RuntimeException {
    
    private final String stepName;
    private final boolean compensatable;
    
    public SagaStepException(String stepName, String message) {
        super(message);
        this.stepName = stepName;
        this.compensatable = true;
    }
    
    public SagaStepException(String stepName, String message, Throwable cause) {
        super(message, cause);
        this.stepName = stepName;
        this.compensatable = true;
    }
    
    public SagaStepException(String stepName, String message, boolean compensatable) {
        super(message);
        this.stepName = stepName;
        this.compensatable = compensatable;
    }
    
    public String getStepName() {
        return stepName;
    }
    
    public boolean isCompensatable() {
        return compensatable;
    }
}
