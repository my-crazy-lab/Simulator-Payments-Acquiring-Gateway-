package com.paymentgateway.authorization.controller;

import com.paymentgateway.authorization.dto.PaymentRequest;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.dto.RefundRequest;
import com.paymentgateway.authorization.dto.RefundResponse;
import com.paymentgateway.authorization.service.PaymentService;
import com.paymentgateway.authorization.service.RefundService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final Counter paymentCounter;
    private final Timer paymentTimer;
    
    public PaymentController(PaymentService paymentService, 
                           RefundService refundService,
                           MeterRegistry meterRegistry) {
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.paymentCounter = Counter.builder("payments.processed")
            .description("Total number of payments processed")
            .register(meterRegistry);
        this.paymentTimer = Timer.builder("payments.processing.time")
            .description("Payment processing time")
            .register(meterRegistry);
    }
    
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestAttribute("merchant") com.paymentgateway.authorization.domain.Merchant merchant,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        return paymentTimer.record(() -> {
            PaymentResponse response = paymentService.processPayment(request, merchant.getId(), idempotencyKey);
            paymentCounter.increment();
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        });
    }
    
    @GetMapping("/payments/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable("id") String paymentId) {
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/payments/{id}/capture")
    public ResponseEntity<PaymentResponse> capturePayment(@PathVariable("id") String paymentId) {
        PaymentResponse response = paymentService.capturePayment(paymentId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/payments/{id}/void")
    public ResponseEntity<PaymentResponse> voidPayment(@PathVariable("id") String paymentId) {
        PaymentResponse response = paymentService.voidPayment(paymentId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/refunds")
    public ResponseEntity<RefundResponse> createRefund(
            @Valid @RequestBody RefundRequest request,
            @RequestAttribute("merchant") com.paymentgateway.authorization.domain.Merchant merchant) {
        
        RefundResponse response = refundService.processRefund(request, merchant.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/refunds/{id}")
    public ResponseEntity<RefundResponse> getRefund(@PathVariable("id") String refundId) {
        RefundResponse response = refundService.getRefund(refundId);
        return ResponseEntity.ok(response);
    }
}
