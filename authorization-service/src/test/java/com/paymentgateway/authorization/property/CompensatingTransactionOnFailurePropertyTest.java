package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.saga.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: payment-acquiring-gateway, Property 40: Compensating Transaction on Failure
 * 
 * For any transaction that fails after partial completion, compensating actions 
 * should be executed to maintain consistency (e.g., releasing reserved funds).
 * 
 * Validates: Requirements 16.2
 */
class CompensatingTransactionOnFailurePropertyTest {
    
    private final SagaExecutor sagaExecutor = new SagaExecutor();
    
    /**
     * Property: When a saga step fails, all previously executed steps should be compensated.
     * 
     * For any saga with N steps where step K fails (K > 0), 
     * steps 0 to K-1 should all be compensated in reverse order.
     */
    @Property(tries = 100)
    void allPreviousStepsAreCompensatedOnFailure(
            @ForAll @IntRange(min = 2, max = 10) int totalSteps,
            @ForAll @IntRange(min = 1, max = 9) int failingStepIndex) {
        
        // Ensure failing step is within bounds
        int actualFailingStep = Math.min(failingStepIndex, totalSteps - 1);
        
        // Track execution and compensation
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        
        // Build steps
        List<SagaStep<TestContext>> steps = new ArrayList<>();
        for (int i = 0; i < totalSteps; i++) {
            final int stepIndex = i;
            final boolean shouldFail = (i == actualFailingStep);
            
            steps.add(new TrackingSagaStep(
                "Step" + i,
                shouldFail,
                executionOrder,
                compensationOrder
            ));
        }
        
        // Execute saga
        TestContext context = new TestContext();
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", context, steps);
        
        // Assert: Saga should fail
        assertThat(result.isSuccess())
            .as("Saga should fail when step %d fails", actualFailingStep)
            .isFalse();
        
        // Assert: Failed step name should be recorded
        assertThat(result.getFailedStepName())
            .as("Failed step name should be recorded")
            .isEqualTo("Step" + actualFailingStep);
        
        // Assert: All steps before the failing step should have been executed
        assertThat(executionOrder)
            .as("Steps 0 to %d should have been executed", actualFailingStep)
            .hasSize(actualFailingStep + 1);
        
        // Assert: All executed steps (except the failing one) should be compensated
        assertThat(compensationOrder)
            .as("All %d steps before failing step should be compensated", actualFailingStep)
            .hasSize(actualFailingStep);
        
        // Assert: Compensation should happen in reverse order
        for (int i = 0; i < actualFailingStep; i++) {
            String expectedStep = "Step" + (actualFailingStep - 1 - i);
            assertThat(compensationOrder.get(i))
                .as("Compensation order should be reverse: position %d should be %s", i, expectedStep)
                .isEqualTo(expectedStep);
        }
        
        // Assert: Compensated steps should match executed steps (minus the failing one)
        assertThat(result.getCompensatedSteps())
            .as("Compensated steps should be recorded in result")
            .hasSize(actualFailingStep);
    }
    
    /**
     * Property: When all steps succeed, no compensation should occur.
     */
    @Property(tries = 100)
    void noCompensationOnSuccess(
            @ForAll @IntRange(min = 1, max = 10) int totalSteps) {
        
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        
        // Build all successful steps
        List<SagaStep<TestContext>> steps = new ArrayList<>();
        for (int i = 0; i < totalSteps; i++) {
            steps.add(new TrackingSagaStep(
                "Step" + i,
                false, // Never fail
                executionOrder,
                compensationOrder
            ));
        }
        
        // Execute saga
        TestContext context = new TestContext();
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", context, steps);
        
        // Assert: Saga should succeed
        assertThat(result.isSuccess())
            .as("Saga should succeed when all steps pass")
            .isTrue();
        
        // Assert: All steps should be executed
        assertThat(executionOrder)
            .as("All %d steps should be executed", totalSteps)
            .hasSize(totalSteps);
        
        // Assert: No compensation should occur
        assertThat(compensationOrder)
            .as("No compensation should occur on success")
            .isEmpty();
        
        assertThat(result.getCompensatedSteps())
            .as("No compensated steps in result")
            .isEmpty();
    }
    
    /**
     * Property: First step failure should not require any compensation.
     */
    @Property(tries = 100)
    void firstStepFailureNoCompensation(
            @ForAll @IntRange(min = 1, max = 10) int totalSteps) {
        
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        
        // Build steps with first one failing
        List<SagaStep<TestContext>> steps = new ArrayList<>();
        for (int i = 0; i < totalSteps; i++) {
            steps.add(new TrackingSagaStep(
                "Step" + i,
                i == 0, // First step fails
                executionOrder,
                compensationOrder
            ));
        }
        
        // Execute saga
        TestContext context = new TestContext();
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", context, steps);
        
        // Assert: Saga should fail
        assertThat(result.isSuccess())
            .as("Saga should fail when first step fails")
            .isFalse();
        
        // Assert: Only first step attempted execution
        assertThat(executionOrder)
            .as("Only first step should attempt execution")
            .hasSize(1);
        
        // Assert: No compensation needed (first step failed, nothing to compensate)
        assertThat(compensationOrder)
            .as("No compensation needed when first step fails")
            .isEmpty();
    }
    
    /**
     * Property: Compensation failures should be tracked but not stop other compensations.
     */
    @Property(tries = 50)
    void compensationFailuresAreTracked(
            @ForAll @IntRange(min = 3, max = 8) int totalSteps,
            @ForAll @IntRange(min = 2, max = 7) int failingStepIndex,
            @ForAll @IntRange(min = 0, max = 6) int compensationFailIndex) {
        
        int actualFailingStep = Math.min(failingStepIndex, totalSteps - 1);
        int actualCompFailIndex = Math.min(compensationFailIndex, actualFailingStep - 1);
        
        if (actualFailingStep < 2) {
            return; // Need at least 2 steps before failure for meaningful test
        }
        
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        AtomicInteger compensationAttempts = new AtomicInteger(0);
        
        // Build steps
        List<SagaStep<TestContext>> steps = new ArrayList<>();
        for (int i = 0; i < totalSteps; i++) {
            final int stepIndex = i;
            final boolean shouldFail = (i == actualFailingStep);
            final boolean compensationShouldFail = (i == actualCompFailIndex);
            
            steps.add(new TrackingSagaStep(
                "Step" + i,
                shouldFail,
                compensationShouldFail,
                executionOrder,
                compensationOrder,
                compensationAttempts
            ));
        }
        
        // Execute saga
        TestContext context = new TestContext();
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", context, steps);
        
        // Assert: Saga should fail
        assertThat(result.isSuccess()).isFalse();
        
        // Assert: All compensations should be attempted (even if some fail)
        assertThat(compensationAttempts.get())
            .as("All %d compensations should be attempted", actualFailingStep)
            .isEqualTo(actualFailingStep);
        
        // Assert: Failed compensations should be tracked
        assertThat(result.getFailedCompensations())
            .as("Failed compensation should be tracked")
            .contains("Step" + actualCompFailIndex);
    }
    
    /**
     * Property: Executed steps list should match what was actually executed.
     */
    @Property(tries = 100)
    void executedStepsMatchActualExecution(
            @ForAll @IntRange(min = 1, max = 10) int totalSteps,
            @ForAll @IntRange(min = 0, max = 9) int failingStepIndex) {
        
        int actualFailingStep = Math.min(failingStepIndex, totalSteps - 1);
        
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        
        List<SagaStep<TestContext>> steps = new ArrayList<>();
        for (int i = 0; i < totalSteps; i++) {
            steps.add(new TrackingSagaStep(
                "Step" + i,
                i == actualFailingStep,
                executionOrder,
                compensationOrder
            ));
        }
        
        TestContext context = new TestContext();
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", context, steps);
        
        // The executed steps in result should match what we tracked
        // Note: The failing step is also counted as "executed" (attempted)
        assertThat(result.getExecutedSteps())
            .as("Executed steps should match actual execution")
            .hasSize(actualFailingStep); // Steps before the failing one
    }
    
    // ==================== Test Helpers ====================
    
    static class TestContext {
        private int value = 0;
        
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
    
    static class TrackingSagaStep extends AbstractSagaStep<TestContext> {
        private final boolean shouldFail;
        private final boolean compensationShouldFail;
        private final List<String> executionOrder;
        private final List<String> compensationOrder;
        private final AtomicInteger compensationAttempts;
        
        TrackingSagaStep(String name, boolean shouldFail,
                        List<String> executionOrder, List<String> compensationOrder) {
            this(name, shouldFail, false, executionOrder, compensationOrder, new AtomicInteger(0));
        }
        
        TrackingSagaStep(String name, boolean shouldFail, boolean compensationShouldFail,
                        List<String> executionOrder, List<String> compensationOrder,
                        AtomicInteger compensationAttempts) {
            super(name);
            this.shouldFail = shouldFail;
            this.compensationShouldFail = compensationShouldFail;
            this.executionOrder = executionOrder;
            this.compensationOrder = compensationOrder;
            this.compensationAttempts = compensationAttempts;
        }
        
        @Override
        protected boolean doExecute(TestContext context) throws Exception {
            executionOrder.add(getName());
            if (shouldFail) {
                throw new SagaStepException(getName(), "Simulated failure at " + getName());
            }
            return true;
        }
        
        @Override
        protected boolean doCompensate(TestContext context) {
            compensationAttempts.incrementAndGet();
            if (compensationShouldFail) {
                return false;
            }
            compensationOrder.add(getName());
            return true;
        }
    }
}
