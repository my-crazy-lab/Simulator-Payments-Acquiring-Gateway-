package com.paymentgateway.authorization.currency;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Interface for exchange rate providers
 */
public interface ExchangeRateProvider {
    
    /**
     * Get the current exchange rate from one currency to another
     * 
     * @param fromCurrency Source currency code (e.g., "USD")
     * @param toCurrency Target currency code (e.g., "EUR")
     * @return Exchange rate if available
     */
    Optional<ExchangeRate> getExchangeRate(String fromCurrency, String toCurrency);
    
    /**
     * Get the provider name
     */
    String getProviderName();
}
