package com.paymentgateway.authorization.controller;

import com.paymentgateway.authorization.dto.PaymentRequest;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.service.PaymentService;
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
    private final Counter paymentCounter;
    private final Timer paymentTimer;
    
    public PaymentController(PaymentService paymentService, MeterRegistry meterRegistry) {
        this.paymentService = paymentService;
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
            @RequestAttribute("merchant") com.paymentgateway.authorization.domain.Merchant merchant) {
        
        return paymentTimer.record(() -> {
            PaymentResponse response = paymentService.processPayment(request, merchant.getId());
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
}
