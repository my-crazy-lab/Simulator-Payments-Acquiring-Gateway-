package com.paymentgateway.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.authorization.domain.Payment;
import com.paymentgateway.authorization.domain.PaymentStatus;
import com.paymentgateway.authorization.dto.MerchantDashboardResponse;
import com.paymentgateway.authorization.dto.PaymentResponse;
import com.paymentgateway.authorization.dto.TransactionQueryRequest;
import com.paymentgateway.authorization.dto.TransactionQueryResponse;
import com.paymentgateway.authorization.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionQueryService.class);
    private static final String CACHE_PREFIX = "transaction:query:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public TransactionQueryService(PaymentRepository paymentRepository,
                                  EntityManager entityManager,
                                  RedisTemplate<String, String> redisTemplate,
                                  ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Query transactions with filters and pagination
     * Uses Redis caching to minimize database load
     */
    public TransactionQueryResponse queryTransactions(UUID merchantId, TransactionQueryRequest request) {
        // Generate cache key based on query parameters
        String cacheKey = generateCacheKey(merchantId, request);
        
        // Try to get from cache
        String cachedResult = redisTemplate.opsForValue().get(cacheKey);
        if (cachedResult != null) {
            try {
                logger.debug("Cache hit for transaction query: {}", cacheKey);
                return objectMapper.readValue(cachedResult, TransactionQueryResponse.class);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to deserialize cached result, querying database", e);
            }
        }
        
        // Cache miss - query database
        logger.debug("Cache miss for transaction query: {}", cacheKey);
        TransactionQueryResponse response = queryFromDatabase(merchantId, request);
        
        // Store in cache
        try {
            String jsonResult = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonResult, CACHE_TTL);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to cache query result", e);
        }
        
        return response;
    }
    
    /**
     * Query transactions from database with dynamic filters
     */
    private TransactionQueryResponse queryFromDatabase(UUID merchantId, TransactionQueryRequest request) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Payment> cq = cb.createQuery(Payment.class);
        Root<Payment> payment = cq.from(Payment.class);
        
        // Build predicates for filtering
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(payment.get("merchantId"), merchantId));
        
        if (request.getStatus() != null) {
            predicates.add(cb.equal(payment.get("status"), request.getStatus()));
        }
        
        if (request.getCurrency() != null) {
            predicates.add(cb.equal(payment.get("currency"), request.getCurrency()));
        }
        
        if (request.getMinAmount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(payment.get("amount"), request.getMinAmount()));
        }
        
        if (request.getMaxAmount() != null) {
            predicates.add(cb.lessThanOrEqualTo(payment.get("amount"), request.getMaxAmount()));
        }
        
        if (request.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(payment.get("createdAt"), request.getStartDate()));
        }
        
        if (request.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(payment.get("createdAt"), request.getEndDate()));
        }
        
        if (request.getCardLastFour() != null) {
            predicates.add(cb.equal(payment.get("cardLastFour"), request.getCardLastFour()));
        }
        
        if (request.getReferenceId() != null) {
            predicates.add(cb.equal(payment.get("referenceId"), request.getReferenceId()));
        }
        
        cq.where(predicates.toArray(new Predicate[0]));
        
        // Apply sorting
        if ("DESC".equalsIgnoreCase(request.getSortDirection())) {
            cq.orderBy(cb.desc(payment.get(request.getSortBy())));
        } else {
            cq.orderBy(cb.asc(payment.get(request.getSortBy())));
        }
        
        // Execute query with pagination
        TypedQuery<Payment> query = entityManager.createQuery(cq);
        
        // Get total count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Payment> countRoot = countQuery.from(Payment.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicates.toArray(new Predicate[0]));
        long totalElements = entityManager.createQuery(countQuery).getSingleResult();
        
        // Apply pagination
        int page = request.getPage();
        int size = Math.min(request.getSize(), 100); // Max 100 per page
        query.setFirstResult(page * size);
        query.setMaxResults(size);
        
        List<Payment> payments = query.getResultList();
        
        // Convert to response DTOs with PAN masking
        List<PaymentResponse> responses = payments.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        
        int totalPages = (int) Math.ceil((double) totalElements / size);
        
        return new TransactionQueryResponse(responses, page, size, totalElements, totalPages);
    }
    
    /**
     * Get transaction history for a specific payment
     */
    public List<PaymentResponse> getTransactionHistory(String paymentId) {
        // For now, return single transaction
        // In full implementation, would include related transactions (captures, refunds, etc.)
        Payment payment = paymentRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        
        return Collections.singletonList(convertToResponse(payment));
    }
    
    /**
     * Get merchant dashboard data
     */
    public MerchantDashboardResponse getMerchantDashboard(UUID merchantId, Instant startDate, Instant endDate) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        
        // Build base predicate
        List<Predicate> basePredicates = new ArrayList<>();
        
        // Query for dashboard metrics
        CriteriaQuery<Object[]> metricsQuery = cb.createQuery(Object[].class);
        Root<Payment> payment = metricsQuery.from(Payment.class);
        
        basePredicates.add(cb.equal(payment.get("merchantId"), merchantId));
        if (startDate != null) {
            basePredicates.add(cb.greaterThanOrEqualTo(payment.get("createdAt"), startDate));
        }
        if (endDate != null) {
            basePredicates.add(cb.lessThanOrEqualTo(payment.get("createdAt"), endDate));
        }
        
        // Get total count and volume
        metricsQuery.multiselect(
            cb.count(payment),
            cb.sum(payment.get("amount"))
        );
        metricsQuery.where(basePredicates.toArray(new Predicate[0]));
        
        Object[] metrics = entityManager.createQuery(metricsQuery).getSingleResult();
        long totalTransactions = (Long) metrics[0];
        BigDecimal totalVolume = metrics[1] != null ? (BigDecimal) metrics[1] : BigDecimal.ZERO;
        
        // Get successful transactions count
        CriteriaQuery<Long> successQuery = cb.createQuery(Long.class);
        Root<Payment> successRoot = successQuery.from(Payment.class);
        List<Predicate> successPredicates = new ArrayList<>(basePredicates);
        successPredicates.add(successRoot.get("status").in(
            PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, PaymentStatus.SETTLED
        ));
        successQuery.select(cb.count(successRoot));
        successQuery.where(successPredicates.toArray(new Predicate[0]));
        long successfulTransactions = entityManager.createQuery(successQuery).getSingleResult();
        
        // Get failed transactions count
        CriteriaQuery<Long> failedQuery = cb.createQuery(Long.class);
        Root<Payment> failedRoot = failedQuery.from(Payment.class);
        List<Predicate> failedPredicates = new ArrayList<>(basePredicates);
        failedPredicates.add(failedRoot.get("status").in(
            PaymentStatus.DECLINED, PaymentStatus.FAILED
        ));
        failedQuery.select(cb.count(failedRoot));
        failedQuery.where(failedPredicates.toArray(new Predicate[0]));
        long failedTransactions = entityManager.createQuery(failedQuery).getSingleResult();
        
        // Calculate average
        BigDecimal averageAmount = totalTransactions > 0 
            ? totalVolume.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        // Get transactions by status
        Map<String, Long> transactionsByStatus = getTransactionsByStatus(merchantId, startDate, endDate);
        
        // Get volume by currency
        Map<String, BigDecimal> volumeByCurrency = getVolumeByCurrency(merchantId, startDate, endDate);
        
        MerchantDashboardResponse response = new MerchantDashboardResponse();
        response.setTotalTransactions(totalTransactions);
        response.setSuccessfulTransactions(successfulTransactions);
        response.setFailedTransactions(failedTransactions);
        response.setTotalVolume(totalVolume);
        response.setAverageTransactionAmount(averageAmount);
        response.setTransactionsByStatus(transactionsByStatus);
        response.setVolumeByCurrency(volumeByCurrency);
        
        return response;
    }
    
    private Map<String, Long> getTransactionsByStatus(UUID merchantId, Instant startDate, Instant endDate) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Payment> payment = query.from(Payment.class);
        
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(payment.get("merchantId"), merchantId));
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(payment.get("createdAt"), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(payment.get("createdAt"), endDate));
        }
        
        query.multiselect(payment.get("status"), cb.count(payment));
        query.where(predicates.toArray(new Predicate[0]));
        query.groupBy(payment.get("status"));
        
        List<Object[]> results = entityManager.createQuery(query).getResultList();
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> ((PaymentStatus) row[0]).name(),
                row -> (Long) row[1]
            ));
    }
    
    private Map<String, BigDecimal> getVolumeByCurrency(UUID merchantId, Instant startDate, Instant endDate) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Payment> payment = query.from(Payment.class);
        
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(payment.get("merchantId"), merchantId));
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(payment.get("createdAt"), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(payment.get("createdAt"), endDate));
        }
        
        query.multiselect(payment.get("currency"), cb.sum(payment.get("amount")));
        query.where(predicates.toArray(new Predicate[0]));
        query.groupBy(payment.get("currency"));
        
        List<Object[]> results = entityManager.createQuery(query).getResultList();
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (BigDecimal) row[1]
            ));
    }
    
    /**
     * Convert Payment entity to PaymentResponse with PAN masking
     * This ensures card numbers are always masked in responses
     */
    private PaymentResponse convertToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        
        // PAN masking - only show last 4 digits
        if (payment.getCardLastFour() != null) {
            response.setCardLastFour(payment.getCardLastFour());
        }
        
        response.setCardBrand(payment.getCardBrand() != null ? payment.getCardBrand().name() : null);
        response.setCreatedAt(payment.getCreatedAt());
        response.setAuthorizedAt(payment.getAuthorizedAt());
        
        return response;
    }
    
    /**
     * Generate cache key from query parameters
     */
    private String generateCacheKey(UUID merchantId, TransactionQueryRequest request) {
        return CACHE_PREFIX + merchantId + ":" +
            request.getStatus() + ":" +
            request.getCurrency() + ":" +
            request.getMinAmount() + ":" +
            request.getMaxAmount() + ":" +
            request.getStartDate() + ":" +
            request.getEndDate() + ":" +
            request.getCardLastFour() + ":" +
            request.getReferenceId() + ":" +
            request.getPage() + ":" +
            request.getSize() + ":" +
            request.getSortBy() + ":" +
            request.getSortDirection();
    }
}
