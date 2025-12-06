package com.paymentgateway.fraud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class VelocityCheckService {
    
    private static final Logger logger = LoggerFactory.getLogger(VelocityCheckService.class);
    
    private static final int MAX_TRANSACTIONS_PER_CARD_PER_HOUR = 10;
    private static final int MAX_TRANSACTIONS_PER_IP_PER_HOUR = 20;
    private static final int MAX_TRANSACTIONS_PER_MERCHANT_PER_MINUTE = 100;
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public VelocityCheckService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    public boolean checkVelocity(String cardToken, String ipAddress, String merchantId) {
        boolean failed = false;
        
        // Check card velocity
        if (cardToken != null) {
            String cardKey = "velocity:card:" + cardToken;
            Long cardCount = redisTemplate.opsForValue().increment(cardKey);
            if (cardCount == 1) {
                redisTemplate.expire(cardKey, Duration.ofHours(1));
            }
            if (cardCount > MAX_TRANSACTIONS_PER_CARD_PER_HOUR) {
                logger.warn("Card velocity limit exceeded: {} transactions in 1 hour", cardCount);
                failed = true;
            }
        }
        
        // Check IP velocity
        if (ipAddress != null) {
            String ipKey = "velocity:ip:" + ipAddress;
            Long ipCount = redisTemplate.opsForValue().increment(ipKey);
            if (ipCount == 1) {
                redisTemplate.expire(ipKey, Duration.ofHours(1));
            }
            if (ipCount > MAX_TRANSACTIONS_PER_IP_PER_HOUR) {
                logger.warn("IP velocity limit exceeded: {} transactions in 1 hour", ipCount);
                failed = true;
            }
        }
        
        // Check merchant velocity
        if (merchantId != null) {
            String merchantKey = "velocity:merchant:" + merchantId;
            Long merchantCount = redisTemplate.opsForValue().increment(merchantKey);
            if (merchantCount == 1) {
                redisTemplate.expire(merchantKey, Duration.ofMinutes(1));
            }
            if (merchantCount > MAX_TRANSACTIONS_PER_MERCHANT_PER_MINUTE) {
                logger.warn("Merchant velocity limit exceeded: {} transactions in 1 minute", merchantCount);
                failed = true;
            }
        }
        
        return failed;
    }
}
