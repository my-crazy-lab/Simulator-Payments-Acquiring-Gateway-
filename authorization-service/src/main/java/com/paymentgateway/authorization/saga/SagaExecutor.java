package com.paymentgateway.authorization.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executes a saga consisting of multiple steps with automatic compensation on failure.
 * 
 * The saga pattern ensures that when a distributed transaction fails partway through,
 * all previously completed steps are compensated (rolled back) to maintain consistency.
 */
@Component
public class SagaExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(SagaExecutor.class);
    
    /**
     * Execute a saga with the given steps.
     * If any step fails, all previously executed steps will be compensated in reverse order.
     *
     * @param sagaName Name of the saga for logging
     * @param context The shared context for all steps
     * @param steps The steps to execute in order
     * @return SagaResult containing execution details
     */
    public <T> SagaResult<T> execute(String sagaName, T context, List<SagaStep<T>> steps) {
        logger.info("Starting saga: {} with {} steps", sagaName, steps.size());
        
        List<String> executedSteps = new ArrayList<>();
        List<SagaStep<T>> executedStepObjects = new ArrayList<>();
        
        // Execute steps in order
        for (SagaStep<T> step : steps) {
            try {
                logger.debug("Saga {}: executing step {}", sagaName, step.getName());
                boolean success = step.execute(context);
                
                if (!success) {
                    logger.warn("Saga {}: step {} returned false, initiating compensation", 
                               sagaName, step.getName());
                    return compensateAndBuildResult(sagaName, context, executedStepObjects, 
                                                   step.getName(), "Step returned false");
                }
                
                executedSteps.add(step.getName());
                executedStepObjects.add(step);
                
            } catch (SagaStepException e) {
                logger.error("Saga {}: step {} failed with exception: {}", 
                            sagaName, step.getName(), e.getMessage());
                return compensateAndBuildResult(sagaName, context, executedStepObjects, 
                                               step.getName(), e.getMessage());
            }
        }
        
        logger.info("Saga {} completed successfully with {} steps", sagaName, executedSteps.size());
        
        return SagaResult.<T>builder()
            .success(true)
            .context(context)
            .executedSteps(executedSteps)
            .build();
    }
    
    /**
     * Compensate all executed steps in reverse order.
     */
    private <T> SagaResult<T> compensateAndBuildResult(String sagaName, T context,
                                                       List<SagaStep<T>> executedSteps,
                                                       String failedStepName,
                                                       String failureReason) {
        logger.info("Saga {}: compensating {} steps due to failure at {}", 
                   sagaName, executedSteps.size(), failedStepName);
        
        List<String> compensatedSteps = new ArrayList<>();
        List<String> failedCompensations = new ArrayList<>();
        List<String> executedStepNames = new ArrayList<>();
        
        // Collect executed step names
        for (SagaStep<T> step : executedSteps) {
            executedStepNames.add(step.getName());
        }
        
        // Compensate in reverse order
        List<SagaStep<T>> reversedSteps = new ArrayList<>(executedSteps);
        Collections.reverse(reversedSteps);
        
        for (SagaStep<T> step : reversedSteps) {
            try {
                logger.debug("Saga {}: compensating step {}", sagaName, step.getName());
                boolean compensated = step.compensate(context);
                
                if (compensated) {
                    compensatedSteps.add(step.getName());
                    logger.info("Saga {}: step {} compensated successfully", sagaName, step.getName());
                } else {
                    failedCompensations.add(step.getName());
                    logger.error("Saga {}: step {} compensation returned false", sagaName, step.getName());
                }
            } catch (Exception e) {
                failedCompensations.add(step.getName());
                logger.error("Saga {}: step {} compensation threw exception: {}", 
                            sagaName, step.getName(), e.getMessage());
            }
        }
        
        if (!failedCompensations.isEmpty()) {
            logger.error("Saga {}: {} compensations failed, manual intervention may be required", 
                        sagaName, failedCompensations.size());
        }
        
        return SagaResult.<T>builder()
            .success(false)
            .context(context)
            .failedStepName(failedStepName)
            .failureReason(failureReason)
            .executedSteps(executedStepNames)
            .compensatedSteps(compensatedSteps)
            .failedCompensations(failedCompensations)
            .build();
    }
}
