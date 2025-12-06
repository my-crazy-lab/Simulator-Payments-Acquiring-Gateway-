package com.paymentgateway.authorization.saga;

/**
 * Represents a single step in a saga transaction.
 * Each step has an execute action and a compensate action for rollback.
 *
 * @param <T> The context type shared across saga steps
 */
public interface SagaStep<T> {
    
    /**
     * Get the name of this saga step for logging and tracking.
     */
    String getName();
    
    /**
     * Execute the forward action of this step.
     *
     * @param context The saga context containing shared state
     * @return true if execution succeeded, false otherwise
     * @throws SagaStepException if execution fails
     */
    boolean execute(T context) throws SagaStepException;
    
    /**
     * Execute the compensating action to rollback this step.
     * This is called when a subsequent step fails.
     *
     * @param context The saga context containing shared state
     * @return true if compensation succeeded, false otherwise
     */
    boolean compensate(T context);
    
    /**
     * Check if this step was executed successfully.
     */
    boolean isExecuted();
    
    /**
     * Check if this step requires compensation (was executed but saga failed).
     */
    boolean requiresCompensation();
}
