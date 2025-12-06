package com.paymentgateway.authorization.saga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SagaExecutor covering compensation scenarios.
 * 
 * Validates: Requirements 16.2 - Compensating transactions on failure
 */
class SagaExecutorTest {
    
    private SagaExecutor sagaExecutor;
    
    @BeforeEach
    void setUp() {
        sagaExecutor = new SagaExecutor();
    }
    
    // ==================== Partial Failure Recovery Tests ====================
    
    @Test
    @DisplayName("Should compensate all executed steps when middle step fails")
    void shouldCompensateAllExecutedStepsWhenMiddleStepFails() {
        // Arrange
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("Step1", false, executionOrder, compensationOrder),
            new TrackingStep("Step2", false, executionOrder, compensationOrder),
            new TrackingStep("Step3", true, executionOrder, compensationOrder), // Fails
            new TrackingStep("Step4", false, executionOrder, compensationOrder),
            new TrackingStep("Step5", false, executionOrder, compensationOrder)
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailedStepName()).isEqualTo("Step3");
        
        // Steps 1, 2, 3 were attempted (3 failed during execution)
        assertThat(executionOrder).containsExactly("Step1", "Step2", "Step3");
        
        // Steps 1, 2 should be compensated in reverse order
        assertThat(compensationOrder).containsExactly("Step2", "Step1");
        
        // Steps 4, 5 should never have been executed
        assertThat(executionOrder).doesNotContain("Step4", "Step5");
    }
    
    @Test
    @DisplayName("Should handle first step failure without compensation")
    void shouldHandleFirstStepFailureWithoutCompensation() {
        // Arrange
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("Step1", true, executionOrder, compensationOrder), // Fails immediately
            new TrackingStep("Step2", false, executionOrder, compensationOrder),
            new TrackingStep("Step3", false, executionOrder, compensationOrder)
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailedStepName()).isEqualTo("Step1");
        assertThat(executionOrder).containsExactly("Step1");
        assertThat(compensationOrder).isEmpty(); // Nothing to compensate
    }
    
    @Test
    @DisplayName("Should handle last step failure with full compensation")
    void shouldHandleLastStepFailureWithFullCompensation() {
        // Arrange
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("Step1", false, executionOrder, compensationOrder),
            new TrackingStep("Step2", false, executionOrder, compensationOrder),
            new TrackingStep("Step3", false, executionOrder, compensationOrder),
            new TrackingStep("Step4", true, executionOrder, compensationOrder) // Last step fails
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailedStepName()).isEqualTo("Step4");
        assertThat(executionOrder).containsExactly("Step1", "Step2", "Step3", "Step4");
        assertThat(compensationOrder).containsExactly("Step3", "Step2", "Step1");
    }
    
    // ==================== Rollback Logic Tests ====================
    
    @Test
    @DisplayName("Should execute compensations in reverse order")
    void shouldExecuteCompensationsInReverseOrder() {
        // Arrange
        List<String> compensationOrder = new ArrayList<>();
        
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("A", false, new ArrayList<>(), compensationOrder),
            new TrackingStep("B", false, new ArrayList<>(), compensationOrder),
            new TrackingStep("C", false, new ArrayList<>(), compensationOrder),
            new TrackingStep("D", false, new ArrayList<>(), compensationOrder),
            new TrackingStep("E", true, new ArrayList<>(), compensationOrder) // Fails
        );
        
        // Act
        sagaExecutor.execute("TestSaga", new TestContext(), steps);
        
        // Assert - Compensation should be D, C, B, A (reverse of execution)
        assertThat(compensationOrder).containsExactly("D", "C", "B", "A");
    }
    
    @Test
    @DisplayName("Should continue compensation even when one compensation fails")
    void shouldContinueCompensationEvenWhenOneCompensationFails() {
        // Arrange
        AtomicInteger compensationAttempts = new AtomicInteger(0);
        List<String> compensationOrder = new ArrayList<>();
        
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("Step1", false, false, new ArrayList<>(), compensationOrder, compensationAttempts),
            new TrackingStep("Step2", false, true, new ArrayList<>(), compensationOrder, compensationAttempts), // Compensation fails
            new TrackingStep("Step3", false, false, new ArrayList<>(), compensationOrder, compensationAttempts),
            new TrackingStep("Step4", true, false, new ArrayList<>(), compensationOrder, compensationAttempts) // Execution fails
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        
        // All 3 compensations should be attempted
        assertThat(compensationAttempts.get()).isEqualTo(3);
        
        // Step2 compensation failed, so only Step3 and Step1 are in compensationOrder
        assertThat(compensationOrder).containsExactly("Step3", "Step1");
        
        // Failed compensation should be tracked
        assertThat(result.getFailedCompensations()).contains("Step2");
    }
    
    @Test
    @DisplayName("Should track failed compensations in result")
    void shouldTrackFailedCompensationsInResult() {
        // Arrange
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("Step1", false, true, new ArrayList<>(), new ArrayList<>(), new AtomicInteger()), // Comp fails
            new TrackingStep("Step2", false, true, new ArrayList<>(), new ArrayList<>(), new AtomicInteger()), // Comp fails
            new TrackingStep("Step3", true, false, new ArrayList<>(), new ArrayList<>(), new AtomicInteger()) // Exec fails
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.hasCompensationFailures()).isTrue();
        assertThat(result.getFailedCompensations()).containsExactlyInAnyOrder("Step1", "Step2");
    }
    
    // ==================== Saga Coordination Tests ====================
    
    @Test
    @DisplayName("Should complete successfully when all steps pass")
    void shouldCompleteSuccessfullyWhenAllStepsPass() {
        // Arrange
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("Step1", false, executionOrder, compensationOrder),
            new TrackingStep("Step2", false, executionOrder, compensationOrder),
            new TrackingStep("Step3", false, executionOrder, compensationOrder)
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFailedStepName()).isNull();
        assertThat(executionOrder).containsExactly("Step1", "Step2", "Step3");
        assertThat(compensationOrder).isEmpty();
        assertThat(result.getExecutedSteps()).containsExactly("Step1", "Step2", "Step3");
    }
    
    @Test
    @DisplayName("Should handle empty saga")
    void shouldHandleEmptySaga() {
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("EmptySaga", new TestContext(), List.of());
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutedSteps()).isEmpty();
        assertThat(result.getCompensatedSteps()).isEmpty();
    }
    
    @Test
    @DisplayName("Should handle single step saga success")
    void shouldHandleSingleStepSagaSuccess() {
        // Arrange
        List<String> executionOrder = new ArrayList<>();
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("OnlyStep", false, executionOrder, new ArrayList<>())
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("SingleStepSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(executionOrder).containsExactly("OnlyStep");
    }
    
    @Test
    @DisplayName("Should handle single step saga failure")
    void shouldHandleSingleStepSagaFailure() {
        // Arrange
        List<String> executionOrder = new ArrayList<>();
        List<String> compensationOrder = new ArrayList<>();
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("OnlyStep", true, executionOrder, compensationOrder)
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("SingleStepSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailedStepName()).isEqualTo("OnlyStep");
        assertThat(compensationOrder).isEmpty(); // Nothing to compensate
    }
    
    @Test
    @DisplayName("Should preserve context across all steps")
    void shouldPreserveContextAcrossAllSteps() {
        // Arrange
        TestContext context = new TestContext();
        
        List<SagaStep<TestContext>> steps = List.of(
            new ContextModifyingStep("Step1", 10),
            new ContextModifyingStep("Step2", 20),
            new ContextModifyingStep("Step3", 30)
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("ContextSaga", context, steps);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContext().getValue()).isEqualTo(60); // 10 + 20 + 30
    }
    
    @Test
    @DisplayName("Should include failure reason in result")
    void shouldIncludeFailureReasonInResult() {
        // Arrange
        List<SagaStep<TestContext>> steps = List.of(
            new TrackingStep("Step1", false, new ArrayList<>(), new ArrayList<>()),
            new FailingStepWithMessage("Step2", "Database connection failed")
        );
        
        // Act
        SagaResult<TestContext> result = sagaExecutor.execute("TestSaga", new TestContext(), steps);
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo("Database connection failed");
    }
    
    // ==================== Test Helpers ====================
    
    static class TestContext {
        private int value = 0;
        
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public void addValue(int delta) { this.value += delta; }
    }
    
    static class TrackingStep extends AbstractSagaStep<TestContext> {
        private final boolean shouldFail;
        private final boolean compensationShouldFail;
        private final List<String> executionOrder;
        private final List<String> compensationOrder;
        private final AtomicInteger compensationAttempts;
        
        TrackingStep(String name, boolean shouldFail, 
                    List<String> executionOrder, List<String> compensationOrder) {
            this(name, shouldFail, false, executionOrder, compensationOrder, new AtomicInteger(0));
        }
        
        TrackingStep(String name, boolean shouldFail, boolean compensationShouldFail,
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
    
    static class ContextModifyingStep extends AbstractSagaStep<TestContext> {
        private final int valueToAdd;
        
        ContextModifyingStep(String name, int valueToAdd) {
            super(name);
            this.valueToAdd = valueToAdd;
        }
        
        @Override
        protected boolean doExecute(TestContext context) {
            context.addValue(valueToAdd);
            return true;
        }
        
        @Override
        protected boolean doCompensate(TestContext context) {
            context.addValue(-valueToAdd);
            return true;
        }
    }
    
    static class FailingStepWithMessage extends AbstractSagaStep<TestContext> {
        private final String errorMessage;
        
        FailingStepWithMessage(String name, String errorMessage) {
            super(name);
            this.errorMessage = errorMessage;
        }
        
        @Override
        protected boolean doExecute(TestContext context) throws Exception {
            throw new SagaStepException(getName(), errorMessage);
        }
        
        @Override
        protected boolean doCompensate(TestContext context) {
            return true;
        }
    }
}
