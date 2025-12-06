package com.paymentgateway.authorization.saga;

import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.PaymentRequest;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.event.PaymentEventPublisher;
import com.paymentgateway.authorization.event.PaymentEventType;
import com.paymentgateway.authorization.psp.*;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import com.paymentgateway.authorization.repository.PaymentRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service that implements the saga pattern for payment processing.
 * Ensures that when any step fails, all previously completed steps are compensated.
 * 
 * Validates: Requirements 16.2 - Compensating transactions on failure
 */
@Service
public class CompensatingTransactionService {
    
    private static final Logger logger = LoggerFactory.getLogger(CompensatingTransactionService.class);
    
    private final SagaExecutor sagaExecutor;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PSPRoutingService pspRoutingService;
    private final PaymentEventPublisher eventPublisher;
    private final Tracer tracer;
    
    public CompensatingTransactionService(SagaExecutor sagaExecutor,
                                         PaymentRepository paymentRepository,
                                         PaymentEventRepository paymentEventRepository,
                                         PSPRoutingService pspRoutingService,
                                         PaymentEventPublisher eventPublisher,
                                         Tracer tracer) {
        this.sagaExecutor = sagaExecutor;
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.pspRoutingService = pspRoutingService;
        this.eventPublisher = eventPublisher;
        this.tracer = tracer;
    }
    
    /**
     * Process a payment using the saga pattern with automatic compensation on failure.
     */
    @Transactional
    public PaymentResponse processPaymentWithSaga(PaymentRequest request, UUID merchantId) {
        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        
        Span span = tracer.spanBuilder("processPaymentWithSaga").startSpan();
        try (var scope = span.makeCurrent()) {
            PaymentSagaContext context = new PaymentSagaContext(request, merchantId, paymentId);
            
            // Build saga steps
            List<SagaStep<PaymentSagaContext>> steps = buildPaymentSagaSteps();
            
            // Execute saga
            SagaResult<PaymentSagaContext> result = sagaExecutor.execute(
                "PaymentAuthorization-" + paymentId, 
                context, 
                steps
            );
            
            // Build response based on result
            return buildResponse(result);
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Payment saga failed unexpectedly: {}", e.getMessage());
            throw new RuntimeException("Payment processing failed: " + e.getMessage(), e);
        } finally {
            span.end();
        }
    }
    
    /**
     * Build the list of saga steps for payment processing.
     */
    private List<SagaStep<PaymentSagaContext>> buildPaymentSagaSteps() {
        List<SagaStep<PaymentSagaContext>> steps = new ArrayList<>();
        
        // Step 1: Create initial payment record
        steps.add(new CreatePaymentRecordStep(paymentRepository, paymentEventRepository));
        
        // Step 2: Tokenization
        steps.add(new TokenizationStep());
        
        // Step 3: Fraud Detection
        steps.add(new FraudDetectionStep());
        
        // Step 4: 3D Secure (conditional)
        steps.add(new ThreeDSecureStep());
        
        // Step 5: PSP Authorization
        steps.add(new PSPAuthorizationStep(pspRoutingService));
        
        // Step 6: Finalize payment
        steps.add(new FinalizePaymentStep(paymentRepository, paymentEventRepository, eventPublisher));
        
        return steps;
    }
    
    private PaymentResponse buildResponse(SagaResult<PaymentSagaContext> result) {
        PaymentSagaContext context = result.getContext();
        PaymentResponse response = new PaymentResponse();
        
        if (result.isSuccess()) {
            Payment payment = context.getPayment();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            response.setCurrency(payment.getCurrency());
            response.setCardLastFour(payment.getCardLastFour());
            response.setCardBrand(payment.getCardBrand() != null ? payment.getCardBrand().name() : null);
            response.setCreatedAt(payment.getCreatedAt());
            response.setAuthorizedAt(payment.getAuthorizedAt());
        } else {
            response.setPaymentId(context.getPaymentId());
            response.setStatus(PaymentStatus.FAILED);
            response.setAmount(context.getRequest().getAmount());
            response.setCurrency(context.getRequest().getCurrency());
            // Include compensation info in response for debugging
            logger.info("Payment {} failed at step {}, compensated steps: {}", 
                       context.getPaymentId(), 
                       result.getFailedStepName(),
                       result.getCompensatedSteps());
        }
        
        return response;
    }
    
    // ==================== Saga Step Implementations ====================
    
    /**
     * Step 1: Create initial payment record in PENDING status.
     */
    static class CreatePaymentRecordStep extends AbstractSagaStep<PaymentSagaContext> {
        private final PaymentRepository paymentRepository;
        private final PaymentEventRepository paymentEventRepository;
        
        CreatePaymentRecordStep(PaymentRepository paymentRepository, 
                               PaymentEventRepository paymentEventRepository) {
            super("CreatePaymentRecord");
            this.paymentRepository = paymentRepository;
            this.paymentEventRepository = paymentEventRepository;
        }
        
        @Override
        protected boolean doExecute(PaymentSagaContext context) {
            Payment payment = new Payment();
            payment.setPaymentId(context.getPaymentId());
            payment.setMerchantId(context.getMerchantId());
            payment.setAmount(context.getRequest().getAmount());
            payment.setCurrency(context.getRequest().getCurrency());
            payment.setDescription(context.getRequest().getDescription());
            payment.setReferenceId(context.getRequest().getReferenceId());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setBillingStreet(context.getRequest().getBillingStreet());
            payment.setBillingCity(context.getRequest().getBillingCity());
            payment.setBillingState(context.getRequest().getBillingState());
            payment.setBillingZip(context.getRequest().getBillingZip());
            payment.setBillingCountry(context.getRequest().getBillingCountry());
            
            payment = paymentRepository.save(payment);
            context.setPayment(payment);
            context.setPaymentRecordCreated(true);
            
            // Create initial event
            PaymentEvent event = new PaymentEvent(payment.getId(), "SAGA_STARTED", "PENDING");
            event.setAmount(payment.getAmount());
            event.setCurrency(payment.getCurrency());
            paymentEventRepository.save(event);
            
            logger.info("Created payment record: {}", context.getPaymentId());
            return true;
        }
        
        @Override
        protected boolean doCompensate(PaymentSagaContext context) {
            if (context.getPayment() != null && context.isPaymentRecordCreated()) {
                Payment payment = context.getPayment();
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                
                // Create compensation event
                PaymentEvent event = new PaymentEvent(payment.getId(), "SAGA_COMPENSATED", "CANCELLED");
                event.setAmount(payment.getAmount());
                event.setCurrency(payment.getCurrency());
                paymentEventRepository.save(event);
                
                logger.info("Compensated payment record: {} -> CANCELLED", context.getPaymentId());
            }
            return true;
        }
    }
    
    /**
     * Step 2: Tokenize the card number.
     */
    static class TokenizationStep extends AbstractSagaStep<PaymentSagaContext> {
        
        TokenizationStep() {
            super("Tokenization");
        }
        
        @Override
        protected boolean doExecute(PaymentSagaContext context) {
            // Simulate tokenization (in real impl, call tokenization service via gRPC)
            String cardNumber = context.getRequest().getCardNumber();
            UUID tokenId = UUID.randomUUID();
            String lastFour = cardNumber.substring(cardNumber.length() - 4);
            
            context.setCardTokenId(tokenId);
            context.setCardLastFour(lastFour);
            context.setTokenCreated(true);
            
            // Update payment with token info
            Payment payment = context.getPayment();
            payment.setCardTokenId(tokenId);
            payment.setCardLastFour(lastFour);
            payment.setCardBrand(CardBrand.VISA); // Simplified
            
            logger.info("Tokenized card for payment: {}", context.getPaymentId());
            return true;
        }
        
        @Override
        protected boolean doCompensate(PaymentSagaContext context) {
            // In real implementation, would call tokenization service to revoke token
            if (context.isTokenCreated()) {
                logger.info("Compensating tokenization for payment: {} (token would be revoked)", 
                           context.getPaymentId());
                context.setTokenCreated(false);
            }
            return true;
        }
    }
    
    /**
     * Step 3: Perform fraud detection.
     */
    static class FraudDetectionStep extends AbstractSagaStep<PaymentSagaContext> {
        
        FraudDetectionStep() {
            super("FraudDetection");
        }
        
        @Override
        protected boolean doExecute(PaymentSagaContext context) {
            // Simulate fraud detection (in real impl, call fraud service via gRPC)
            double fraudScore = 0.15; // Low risk
            context.setFraudScore(fraudScore);
            context.setFraudCheckPassed(fraudScore < 0.75);
            
            // Update payment with fraud info
            Payment payment = context.getPayment();
            payment.setFraudScore(BigDecimal.valueOf(fraudScore));
            payment.setFraudStatus(fraudScore < 0.75 ? FraudStatus.CLEAN : FraudStatus.BLOCK);
            
            if (!context.isFraudCheckPassed()) {
                context.setFailureReason("Fraud check failed: score=" + fraudScore);
                throw new SagaStepException("FraudDetection", "Transaction blocked by fraud detection");
            }
            
            // Determine if 3DS is required based on fraud score
            context.setThreeDsRequired(fraudScore > 0.5);
            
            logger.info("Fraud detection passed for payment: {}, score={}", 
                       context.getPaymentId(), fraudScore);
            return true;
        }
        
        @Override
        protected boolean doCompensate(PaymentSagaContext context) {
            // In real implementation, would clear any fraud alerts created
            if (context.isFraudAlertCreated()) {
                logger.info("Compensating fraud detection for payment: {} (alerts would be cleared)", 
                           context.getPaymentId());
                context.setFraudAlertCreated(false);
            }
            return true;
        }
    }
    
    /**
     * Step 4: Perform 3D Secure authentication if required.
     */
    static class ThreeDSecureStep extends AbstractSagaStep<PaymentSagaContext> {
        
        ThreeDSecureStep() {
            super("ThreeDSecure");
        }
        
        @Override
        protected boolean doExecute(PaymentSagaContext context) {
            Payment payment = context.getPayment();
            
            if (!context.isThreeDsRequired()) {
                // 3DS not required, skip
                payment.setThreeDsStatus(ThreeDSStatus.NOT_ENROLLED);
                logger.info("3DS not required for payment: {}", context.getPaymentId());
                return true;
            }
            
            // Simulate 3DS authentication (in real impl, call 3DS service via gRPC)
            context.setThreeDsSessionCreated(true);
            context.setThreeDsAuthenticated(true);
            context.setThreeDsCavv("CAVV_" + UUID.randomUUID().toString().substring(0, 8));
            context.setThreeDsEci("05");
            
            payment.setThreeDsStatus(ThreeDSStatus.AUTHENTICATED);
            payment.setThreeDsCavv(context.getThreeDsCavv());
            payment.setThreeDsEci(context.getThreeDsEci());
            
            logger.info("3DS authentication completed for payment: {}", context.getPaymentId());
            return true;
        }
        
        @Override
        protected boolean doCompensate(PaymentSagaContext context) {
            // In real implementation, would invalidate 3DS session
            if (context.isThreeDsSessionCreated()) {
                logger.info("Compensating 3DS for payment: {} (session would be invalidated)", 
                           context.getPaymentId());
                context.setThreeDsSessionCreated(false);
            }
            return true;
        }
    }
    
    /**
     * Step 5: Authorize with PSP.
     */
    static class PSPAuthorizationStep extends AbstractSagaStep<PaymentSagaContext> {
        private final PSPRoutingService pspRoutingService;
        
        PSPAuthorizationStep(PSPRoutingService pspRoutingService) {
            super("PSPAuthorization");
            this.pspRoutingService = pspRoutingService;
        }
        
        @Override
        protected boolean doExecute(PaymentSagaContext context) {
            Payment payment = context.getPayment();
            
            // Build PSP request
            PSPAuthorizationRequest pspRequest = new PSPAuthorizationRequest();
            pspRequest.setMerchantId(context.getMerchantId());
            pspRequest.setAmount(payment.getAmount());
            pspRequest.setCurrency(payment.getCurrency());
            pspRequest.setCardTokenId(context.getCardTokenId());
            pspRequest.setCardLastFour(context.getCardLastFour());
            pspRequest.setCardBrand(payment.getCardBrand() != null ? payment.getCardBrand().name() : null);
            
            if (context.getThreeDsCavv() != null) {
                pspRequest.setCavv(context.getThreeDsCavv());
                pspRequest.setEci(context.getThreeDsEci());
            }
            
            // Call PSP
            PSPAuthorizationResponse pspResponse = pspRoutingService.authorizeWithFailover(pspRequest);
            
            if (pspResponse.isSuccess()) {
                context.setPspTransactionId(pspResponse.getPspTransactionId());
                context.setPspAuthorized(true);
                context.setPspAuthorizationCreated(true);
                
                payment.setPspTransactionId(pspResponse.getPspTransactionId());
                payment.setStatus(PaymentStatus.AUTHORIZED);
                payment.setAuthorizedAt(Instant.now());
                
                logger.info("PSP authorization successful for payment: {}", context.getPaymentId());
                return true;
            } else {
                context.setFailureReason("PSP declined: " + pspResponse.getDeclineMessage());
                throw new SagaStepException("PSPAuthorization", 
                    "PSP authorization failed: " + pspResponse.getDeclineMessage());
            }
        }
        
        @Override
        protected boolean doCompensate(PaymentSagaContext context) {
            // Void the PSP authorization if it was created
            if (context.isPspAuthorizationCreated() && context.getPspTransactionId() != null) {
                try {
                    PSPClient pspClient = pspRoutingService.selectPSP(context.getMerchantId());
                    PSPVoidResponse voidResponse = pspClient.voidTransaction(context.getPspTransactionId());
                    
                    if (voidResponse.isSuccess()) {
                        logger.info("Voided PSP authorization for payment: {}", context.getPaymentId());
                        context.setPspAuthorizationCreated(false);
                        return true;
                    } else {
                        logger.error("Failed to void PSP authorization for payment: {}", 
                                    context.getPaymentId());
                        return false;
                    }
                } catch (Exception e) {
                    logger.error("Exception voiding PSP authorization for payment: {}: {}", 
                                context.getPaymentId(), e.getMessage());
                    return false;
                }
            }
            return true;
        }
    }
    
    /**
     * Step 6: Finalize the payment and publish events.
     */
    static class FinalizePaymentStep extends AbstractSagaStep<PaymentSagaContext> {
        private final PaymentRepository paymentRepository;
        private final PaymentEventRepository paymentEventRepository;
        private final PaymentEventPublisher eventPublisher;
        
        FinalizePaymentStep(PaymentRepository paymentRepository,
                          PaymentEventRepository paymentEventRepository,
                          PaymentEventPublisher eventPublisher) {
            super("FinalizePayment");
            this.paymentRepository = paymentRepository;
            this.paymentEventRepository = paymentEventRepository;
            this.eventPublisher = eventPublisher;
        }
        
        @Override
        protected boolean doExecute(PaymentSagaContext context) {
            Payment payment = context.getPayment();
            
            // Save final payment state
            payment = paymentRepository.save(payment);
            context.setPayment(payment);
            context.setFinalStatus(payment.getStatus());
            
            // Create success event
            PaymentEvent event = new PaymentEvent(payment.getId(), "AUTHORIZATION", "SUCCESS");
            event.setAmount(payment.getAmount());
            event.setCurrency(payment.getCurrency());
            paymentEventRepository.save(event);
            
            // Publish to Kafka
            eventPublisher.publishPaymentEvent(payment, PaymentEventType.PAYMENT_AUTHORIZED);
            
            logger.info("Finalized payment: {} with status {}", 
                       context.getPaymentId(), payment.getStatus());
            return true;
        }
        
        @Override
        protected boolean doCompensate(PaymentSagaContext context) {
            // The finalize step compensation is handled by earlier steps
            // Just log that we're rolling back the finalization
            logger.info("Compensating finalization for payment: {}", context.getPaymentId());
            return true;
        }
    }
}
