package com.paymentgateway.authorization.controller;

import com.paymentgateway.authorization.domain.Merchant;
import com.paymentgateway.authorization.dto.MerchantDashboardResponse;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.dto.TransactionQueryRequest;
import com.paymentgateway.authorization.dto.TransactionQueryResponse;
import com.paymentgateway.authorization.service.TransactionQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    
    private final TransactionQueryService transactionQueryService;
    
    public TransactionController(TransactionQueryService transactionQueryService) {
        this.transactionQueryService = transactionQueryService;
    }
    
    /**
     * Query transactions with filters and pagination
     * Supports filtering by status, currency, amount range, date range, etc.
     */
    @GetMapping
    public ResponseEntity<TransactionQueryResponse> queryTransactions(
            @RequestAttribute("merchant") Merchant merchant,
            TransactionQueryRequest request) {
        
        TransactionQueryResponse response = transactionQueryService.queryTransactions(
            merchant.getId(), 
            request
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get transaction history for a specific payment
     * Returns the payment and all related transactions (captures, refunds, etc.)
     */
    @GetMapping("/{paymentId}/history")
    public ResponseEntity<List<PaymentResponse>> getTransactionHistory(
            @PathVariable("paymentId") String paymentId) {
        
        List<PaymentResponse> history = transactionQueryService.getTransactionHistory(paymentId);
        return ResponseEntity.ok(history);
    }
    
    /**
     * Get merchant dashboard data
     * Returns aggregated metrics for the merchant
     */
    @GetMapping("/dashboard")
    public ResponseEntity<MerchantDashboardResponse> getMerchantDashboard(
            @RequestAttribute("merchant") Merchant merchant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        
        MerchantDashboardResponse response = transactionQueryService.getMerchantDashboard(
            merchant.getId(),
            startDate,
            endDate
        );
        
        return ResponseEntity.ok(response);
    }
}
