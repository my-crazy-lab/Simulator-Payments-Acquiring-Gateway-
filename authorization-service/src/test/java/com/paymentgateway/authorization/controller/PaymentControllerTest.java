package com.paymentgateway.authorization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.authorization.domain.PaymentStatus;
import com.paymentgateway.authorization.dto.PaymentRequest;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private PaymentService paymentService;
    
    @Test
    void shouldCreatePaymentWithValidRequest() throws Exception {
        // Arrange
        PaymentRequest request = new PaymentRequest(
            "4532015112830366",
            12,
            2025,
            "123",
            new BigDecimal("100.00"),
            "USD"
        );
        
        PaymentResponse response = new PaymentResponse(
            "pay_abc123",
            PaymentStatus.AUTHORIZED,
            new BigDecimal("100.00"),
            "USD"
        );
        response.setCardLastFour("0366");
        response.setCardBrand("VISA");
        response.setCreatedAt(Instant.now());
        response.setAuthorizedAt(Instant.now());
        
        when(paymentService.processPayment(any(PaymentRequest.class), any(UUID.class)))
            .thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Merchant-Id", UUID.randomUUID().toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentId").value("pay_abc123"))
            .andExpect(jsonPath("$.status").value("AUTHORIZED"))
            .andExpect(jsonPath("$.amount").value(100.00))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.cardLastFour").value("0366"));
    }
    
    @Test
    void shouldRejectPaymentWithMissingCardNumber() throws Exception {
        // Arrange
        PaymentRequest request = new PaymentRequest(
            null, // Missing card number
            12,
            2025,
            "123",
            new BigDecimal("100.00"),
            "USD"
        );
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldRejectPaymentWithInvalidCardNumber() throws Exception {
        // Arrange
        PaymentRequest request = new PaymentRequest(
            "123", // Invalid card number
            12,
            2025,
            "123",
            new BigDecimal("100.00"),
            "USD"
        );
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldRejectPaymentWithInvalidAmount() throws Exception {
        // Arrange
        PaymentRequest request = new PaymentRequest(
            "4532015112830366",
            12,
            2025,
            "123",
            new BigDecimal("-10.00"), // Negative amount
            "USD"
        );
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldRejectPaymentWithInvalidCurrency() throws Exception {
        // Arrange
        PaymentRequest request = new PaymentRequest(
            "4532015112830366",
            12,
            2025,
            "123",
            new BigDecimal("100.00"),
            "INVALID" // Invalid currency code
        );
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldGetPaymentById() throws Exception {
        // Arrange
        String paymentId = "pay_abc123";
        PaymentResponse response = new PaymentResponse(
            paymentId,
            PaymentStatus.AUTHORIZED,
            new BigDecimal("100.00"),
            "USD"
        );
        response.setCardLastFour("0366");
        response.setCardBrand("VISA");
        
        when(paymentService.getPayment(eq(paymentId))).thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(paymentId))
            .andExpect(jsonPath("$.status").value("AUTHORIZED"))
            .andExpect(jsonPath("$.amount").value(100.00));
    }
    
    @Test
    void shouldReturnNotFoundForNonExistentPayment() throws Exception {
        // Arrange
        String paymentId = "pay_nonexistent";
        when(paymentService.getPayment(eq(paymentId)))
            .thenThrow(new RuntimeException("Payment not found: " + paymentId));
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
            .andExpect(status().is5xxServerError());
    }
    
    @Test
    void shouldCaptureAuthorizedPayment() throws Exception {
        // Arrange
        String paymentId = "pay_abc123";
        PaymentResponse response = new PaymentResponse(
            paymentId,
            PaymentStatus.CAPTURED,
            new BigDecimal("100.00"),
            "USD"
        );
        
        when(paymentService.capturePayment(eq(paymentId))).thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/payments/{id}/capture", paymentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(paymentId))
            .andExpect(jsonPath("$.status").value("CAPTURED"));
    }
    
    @Test
    void shouldVoidAuthorizedPayment() throws Exception {
        // Arrange
        String paymentId = "pay_abc123";
        PaymentResponse response = new PaymentResponse(
            paymentId,
            PaymentStatus.CANCELLED,
            new BigDecimal("100.00"),
            "USD"
        );
        
        when(paymentService.voidPayment(eq(paymentId))).thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/payments/{id}/void", paymentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(paymentId))
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
