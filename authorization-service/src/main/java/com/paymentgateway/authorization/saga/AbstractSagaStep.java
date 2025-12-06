package com.paymentgateway.authorization.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base implementation of SagaStep with common functionality.
 *
 * @param <T> The context type shared across saga steps
 */
public abstract class AbstractSagaStep<T> implements SagaStep<T> {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final String name;
    private boolean executed = false;
    private boolean compensated = false;
    
    protected AbstractSagaStep(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public final boolean execute(T context) throws SagaStepException {
        logger.debug("Executing saga step: {}", name);
        try {
            boolean result = doExecute(context);
            if (result) {
                executed = true;
                logger.debug("Saga step {} executed successfully", name);
            } else {
                logger.warn("Saga step {} returned false", name);
            }
            return result;
        } catch (Exception e) {
            logger.error("Saga step {} failed: {}", name, e.getMessage());
            throw new SagaStepException(name, e.getMessage(), e);
        }
    }
    
    @Override
    public final boolean compensate(T context) {
        if (!executed || compensated) {
            logger.debug("Skipping compensation for step {}: executed={}, compensated={}", 
                        name, executed, compensated);
            return true;
        }
        
        logger.info("Compensating saga step: {}", name);
        try {
            boolean result = doCompensate(context);
            if (result) {
                compensated = true;
                logger.info("Saga step {} compensated successfully", name);
            } else {
                logger.warn("Saga step {} compensation returned false", name);
            }
            return result;
        } catch (Exception e) {
            logger.error("Saga step {} compensation failed: {}", name, e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public boolean requiresCompensation() {
        return executed && !compensated;
    }
    
    /**
     * Implement the actual execution logic.
     */
    protected abstract boolean doExecute(T context) throws Exception;
    
    /**
     * Implement the actual compensation logic.
     */
    protected abstract boolean doCompensate(T context);
    
    /**
     * Reset the step state (for testing purposes).
     */
    public void reset() {
        executed = false;
        compensated = false;
    }
}
