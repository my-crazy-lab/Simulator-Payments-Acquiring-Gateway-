package com.paymentgateway.authorization.service;

import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.PaymentRequest;
import com.paymentgateway.authorization.dto.PaymentResponse;
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
    private final Tracer tracer;
    
    public PaymentService(PaymentRepository paymentRepository,
                         PaymentEventRepository paymentEventRepository,
                         Tracer tracer) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.tracer = tracer;
    }
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, UUID merchantId) {
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
            
            // Step 4: PSP Authorization (simulated)
            span.addEvent("psp_authorization_start");
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setAuthorizedAt(Instant.now());
            payment.setPspTransactionId("psp_" + UUID.randomUUID().toString().substring(0, 20));
            span.addEvent("psp_authorization_complete");
            
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
}
