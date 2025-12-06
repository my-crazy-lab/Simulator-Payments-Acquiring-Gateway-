package com.paymentgateway.authorization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.MerchantDashboardResponse;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.dto.TransactionQueryRequest;
import com.paymentgateway.authorization.dto.TransactionQueryResponse;
import com.paymentgateway.authorization.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TransactionQueryServiceTest {
    
    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private EntityManager entityManager;
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    @Mock
    private CriteriaBuilder criteriaBuilder;
    
    @Mock
    private CriteriaQuery<Payment> criteriaQuery;
    
    @Mock
    private CriteriaQuery<Long> countQuery;
    
    @Mock
    private Root<Payment> root;
    
    @Mock
    private TypedQuery<Payment> typedQuery;
    
    @Mock
    private TypedQuery<Long> countTypedQuery;
    
    private TransactionQueryService transactionQueryService;
    private ObjectMapper objectMapper;
    private UUID merchantId;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        transactionQueryService = new TransactionQueryService(
            paymentRepository,
            entityManager,
            redisTemplate,
            objectMapper
        );
        
        merchantId = UUID.randomUUID();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    
    @Test
    void shouldUseCacheWhenAvailable() throws Exception {
        // Given
        TransactionQueryRequest request = new TransactionQueryRequest();
        request.setPage(0);
        request.setSize(20);
        
        List<PaymentResponse> cachedResponses = new ArrayList<>();
        cachedResponses.add(createPaymentResponse("pay_123", PaymentStatus.AUTHORIZED));
        
        TransactionQueryResponse cachedResult = new TransactionQueryResponse(
            cachedResponses, 0, 20, 1, 1
        );
        
        String cacheKey = "transaction:query:" + merchantId + ":null:null:null:null:null:null:null:null:0:20:createdAt:DESC";
        when(valueOperations.get(cacheKey)).thenReturn(objectMapper.writeValueAsString(cachedResult));
        
        // When
        TransactionQueryResponse response = transactionQueryService.queryTransactions(merchantId, request);
        
        // Then
        assertThat(response.getTransactions()).hasSize(1);
        assertThat(response.getTransactions().get(0).getPaymentId()).isEqualTo("pay_123");
        
        // Verify database was not queried
        verify(entityManager, never()).getCriteriaBuilder();
    }
    
    @Test
    void shouldHandleCacheDeserializationError() {
        // Given
        TransactionQueryRequest request = new TransactionQueryRequest();
        request.setPage(0);
        request.setSize(20);
        
        // Return invalid JSON
        when(valueOperations.get(anyString())).thenReturn("invalid json");
        
        // Mock database query to return empty result
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(Payment.class)).thenReturn(criteriaQuery);
        when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQuery);
        when(criteriaQuery.from(Payment.class)).thenReturn(root);
        when(countQuery.from(Payment.class)).thenReturn(root);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(entityManager.createQuery(countQuery)).thenReturn(countTypedQuery);
        when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(new ArrayList<>());
        when(countTypedQuery.getSingleResult()).thenReturn(0L);
        
        // When
        TransactionQueryResponse response = transactionQueryService.queryTransactions(merchantId, request);
        
        // Then - should fall back to database query
        assertThat(response).isNotNull();
        verify(entityManager, atLeastOnce()).getCriteriaBuilder();
    }
    
    @Test
    void shouldCalculatePaginationCorrectly() {
        // Given - Test pagination calculation
        List<PaymentResponse> responses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            responses.add(createPaymentResponse("pay_" + i, PaymentStatus.AUTHORIZED));
        }
        
        // When
        TransactionQueryResponse response = new TransactionQueryResponse(responses, 1, 10, 25, 3);
        
        // Then
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(25);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isHasPrevious()).isTrue();
        assertThat(response.isHasNext()).isTrue();
    }
    
    @Test
    void shouldIndicateFirstPage() {
        // Given
        List<PaymentResponse> responses = new ArrayList<>();
        responses.add(createPaymentResponse("pay_1", PaymentStatus.AUTHORIZED));
        
        // When
        TransactionQueryResponse response = new TransactionQueryResponse(responses, 0, 10, 25, 3);
        
        // Then
        assertThat(response.isHasPrevious()).isFalse();
        assertThat(response.isHasNext()).isTrue();
    }
    
    @Test
    void shouldIndicateLastPage() {
        // Given
        List<PaymentResponse> responses = new ArrayList<>();
        responses.add(createPaymentResponse("pay_1", PaymentStatus.AUTHORIZED));
        
        // When
        TransactionQueryResponse response = new TransactionQueryResponse(responses, 2, 10, 25, 3);
        
        // Then
        assertThat(response.isHasPrevious()).isTrue();
        assertThat(response.isHasNext()).isFalse();
    }
    
    @Test
    void shouldGetTransactionHistory() {
        // Given
        String paymentId = "pay_123";
        Payment payment = createTestPayment(paymentId, PaymentStatus.CAPTURED);
        
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(payment));
        
        // When
        List<PaymentResponse> history = transactionQueryService.getTransactionHistory(paymentId);
        
        // Then
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPaymentId()).isEqualTo(paymentId);
        assertThat(history.get(0).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }
    
    @Test
    void shouldMaskPANInResponses() {
        // Given
        String paymentId = "pay_123";
        Payment payment = createTestPayment(paymentId, PaymentStatus.AUTHORIZED);
        payment.setCardLastFour("1234");
        
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(payment));
        
        // When
        List<PaymentResponse> history = transactionQueryService.getTransactionHistory(paymentId);
        
        // Then
        assertThat(history.get(0).getCardLastFour()).isEqualTo("1234");
        assertThat(history.get(0).getCardLastFour()).hasSize(4);
    }
    
    // Helper methods
    
    private Payment createTestPayment(String paymentId, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setAmount(BigDecimal.valueOf(100.00));
        payment.setCurrency("USD");
        payment.setStatus(status);
        payment.setCardLastFour("1234");
        payment.setCardBrand(CardBrand.VISA);
        payment.setCreatedAt(Instant.now());
        return payment;
    }
    
    private PaymentResponse createPaymentResponse(String paymentId, PaymentStatus status) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(paymentId);
        response.setStatus(status);
        response.setAmount(BigDecimal.valueOf(100.00));
        response.setCurrency("USD");
        response.setCardLastFour("1234");
        response.setCardBrand("VISA");
        response.setCreatedAt(Instant.now());
        return response;
    }
}
