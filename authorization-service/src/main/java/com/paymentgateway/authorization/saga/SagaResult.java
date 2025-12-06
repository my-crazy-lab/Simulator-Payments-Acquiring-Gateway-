package com.paymentgateway.authorization.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a saga execution containing success status and compensation details.
 */
public class SagaResult<T> {
    
    private final boolean success;
    private final T context;
    private final String failedStepName;
    private final String failureReason;
    private final List<String> executedSteps;
    private final List<String> compensatedSteps;
    private final List<String> failedCompensations;
    
    private SagaResult(Builder<T> builder) {
        this.success = builder.success;
        this.context = builder.context;
        this.failedStepName = builder.failedStepName;
        this.failureReason = builder.failureReason;
        this.executedSteps = Collections.unmodifiableList(new ArrayList<>(builder.executedSteps));
        this.compensatedSteps = Collections.unmodifiableList(new ArrayList<>(builder.compensatedSteps));
        this.failedCompensations = Collections.unmodifiableList(new ArrayList<>(builder.failedCompensations));
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public T getContext() {
        return context;
    }
    
    public String getFailedStepName() {
        return failedStepName;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public List<String> getExecutedSteps() {
        return executedSteps;
    }
    
    public List<String> getCompensatedSteps() {
        return compensatedSteps;
    }
    
    public List<String> getFailedCompensations() {
        return failedCompensations;
    }
    
    public boolean hasCompensationFailures() {
        return !failedCompensations.isEmpty();
    }
    
    public boolean wasCompensated() {
        return !compensatedSteps.isEmpty();
    }
    
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    public static class Builder<T> {
        private boolean success;
        private T context;
        private String failedStepName;
        private String failureReason;
        private List<String> executedSteps = new ArrayList<>();
        private List<String> compensatedSteps = new ArrayList<>();
        private List<String> failedCompensations = new ArrayList<>();
        
        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder<T> context(T context) {
            this.context = context;
            return this;
        }
        
        public Builder<T> failedStepName(String failedStepName) {
            this.failedStepName = failedStepName;
            return this;
        }
        
        public Builder<T> failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }
        
        public Builder<T> executedSteps(List<String> executedSteps) {
            this.executedSteps = executedSteps;
            return this;
        }
        
        public Builder<T> compensatedSteps(List<String> compensatedSteps) {
            this.compensatedSteps = compensatedSteps;
            return this;
        }
        
        public Builder<T> failedCompensations(List<String> failedCompensations) {
            this.failedCompensations = failedCompensations;
            return this;
        }
        
        public Builder<T> addExecutedStep(String step) {
            this.executedSteps.add(step);
            return this;
        }
        
        public Builder<T> addCompensatedStep(String step) {
            this.compensatedSteps.add(step);
            return this;
        }
        
        public Builder<T> addFailedCompensation(String step) {
            this.failedCompensations.add(step);
            return this;
        }
        
        public SagaResult<T> build() {
            return new SagaResult<>(this);
        }
    }
}
