package com.paymentgateway.authorization.security;

import com.paymentgateway.authorization.domain.Merchant;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public RateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Check if merchant has exceeded rate limit
     * Uses token bucket algorithm with Redis
     */
    public boolean isRateLimitExceeded(Merchant merchant) {
        String key = "rate_limit:" + merchant.getMerchantId();
        Integer limit = merchant.getRateLimitPerSecond();
        
        // Get current count
        String countStr = redisTemplate.opsForValue().get(key);
        long count = countStr != null ? Long.parseLong(countStr) : 0;
        
        if (count >= limit) {
            return true;
        }
        
        // Increment counter
        Long newCount = redisTemplate.opsForValue().increment(key);
        
        // Set expiry on first request in the window
        if (newCount == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(1));
        }
        
        return false;
    }
    
    /**
     * Get remaining requests for merchant
     */
    public long getRemainingRequests(Merchant merchant) {
        String key = "rate_limit:" + merchant.getMerchantId();
        String countStr = redisTemplate.opsForValue().get(key);
        long count = countStr != null ? Long.parseLong(countStr) : 0;
        
        return Math.max(0, merchant.getRateLimitPerSecond() - count);
    }
    
    /**
     * Get time until rate limit resets (in seconds)
     */
    public long getResetTime(Merchant merchant) {
        String key = "rate_limit:" + merchant.getMerchantId();
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
    
    /**
     * Track invalid authentication attempts for rate limiting
     */
    public void recordInvalidAuthAttempt(String identifier) {
        String key = "auth_attempts:" + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count == 1) {
            // Set expiry of 15 minutes on first attempt
            redisTemplate.expire(key, Duration.ofMinutes(15));
        }
    }
    
    /**
     * Check if too many invalid auth attempts
     */
    public boolean isTooManyInvalidAuthAttempts(String identifier) {
        String key = "auth_attempts:" + identifier;
        String countStr = redisTemplate.opsForValue().get(key);
        long count = countStr != null ? Long.parseLong(countStr) : 0;
        
        return count >= 10; // Max 10 attempts per 15 minutes
    }
}
