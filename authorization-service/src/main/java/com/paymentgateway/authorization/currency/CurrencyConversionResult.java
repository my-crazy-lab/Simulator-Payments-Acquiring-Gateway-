package com.paymentgateway.authorization.currency;

import java.math.BigDecimal;

/**
 * Result of a currency conversion operation
 */
public class CurrencyConversionResult {
    
    private final BigDecimal originalAmount;
    private final String originalCurrency;
    private final BigDecimal convertedAmount;
    private final String convertedCurrency;
    private final BigDecimal exchangeRate;
    
    public CurrencyConversionResult(BigDecimal originalAmount, String originalCurrency,
                                   BigDecimal convertedAmount, String convertedCurrency,
                                   BigDecimal exchangeRate) {
        this.originalAmount = originalAmount;
        this.originalCurrency = originalCurrency;
        this.convertedAmount = convertedAmount;
        this.convertedCurrency = convertedCurrency;
        this.exchangeRate = exchangeRate;
    }
    
    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }
    
    public String getOriginalCurrency() {
        return originalCurrency;
    }
    
    public BigDecimal getConvertedAmount() {
        return convertedAmount;
    }
    
    public String getConvertedCurrency() {
        return convertedCurrency;
    }
    
    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }
}
