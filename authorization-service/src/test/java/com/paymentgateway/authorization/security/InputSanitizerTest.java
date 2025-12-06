package com.paymentgateway.authorization.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for InputSanitizer.
 * Tests input sanitization including SQL injection detection, XSS prevention,
 * and input validation.
 * 
 * Requirements: 24.2
 */
class InputSanitizerTest {
    
    private InputSanitizer sanitizer;
    
    @BeforeEach
    void setUp() {
        sanitizer = new InputSanitizer();
    }
    
    @Nested
    @DisplayName("SQL Injection Detection Tests")
    class SqlInjectionTests {
        
        @ParameterizedTest
        @ValueSource(strings = {
            "SELECT * FROM users",
            "'; DROP TABLE payments; --",
            "1 OR 1=1",
            "UNION SELECT password FROM users",
            "'; DELETE FROM transactions; --",
            "INSERT INTO merchants VALUES",
            "UPDATE payments SET amount=0",
            "TRUNCATE TABLE audit_logs",
            "EXEC xp_cmdshell",
            "/* comment */ SELECT"
        })
        @DisplayName("Should detect SQL injection patterns")
        void shouldDetectSqlInjection(String maliciousInput) {
            // Act
            boolean containsSql = sanitizer.containsSqlInjection(maliciousInput);
            
            // Assert
            assertThat(containsSql)
                .as("Should detect SQL injection in: %s", maliciousInput)
                .isTrue();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {
            "John Doe",
            "user@example.com",
            "Payment for order #12345",
            "Normal transaction description",
            "123 Main Street, Apt 4B"
        })
        @DisplayName("Should not flag normal input as SQL injection")
        void shouldNotFlagNormalInput(String normalInput) {
            // Act
            boolean containsSql = sanitizer.containsSqlInjection(normalInput);
            
            // Assert
            assertThat(containsSql)
                .as("Should not flag normal input: %s", normalInput)
                .isFalse();
        }
        
        @Test
        @DisplayName("Should handle null input for SQL injection check")
        void shouldHandleNullInput() {
            // Act
            boolean containsSql = sanitizer.containsSqlInjection(null);
            
            // Assert
            assertThat(containsSql).isFalse();
        }
        
        @Test
        @DisplayName("Should handle empty input for SQL injection check")
        void shouldHandleEmptyInput() {
            // Act
            boolean containsSql = sanitizer.containsSqlInjection("");
            
            // Assert
            assertThat(containsSql).isFalse();
        }
    }
    
    @Nested
    @DisplayName("XSS Detection Tests")
    class XssTests {
        
        @ParameterizedTest
        @ValueSource(strings = {
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert('xss')>",
            "javascript:alert('xss')",
            "<iframe src='malicious.com'>",
            "<object data='malicious.swf'>",
            "<embed src='malicious.swf'>",
            "<link rel='stylesheet' href='malicious.css'>",
            "<meta http-equiv='refresh' content='0;url=malicious.com'>",
            "<style>body{background:url('malicious.com')}</style>",
            "vbscript:msgbox('xss')"
        })
        @DisplayName("Should detect XSS patterns")
        void shouldDetectXss(String maliciousInput) {
            // Act
            boolean containsXss = sanitizer.containsXss(maliciousInput);
            
            // Assert
            assertThat(containsXss)
                .as("Should detect XSS in: %s", maliciousInput)
                .isTrue();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {
            "Hello World",
            "user@example.com",
            "Payment description with <amount>",
            "Normal text without scripts",
            "https://example.com/page"
        })
        @DisplayName("Should not flag normal input as XSS")
        void shouldNotFlagNormalInput(String normalInput) {
            // Act
            boolean containsXss = sanitizer.containsXss(normalInput);
            
            // Assert
            assertThat(containsXss)
                .as("Should not flag normal input: %s", normalInput)
                .isFalse();
        }
    }
    
    @Nested
    @DisplayName("Input Sanitization Tests")
    class SanitizationTests {
        
        @Test
        @DisplayName("Should trim whitespace")
        void shouldTrimWhitespace() {
            // Act
            String result = sanitizer.sanitize("  hello world  ");
            
            // Assert
            assertThat(result).isEqualTo("hello world");
        }
        
        @Test
        @DisplayName("Should remove null bytes")
        void shouldRemoveNullBytes() {
            // Act
            String result = sanitizer.sanitize("hello\0world");
            
            // Assert
            assertThat(result).isEqualTo("helloworld");
        }
        
        @Test
        @DisplayName("Should encode HTML entities")
        void shouldEncodeHtmlEntities() {
            // Act
            String result = sanitizer.sanitize("<script>alert('xss')</script>");
            
            // Assert
            assertThat(result).doesNotContain("<script>");
            assertThat(result).contains("&lt;script&gt;");
        }
        
        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNullInput() {
            // Act
            String result = sanitizer.sanitize(null);
            
            // Assert
            assertThat(result).isNull();
        }
    }
    
    @Nested
    @DisplayName("Validate and Sanitize Tests")
    class ValidateAndSanitizeTests {
        
        @Test
        @DisplayName("Should throw exception for SQL injection")
        void shouldThrowExceptionForSqlInjection() {
            // Act & Assert
            assertThatThrownBy(() -> 
                sanitizer.validateAndSanitize("'; DROP TABLE users; --", "description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid input");
        }
        
        @Test
        @DisplayName("Should throw exception for XSS")
        void shouldThrowExceptionForXss() {
            // Act & Assert
            assertThatThrownBy(() -> 
                sanitizer.validateAndSanitize("<script>alert('xss')</script>", "comment"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid input");
        }
        
        @Test
        @DisplayName("Should return sanitized input for valid data")
        void shouldReturnSanitizedInputForValidData() {
            // Act
            String result = sanitizer.validateAndSanitize("  Hello World  ", "name");
            
            // Assert
            assertThat(result).isEqualTo("Hello World");
        }
    }
    
    @Nested
    @DisplayName("Format Validation Tests")
    class FormatValidationTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"MERCHANT_001", "ACME-CORP", "TEST123"})
        @DisplayName("Should validate valid merchant IDs")
        void shouldValidateValidMerchantIds(String merchantId) {
            // Act
            boolean isValid = sanitizer.isValidMerchantId(merchantId);
            
            // Assert
            assertThat(isValid).isTrue();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"merchant@123", "test merchant", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
        @DisplayName("Should reject invalid merchant IDs")
        void shouldRejectInvalidMerchantIds(String merchantId) {
            // Act
            boolean isValid = sanitizer.isValidMerchantId(merchantId);
            
            // Assert
            assertThat(isValid).isFalse();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"pay_123abc", "txn-456-def", "payment_id_789"})
        @DisplayName("Should validate valid payment IDs")
        void shouldValidateValidPaymentIds(String paymentId) {
            // Act
            boolean isValid = sanitizer.isValidPaymentId(paymentId);
            
            // Assert
            assertThat(isValid).isTrue();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"USD", "EUR", "GBP", "JPY"})
        @DisplayName("Should validate valid currency codes")
        void shouldValidateValidCurrencyCodes(String currency) {
            // Act
            boolean isValid = sanitizer.isValidCurrency(currency);
            
            // Assert
            assertThat(isValid).isTrue();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"usd", "US", "USDD", "123"})
        @DisplayName("Should reject invalid currency codes")
        void shouldRejectInvalidCurrencyCodes(String currency) {
            // Act
            boolean isValid = sanitizer.isValidCurrency(currency);
            
            // Assert
            assertThat(isValid).isFalse();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"4111111111111111", "5500000000000004", "340000000000009"})
        @DisplayName("Should validate valid card number formats")
        void shouldValidateValidCardNumberFormats(String cardNumber) {
            // Act
            boolean isValid = sanitizer.isValidCardNumberFormat(cardNumber);
            
            // Assert
            assertThat(isValid).isTrue();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"411111111111", "55000000000000041234567890", "abcd1234efgh5678"})
        @DisplayName("Should reject invalid card number formats")
        void shouldRejectInvalidCardNumberFormats(String cardNumber) {
            // Act
            boolean isValid = sanitizer.isValidCardNumberFormat(cardNumber);
            
            // Assert
            assertThat(isValid).isFalse();
        }
    }
    
    @Nested
    @DisplayName("HTML Encoding Tests")
    class HtmlEncodingTests {
        
        @Test
        @DisplayName("Should encode ampersand")
        void shouldEncodeAmpersand() {
            // Act
            String result = sanitizer.encodeHtmlEntities("Tom & Jerry");
            
            // Assert
            assertThat(result).isEqualTo("Tom &amp; Jerry");
        }
        
        @Test
        @DisplayName("Should encode angle brackets")
        void shouldEncodeAngleBrackets() {
            // Act
            String result = sanitizer.encodeHtmlEntities("<div>content</div>");
            
            // Assert
            assertThat(result).isEqualTo("&lt;div&gt;content&lt;&#x2F;div&gt;");
        }
        
        @Test
        @DisplayName("Should encode quotes")
        void shouldEncodeQuotes() {
            // Act
            String result = sanitizer.encodeHtmlEntities("He said \"hello\" and 'goodbye'");
            
            // Assert
            assertThat(result).contains("&quot;");
            assertThat(result).contains("&#x27;");
        }
    }
    
    @Nested
    @DisplayName("HTML Stripping Tests")
    class HtmlStrippingTests {
        
        @Test
        @DisplayName("Should strip HTML tags")
        void shouldStripHtmlTags() {
            // Act
            String result = sanitizer.stripHtmlTags("<p>Hello <b>World</b></p>");
            
            // Assert
            assertThat(result).isEqualTo("Hello World");
        }
        
        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNullInput() {
            // Act
            String result = sanitizer.stripHtmlTags(null);
            
            // Assert
            assertThat(result).isNull();
        }
    }
}
