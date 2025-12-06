package com.paymentgateway.authorization.currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents an exchange rate between two currencies
 */
public class ExchangeRate {
    
    private final String fromCurrency;
    private final String toCurrency;
    private final BigDecimal rate;
    private final Instant timestamp;
    private final String provider;
    
    public ExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate, 
                       Instant timestamp, String provider) {
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.rate = rate;
        this.timestamp = timestamp;
        this.provider = provider;
    }
    
    public String getFromCurrency() {
        return fromCurrency;
    }
    
    public String getToCurrency() {
        return toCurrency;
    }
    
    public BigDecimal getRate() {
        return rate;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getProvider() {
        return provider;
    }
    
    @Override
    public String toString() {
        return String.format("ExchangeRate{%s->%s: %s, timestamp=%s, provider=%s}",
            fromCurrency, toCurrency, rate, timestamp, provider);
    }
}
