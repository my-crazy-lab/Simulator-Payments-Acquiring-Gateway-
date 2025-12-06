package com.paymentgateway.authorization.currency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;

/**
 * Service for currency conversion with Redis caching
 * Implements Requirements 23.1, 23.2, 23.3, 23.4, 23.5
 */
@Service
public class CurrencyConversionService {
    
    private static final Logger logger = LoggerFactory.getLogger(CurrencyConversionService.class);
    private static final String RATE_CACHE_PREFIX = "exchange_rate:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final int SCALE = 2; // Decimal places for currency amounts
    
    private final ExchangeRateProvider exchangeRateProvider;
    private final RedisTemplate<String, String> redisTemplate;
    
    public CurrencyConversionService(ExchangeRateProvider exchangeRateProvider,
                                    RedisTemplate<String, String> redisTemplate) {
        this.exchangeRateProvider = exchangeRateProvider;
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Convert an amount from one currency to another
     * Requirement 23.1: Apply current exchange rates
     * Requirement 23.4: Cache rates in Redis with TTL
     * 
     * @param amount Amount to convert
     * @param fromCurrency Source currency
     * @param toCurrency Target currency
     * @return Conversion result with original and converted amounts
     */
    public CurrencyConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null || fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("Amount and currencies must not be null");
        }
        
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        
        // Same currency - no conversion needed
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return new CurrencyConversionResult(
                amount, fromCurrency.toUpperCase(),
                amount, toCurrency.toUpperCase(),
                BigDecimal.ONE
            );
        }
        
        // Get exchange rate (with caching)
        ExchangeRate exchangeRate = getExchangeRateWithCache(fromCurrency, toCurrency);
        
        // Perform conversion
        BigDecimal convertedAmount = amount.multiply(exchangeRate.getRate())
            .setScale(SCALE, RoundingMode.HALF_UP);
        
        logger.debug("Converted {} {} to {} {} using rate {}",
            amount, fromCurrency, convertedAmount, toCurrency, exchangeRate.getRate());
        
        return new CurrencyConversionResult(
            amount, fromCurrency.toUpperCase(),
            convertedAmount, toCurrency.toUpperCase(),
            exchangeRate.getRate()
        );
    }
    
    /**
     * Get exchange rate with Redis caching
     * Requirement 23.4: Cache rates in Redis with appropriate TTL
     * Requirement 23.5: Use last known rate if provider unavailable
     */
    private ExchangeRate getExchangeRateWithCache(String fromCurrency, String toCurrency) {
        String cacheKey = RATE_CACHE_PREFIX + fromCurrency.toUpperCase() + "_" + toCurrency.toUpperCase();
        
        // Try to get from cache
        String cachedRate = redisTemplate.opsForValue().get(cacheKey);
        if (cachedRate != null) {
            try {
                BigDecimal rate = new BigDecimal(cachedRate);
                logger.debug("Using cached exchange rate for {}->{}: {}", fromCurrency, toCurrency, rate);
                return new ExchangeRate(
                    fromCurrency.toUpperCase(),
                    toCurrency.toUpperCase(),
                    rate,
                    java.time.Instant.now(),
                    "cache"
                );
            } catch (NumberFormatException e) {
                logger.warn("Invalid cached rate for {}, fetching fresh rate", cacheKey);
            }
        }
        
        // Fetch from provider
        Optional<ExchangeRate> rateOpt = exchangeRateProvider.getExchangeRate(fromCurrency, toCurrency);
        
        if (rateOpt.isPresent()) {
            ExchangeRate rate = rateOpt.get();
            // Cache the rate
            redisTemplate.opsForValue().set(cacheKey, rate.getRate().toString(), CACHE_TTL);
            logger.info("Fetched and cached exchange rate for {}->{}: {}", 
                fromCurrency, toCurrency, rate.getRate());
            return rate;
        }
        
        // Requirement 23.5: If provider unavailable and no cache, throw exception
        // In production, might want to use a fallback rate or flag for manual review
        throw new CurrencyConversionException(
            String.format("Exchange rate not available for %s to %s", fromCurrency, toCurrency)
        );
    }
    
    /**
     * Get the current exchange rate between two currencies
     * 
     * @param fromCurrency Source currency
     * @param toCurrency Target currency
     * @return Exchange rate
     */
    public ExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        return getExchangeRateWithCache(fromCurrency, toCurrency);
    }
    
    /**
     * Clear cached exchange rate
     * 
     * @param fromCurrency Source currency
     * @param toCurrency Target currency
     */
    public void clearCachedRate(String fromCurrency, String toCurrency) {
        String cacheKey = RATE_CACHE_PREFIX + fromCurrency.toUpperCase() + "_" + toCurrency.toUpperCase();
        redisTemplate.delete(cacheKey);
        logger.info("Cleared cached rate for {}->{}", fromCurrency, toCurrency);
    }
}
