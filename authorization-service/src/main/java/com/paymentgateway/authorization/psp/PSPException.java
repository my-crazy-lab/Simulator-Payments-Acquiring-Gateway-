package com.paymentgateway.authorization.psp;

/**
 * Exception thrown when PSP operations fail
 */
public class PSPException extends RuntimeException {
    
    private final String pspName;
    private final String errorCode;
    private final boolean retryable;
    
    public PSPException(String pspName, String message) {
        super(message);
        this.pspName = pspName;
        this.errorCode = "PSP_ERROR";
        this.retryable = false;
    }
    
    public PSPException(String pspName, String message, Throwable cause) {
        super(message, cause);
        this.pspName = pspName;
        this.errorCode = "PSP_ERROR";
        this.retryable = true; // Network errors are typically retryable
    }
    
    public PSPException(String pspName, String errorCode, String message, boolean retryable) {
        super(message);
        this.pspName = pspName;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public String getPspName() {
        return pspName;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
}
