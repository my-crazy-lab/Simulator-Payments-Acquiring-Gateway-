package com.paymentgateway.authorization.currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CurrencyConversionService
 * Tests rate caching, conversion accuracy, and fallback behavior
 */
class CurrencyConversionServiceTest {
    
    private CurrencyConversionService service;
    private ExchangeRateProvider mockProvider;
    private RedisTemplate<String, String> mockRedisTemplate;
    private ValueOperations<String, String> mockValueOps;
    
    @BeforeEach
    void setUp() {
        mockProvider = mock(ExchangeRateProvider.class);
        
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        mockRedisTemplate = redisTemplate;
        
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        mockValueOps = valueOps;
        
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        
        service = new CurrencyConversionService(mockProvider, mockRedisTemplate);
    }
    
    @Test
    void shouldConvertCurrencySuccessfully() {
        // Arrange
        ExchangeRate rate = new ExchangeRate("USD", "EUR", new BigDecimal("0.92"), 
            java.time.Instant.now(), "test");
        when(mockProvider.getExchangeRate("USD", "EUR")).thenReturn(java.util.Optional.of(rate));
        when(mockValueOps.get(anyString())).thenReturn(null); // Cache miss
        
        // Act
        CurrencyConversionResult result = service.convert(new BigDecimal("100.00"), "USD", "EUR");
        
        // Assert
        assertThat(result.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getOriginalCurrency()).isEqualTo("USD");
        assertThat(result.getConvertedAmount()).isEqualByComparingTo(new BigDecimal("92.00"));
        assertThat(result.getConvertedCurrency()).isEqualTo("EUR");
        assertThat(result.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.92"));
    }
    
    @Test
    void shouldReturnSameAmountForSameCurrency() {
        // Act
        CurrencyConversionResult result = service.convert(new BigDecimal("100.00"), "USD", "USD");
        
        // Assert
        assertThat(result.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getConvertedAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        
        // Should not call provider for same currency
        verify(mockProvider, never()).getExchangeRate(anyString(), anyString());
    }
    
    @Test
    void shouldUseCachedRateWhenAvailable() {
        // Arrange
        when(mockValueOps.get("exchange_rate:USD_EUR")).thenReturn("0.92");
        
        // Act
        CurrencyConversionResult result = service.convert(new BigDecimal("100.00"), "USD", "EUR");
        
        // Assert
        assertThat(result.getConvertedAmount()).isEqualByComparingTo(new BigDecimal("92.00"));
        
        // Should not call provider when cache hit
        verify(mockProvider, never()).getExchangeRate(anyString(), anyString());
    }
    
    @Test
    void shouldCacheRateAfterFetchingFromProvider() {
        // Arrange
        ExchangeRate rate = new ExchangeRate("USD", "EUR", new BigDecimal("0.92"), 
            java.time.Instant.now(), "test");
        when(mockProvider.getExchangeRate("USD", "EUR")).thenReturn(java.util.Optional.of(rate));
        when(mockValueOps.get(anyString())).thenReturn(null); // Cache miss
        
        // Act
        service.convert(new BigDecimal("100.00"), "USD", "EUR");
        
        // Assert
        verify(mockValueOps).set(eq("exchange_rate:USD_EUR"), eq("0.92"), any(Duration.class));
    }
    
    @Test
    void shouldThrowExceptionWhenRateNotAvailable() {
        // Arrange
        when(mockProvider.getExchangeRate("USD", "XYZ")).thenReturn(java.util.Optional.empty());
        when(mockValueOps.get(anyString())).thenReturn(null); // Cache miss
        
        // Act & Assert
        assertThatThrownBy(() -> service.convert(new BigDecimal("100.00"), "USD", "XYZ"))
            .isInstanceOf(CurrencyConversionException.class)
            .hasMessageContaining("Exchange rate not available");
    }
    
    @Test
    void shouldUseCachedRateWhenProviderUnavailable() {
        // Arrange
        when(mockValueOps.get("exchange_rate:USD_EUR")).thenReturn("0.92");
        when(mockProvider.getExchangeRate(anyString(), anyString()))
            .thenReturn(java.util.Optional.empty());
        
        // Act
        CurrencyConversionResult result = service.convert(new BigDecimal("100.00"), "USD", "EUR");
        
        // Assert
        assertThat(result.getConvertedAmount()).isEqualByComparingTo(new BigDecimal("92.00"));
    }
    
    @Test
    void shouldHandleInvalidCachedRate() {
        // Arrange
        when(mockValueOps.get("exchange_rate:USD_EUR")).thenReturn("invalid");
        ExchangeRate rate = new ExchangeRate("USD", "EUR", new BigDecimal("0.92"), 
            java.time.Instant.now(), "test");
        when(mockProvider.getExchangeRate("USD", "EUR")).thenReturn(java.util.Optional.of(rate));
        
        // Act
        CurrencyConversionResult result = service.convert(new BigDecimal("100.00"), "USD", "EUR");
        
        // Assert
        assertThat(result.getConvertedAmount()).isEqualByComparingTo(new BigDecimal("92.00"));
        verify(mockProvider).getExchangeRate("USD", "EUR");
    }
    
    @Test
    void shouldRejectNullAmount() {
        // Act & Assert
        assertThatThrownBy(() -> service.convert(null, "USD", "EUR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null");
    }
    
    @Test
    void shouldRejectNullCurrencies() {
        // Act & Assert
        assertThatThrownBy(() -> service.convert(new BigDecimal("100.00"), null, "EUR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null");
        
        assertThatThrownBy(() -> service.convert(new BigDecimal("100.00"), "USD", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null");
    }
    
    @Test
    void shouldRejectNegativeAmount() {
        // Act & Assert
        assertThatThrownBy(() -> service.convert(new BigDecimal("-100.00"), "USD", "EUR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be non-negative");
    }
    
    @Test
    void shouldHandleZeroAmount() {
        // Arrange
        ExchangeRate rate = new ExchangeRate("USD", "EUR", new BigDecimal("0.92"), 
            java.time.Instant.now(), "test");
        when(mockProvider.getExchangeRate("USD", "EUR")).thenReturn(java.util.Optional.of(rate));
        when(mockValueOps.get(anyString())).thenReturn(null);
        
        // Act
        CurrencyConversionResult result = service.convert(BigDecimal.ZERO, "USD", "EUR");
        
        // Assert
        assertThat(result.getConvertedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
    
    @Test
    void shouldRoundToTwoDecimalPlaces() {
        // Arrange
        ExchangeRate rate = new ExchangeRate("USD", "EUR", new BigDecimal("0.923456"), 
            java.time.Instant.now(), "test");
        when(mockProvider.getExchangeRate("USD", "EUR")).thenReturn(java.util.Optional.of(rate));
        when(mockValueOps.get(anyString())).thenReturn(null);
        
        // Act
        CurrencyConversionResult result = service.convert(new BigDecimal("100.00"), "USD", "EUR");
        
        // Assert
        assertThat(result.getConvertedAmount()).isEqualByComparingTo(new BigDecimal("92.35"));
    }
    
    @Test
    void shouldClearCachedRate() {
        // Act
        service.clearCachedRate("USD", "EUR");
        
        // Assert
        verify(mockRedisTemplate).delete("exchange_rate:USD_EUR");
    }
    
    @Test
    void shouldGetExchangeRate() {
        // Arrange
        ExchangeRate rate = new ExchangeRate("USD", "EUR", new BigDecimal("0.92"), 
            java.time.Instant.now(), "test");
        when(mockProvider.getExchangeRate("USD", "EUR")).thenReturn(java.util.Optional.of(rate));
        when(mockValueOps.get(anyString())).thenReturn(null);
        
        // Act
        ExchangeRate result = service.getExchangeRate("USD", "EUR");
        
        // Assert
        assertThat(result.getRate()).isEqualByComparingTo(new BigDecimal("0.92"));
        assertThat(result.getFromCurrency()).isEqualTo("USD");
        assertThat(result.getToCurrency()).isEqualTo("EUR");
    }
}
