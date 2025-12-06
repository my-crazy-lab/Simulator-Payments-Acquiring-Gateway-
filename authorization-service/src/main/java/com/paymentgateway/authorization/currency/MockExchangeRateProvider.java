package com.paymentgateway.authorization.currency;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mock exchange rate provider for testing and development
 * In production, this would be replaced with a real provider like OpenExchangeRates or ECB
 */
@Component
public class MockExchangeRateProvider implements ExchangeRateProvider {
    
    private static final Map<String, BigDecimal> MOCK_RATES = new HashMap<>();
    
    static {
        // Base rates against USD (using reciprocal rates for consistency)
        MOCK_RATES.put("USD_EUR", new BigDecimal("0.917431")); // 1/1.09
        MOCK_RATES.put("USD_GBP", new BigDecimal("0.787402")); // 1/1.27
        MOCK_RATES.put("USD_JPY", new BigDecimal("149.477"));
        MOCK_RATES.put("USD_CAD", new BigDecimal("1.360544")); // 1/0.735
        MOCK_RATES.put("USD_AUD", new BigDecimal("1.519757")); // 1/0.658
        MOCK_RATES.put("USD_CHF", new BigDecimal("0.880282")); // 1/1.136
        
        // EUR rates
        MOCK_RATES.put("EUR_USD", new BigDecimal("1.09"));
        MOCK_RATES.put("EUR_GBP", new BigDecimal("0.858369")); // 1/1.165
        MOCK_RATES.put("EUR_JPY", new BigDecimal("162.93"));
        
        // GBP rates
        MOCK_RATES.put("GBP_USD", new BigDecimal("1.27"));
        MOCK_RATES.put("GBP_EUR", new BigDecimal("1.165"));
        MOCK_RATES.put("GBP_JPY", new BigDecimal("189.84"));
        
        // JPY rates
        MOCK_RATES.put("JPY_USD", new BigDecimal("0.006689")); // 1/149.477
        MOCK_RATES.put("JPY_EUR", new BigDecimal("0.006138")); // 1/162.93
        MOCK_RATES.put("JPY_GBP", new BigDecimal("0.005268")); // 1/189.84
        
        // CAD rates
        MOCK_RATES.put("CAD_USD", new BigDecimal("0.735"));
        MOCK_RATES.put("CAD_EUR", new BigDecimal("0.674312")); // 0.735/1.09
        
        // AUD rates
        MOCK_RATES.put("AUD_USD", new BigDecimal("0.658"));
        MOCK_RATES.put("AUD_EUR", new BigDecimal("0.603670")); // 0.658/1.09
        
        // CHF rates
        MOCK_RATES.put("CHF_USD", new BigDecimal("1.136"));
        MOCK_RATES.put("CHF_EUR", new BigDecimal("1.042202")); // 1.136/1.09
        
        // Same currency (identity)
        MOCK_RATES.put("USD_USD", BigDecimal.ONE);
        MOCK_RATES.put("EUR_EUR", BigDecimal.ONE);
        MOCK_RATES.put("GBP_GBP", BigDecimal.ONE);
        MOCK_RATES.put("JPY_JPY", BigDecimal.ONE);
        MOCK_RATES.put("CAD_CAD", BigDecimal.ONE);
        MOCK_RATES.put("AUD_AUD", BigDecimal.ONE);
        MOCK_RATES.put("CHF_CHF", BigDecimal.ONE);
    }
    
    @Override
    public Optional<ExchangeRate> getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null) {
            return Optional.empty();
        }
        
        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();
        String key = from + "_" + to;
        
        BigDecimal rate = MOCK_RATES.get(key);
        
        if (rate != null) {
            return Optional.of(new ExchangeRate(
                from, to, rate, Instant.now(), getProviderName()
            ));
        }
        
        // Try to calculate via USD as intermediate currency
        if (!from.equals("USD") && !to.equals("USD")) {
            String fromToUsd = from + "_USD";
            String usdToTarget = "USD_" + to;
            
            BigDecimal fromRate = MOCK_RATES.get(fromToUsd);
            BigDecimal toRate = MOCK_RATES.get(usdToTarget);
            
            if (fromRate != null && toRate != null) {
                BigDecimal calculatedRate = fromRate.multiply(toRate)
                    .setScale(6, java.math.RoundingMode.HALF_UP);
                return Optional.of(new ExchangeRate(
                    from, to, calculatedRate, Instant.now(), getProviderName()
                ));
            }
        }
        
        return Optional.empty();
    }
    
    @Override
    public String getProviderName() {
        return "MockExchangeRateProvider";
    }
}
