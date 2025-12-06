package com.paymentgateway.authorization.service;

import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.PaymentRequest;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.idempotency.IdempotencyService;
import com.paymentgateway.authorization.psp.*;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import com.paymentgateway.authorization.repository.PaymentRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PSPRoutingService pspRoutingService;
    private final Tracer tracer;
    private final IdempotencyService idempotencyService;
    
    public PaymentService(PaymentRepository paymentRepository,
                         PaymentEventRepository paymentEventRepository,
                         PSPRoutingService pspRoutingService,
                         Tracer tracer,
                         IdempotencyService idempotencyService) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.pspRoutingService = pspRoutingService;
        this.tracer = tracer;
        this.idempotencyService = idempotencyService;
    }
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, UUID merchantId, String idempotencyKey) {
        // Check for existing result if idempotency key is provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            PaymentResponse existingResult = idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class);
            if (existingResult != null) {
                logger.info("Returning cached payment result for idempotency key: {}", idempotencyKey);
                return existingResult;
            }
            
            // Acquire distributed lock to prevent concurrent processing
            if (!idempotencyService.acquireLock(idempotencyKey)) {
                // Check again for result (another thread may have completed while we waited)
                existingResult = idempotencyService.getExistingResult(idempotencyKey, PaymentResponse.class);
                if (existingResult != null) {
                    logger.info("Returning cached payment result after lock wait for idempotency key: {}", idempotencyKey);
                    return existingResult;
                }
                throw new RuntimeException("Failed to acquire lock for idempotency key: " + idempotencyKey);
            }
            
            try {
                PaymentResponse response = processPaymentInternal(request, merchantId);
                // Store result atomically with idempotency key
                idempotencyService.storeResult(idempotencyKey, response);
                return response;
            } finally {
                idempotencyService.releaseLock(idempotencyKey);
            }
        }
        
        // No idempotency key provided, process normally
        return processPaymentInternal(request, merchantId);
    }
    
    @Transactional
    private PaymentResponse processPaymentInternal(PaymentRequest request, UUID merchantId) {
        long startTime = System.currentTimeMillis();
        
        // Create distributed trace span
        Span span = tracer.spanBuilder("processPayment").startSpan();
        try (var scope = span.makeCurrent()) {
            
            // Generate payment ID
            String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            
            // Create payment entity
            Payment payment = new Payment();
            payment.setPaymentId(paymentId);
            payment.setMerchantId(merchantId);
            payment.setAmount(request.getAmount());
            payment.setCurrency(request.getCurrency());
            payment.setDescription(request.getDescription());
            payment.setReferenceId(request.getReferenceId());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setBillingStreet(request.getBillingStreet());
            payment.setBillingCity(request.getBillingCity());
            payment.setBillingState(request.getBillingState());
            payment.setBillingZip(request.getBillingZip());
            payment.setBillingCountry(request.getBillingCountry());
            
            // Step 1: Tokenization (simulated - would call tokenization service via gRPC)
            span.addEvent("tokenization_start");
            UUID tokenId = simulateTokenization(request.getCardNumber());
            payment.setCardTokenId(tokenId);
            payment.setCardLastFour(request.getCardNumber().substring(request.getCardNumber().length() - 4));
            payment.setCardBrand(CardBrand.VISA); // Simplified
            span.addEvent("tokenization_complete");
            
            // Step 2: Fraud Detection (simulated - would call fraud detection service via gRPC)
            span.addEvent("fraud_detection_start");
            payment.setFraudScore(java.math.BigDecimal.valueOf(0.15));
            payment.setFraudStatus(FraudStatus.CLEAN);
            span.addEvent("fraud_detection_complete");
            
            // Step 3: 3D Secure (simulated - would call 3DS service via gRPC if needed)
            span.addEvent("3ds_check_start");
            payment.setThreeDsStatus(ThreeDSStatus.NOT_ENROLLED);
            span.addEvent("3ds_check_complete");
            
            // Step 4: PSP Authorization (using PSP routing service)
            span.addEvent("psp_authorization_start");
            PSPAuthorizationRequest pspRequest = buildPSPAuthorizationRequest(payment, request);
            PSPAuthorizationResponse pspResponse = pspRoutingService.authorizeWithFailover(pspRequest);
            
            if (pspResponse.isSuccess()) {
                payment.setStatus(PaymentStatus.AUTHORIZED);
                payment.setAuthorizedAt(Instant.now());
                payment.setPspTransactionId(pspResponse.getPspTransactionId());
                span.addEvent("psp_authorization_complete");
            } else {
                payment.setStatus(PaymentStatus.DECLINED);
                span.addEvent("psp_authorization_declined");
                logger.warn("Payment declined: paymentId={}, reason={}", 
                           paymentId, pspResponse.getDeclineMessage());
            }
            
            // Calculate processing time
            long processingTime = System.currentTimeMillis() - startTime;
            payment.setProcessingTimeMs((int) processingTime);
            
            // Save payment
            payment = paymentRepository.save(payment);
            
            // Create payment event
            PaymentEvent event = new PaymentEvent(payment.getId(), "AUTHORIZATION", "SUCCESS");
            event.setAmount(payment.getAmount());
            event.setCurrency(payment.getCurrency());
            event.setProcessingTimeMs((int) processingTime);
            paymentEventRepository.save(event);
            
            logger.info("Payment processed successfully: paymentId={}, status={}, processingTime={}ms",
                       paymentId, payment.getStatus(), processingTime);
            
            // Build response
            PaymentResponse response = new PaymentResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setStatus(payment.getStatus());
            response.setAmount(payment.getAmount());
            response.setCurrency(payment.getCurrency());
            response.setCardLastFour(payment.getCardLastFour());
            response.setCardBrand(payment.getCardBrand().name());
            response.setCreatedAt(payment.getCreatedAt());
            response.setAuthorizedAt(payment.getAuthorizedAt());
            
            return response;
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Payment processing failed", e);
            throw new RuntimeException("Payment processing failed: " + e.getMessage(), e);
        } finally {
            span.end();
        }
    }
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, UUID merchantId) {
        return processPayment(request, merchantId, null);
    }
    
    public PaymentResponse getPayment(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setCardLastFour(payment.getCardLastFour());
        response.setCardBrand(payment.getCardBrand() != null ? payment.getCardBrand().name() : null);
        response.setCreatedAt(payment.getCreatedAt());
        response.setAuthorizedAt(payment.getAuthorizedAt());
        
        return response;
    }
    
    @Transactional
    public PaymentResponse capturePayment(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new RuntimeException("Payment must be in AUTHORIZED status to capture");
        }
        
        // Call PSP to capture
        PSPClient pspClient = pspRoutingService.selectPSP(payment.getMerchantId());
        PSPCaptureResponse pspResponse = pspClient.capture(
            payment.getPspTransactionId(), 
            payment.getAmount(), 
            payment.getCurrency()
        );
        
        if (!pspResponse.isSuccess()) {
            throw new RuntimeException("PSP capture failed: " + pspResponse.getErrorMessage());
        }
        
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());
        payment = paymentRepository.save(payment);
        
        // Create payment event
        PaymentEvent event = new PaymentEvent(payment.getId(), "CAPTURE", "SUCCESS");
        event.setAmount(payment.getAmount());
        event.setCurrency(payment.getCurrency());
        paymentEventRepository.save(event);
        
        logger.info("Payment captured: paymentId={}", paymentId);
        
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        
        return response;
    }
    
    @Transactional
    public PaymentResponse voidPayment(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new RuntimeException("Payment must be in AUTHORIZED status to void");
        }
        
        // Call PSP to void
        PSPClient pspClient = pspRoutingService.selectPSP(payment.getMerchantId());
        PSPVoidResponse pspResponse = pspClient.voidTransaction(payment.getPspTransactionId());
        
        if (!pspResponse.isSuccess()) {
            throw new RuntimeException("PSP void failed: " + pspResponse.getErrorMessage());
        }
        
        payment.setStatus(PaymentStatus.CANCELLED);
        payment = paymentRepository.save(payment);
        
        // Create payment event
        PaymentEvent event = new PaymentEvent(payment.getId(), "VOID", "SUCCESS");
        event.setAmount(payment.getAmount());
        event.setCurrency(payment.getCurrency());
        paymentEventRepository.save(event);
        
        logger.info("Payment voided: paymentId={}", paymentId);
        
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        
        return response;
    }
    
    // Simulated tokenization - in real implementation, this would call the tokenization service
    private UUID simulateTokenization(String cardNumber) {
        return UUID.randomUUID();
    }
    
    private PSPAuthorizationRequest buildPSPAuthorizationRequest(Payment payment, PaymentRequest request) {
        PSPAuthorizationRequest pspRequest = new PSPAuthorizationRequest();
        pspRequest.setMerchantId(payment.getMerchantId());
        pspRequest.setAmount(payment.getAmount());
        pspRequest.setCurrency(payment.getCurrency());
        pspRequest.setCardTokenId(payment.getCardTokenId());
        pspRequest.setCardLastFour(payment.getCardLastFour());
        pspRequest.setCardBrand(payment.getCardBrand() != null ? payment.getCardBrand().name() : null);
        pspRequest.setDescription(payment.getDescription());
        pspRequest.setReferenceId(payment.getReferenceId());
        pspRequest.setBillingStreet(payment.getBillingStreet());
        pspRequest.setBillingCity(payment.getBillingCity());
        pspRequest.setBillingState(payment.getBillingState());
        pspRequest.setBillingZip(payment.getBillingZip());
        pspRequest.setBillingCountry(payment.getBillingCountry());
        
        // Add 3DS data if available
        if (payment.getThreeDsCavv() != null) {
            pspRequest.setCavv(payment.getThreeDsCavv());
            pspRequest.setEci(payment.getThreeDsEci());
        }
        
        return pspRequest;
    }
}
