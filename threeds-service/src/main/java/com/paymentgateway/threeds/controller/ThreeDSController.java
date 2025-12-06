package com.paymentgateway.threeds.controller;

import com.paymentgateway.threeds.domain.ThreeDSTransaction;
import com.paymentgateway.threeds.service.ThreeDSService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for browser redirect flows
 */
@RestController
@RequestMapping("/api/v1/3ds")
public class ThreeDSController {

    private final ThreeDSService threeDSService;

    public ThreeDSController(ThreeDSService threeDSService) {
        this.threeDSService = threeDSService;
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestParam String transactionId,
            @RequestParam String pares) {
        
        ThreeDSTransaction transaction = threeDSService.completeAuthentication(transactionId, pares);
        
        return ResponseEntity.ok(Map.of(
            "transactionId", transaction.getTransactionId(),
            "status", transaction.getStatus().toString(),
            "authenticated", transaction.getStatus().toString().equals("AUTHENTICATED"),
            "merchantReturnUrl", transaction.getMerchantReturnUrl()
        ));
    }

    @GetMapping("/status/{transactionId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String transactionId) {
        ThreeDSTransaction transaction = threeDSService.getTransaction(transactionId);
        
        if (transaction == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(Map.of(
            "transactionId", transaction.getTransactionId(),
            "status", transaction.getStatus().toString(),
            "expired", transaction.isExpired()
        ));
    }
}
