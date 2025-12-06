package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.security.TlsConfiguration;
import com.paymentgateway.authorization.security.TlsConfiguration.TlsValidationResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: payment-acquiring-gateway, Property 34: TLS 1.3 Enforcement
 * 
 * For any external API connection, the TLS version negotiated should be 1.3 
 * or higher - older versions should be rejected.
 * 
 * Validates: Requirements 1.5
 */
public class TLS13EnforcementPropertyTest {
    
    private final TlsConfiguration tlsConfiguration = new TlsConfiguration();
    
    // Set of all deprecated/insecure TLS versions that must be rejected
    private static final Set<String> INSECURE_VERSIONS = Set.of(
        "SSLv2", "SSLv3", "TLSv1", "TLSv1.0", "TLSv1.1", "TLSv1.2",
        "SSL", "TLS", "ssl", "tls"
    );
    
    /**
     * Property: For any TLS version below 1.3, the connection should be rejected.
     * 
     * This tests that all deprecated TLS versions are properly rejected.
     */
    @Property(tries = 100)
    void insecureTlsVersionsShouldBeRejected(
            @ForAll("insecureTlsVersions") String tlsVersion) {
        
        // Act
        TlsValidationResult result = tlsConfiguration.validateConnection(tlsVersion);
        
        // Assert - All insecure versions should be rejected
        assertThat(result.isValid())
            .as("TLS version %s should be rejected", tlsVersion)
            .isFalse();
        
        assertThat(result.getMessage())
            .as("Rejection message should indicate the version is not allowed")
            .containsIgnoringCase("not allowed");
    }
    
    /**
     * Property: TLS 1.3 should always be accepted.
     */
    @Property(tries = 100)
    void tls13ShouldAlwaysBeAccepted() {
        // Arrange
        String tlsVersion = "TLSv1.3";
        
        // Act
        TlsValidationResult result = tlsConfiguration.validateConnection(tlsVersion);
        
        // Assert
        assertThat(result.isValid())
            .as("TLS 1.3 should always be accepted")
            .isTrue();
        
        assertThat(result.getMessage())
            .as("Acceptance message should confirm the version is allowed")
            .containsIgnoringCase("allowed");
    }
    
    /**
     * Property: For any null or empty TLS version, the connection should be rejected.
     */
    @Property(tries = 100)
    void nullOrEmptyTlsVersionShouldBeRejected(
            @ForAll("nullOrEmptyStrings") String tlsVersion) {
        
        // Act
        TlsValidationResult result = tlsConfiguration.validateConnection(tlsVersion);
        
        // Assert
        assertThat(result.isValid())
            .as("Null or empty TLS version should be rejected")
            .isFalse();
    }
    
    /**
     * Property: For any random string that is not a valid TLS version, 
     * the connection should be rejected.
     */
    @Property(tries = 100)
    void invalidTlsVersionStringsShouldBeRejected(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String randomString) {
        
        // Skip if it happens to be TLSv1.3
        Assume.that(!randomString.equals("TLSv1.3"));
        
        // Act
        TlsValidationResult result = tlsConfiguration.validateConnection(randomString);
        
        // Assert
        assertThat(result.isValid())
            .as("Random string '%s' should not be accepted as valid TLS version", randomString)
            .isFalse();
    }
    
    /**
     * Property: The isVersionAllowed method should only return true for TLS 1.3.
     */
    @Property(tries = 100)
    void onlyTls13ShouldBeAllowed(
            @ForAll("allTlsVersions") String tlsVersion) {
        
        // Act
        boolean isAllowed = tlsConfiguration.isVersionAllowed(tlsVersion);
        
        // Assert
        if (tlsVersion.equals("TLSv1.3")) {
            assertThat(isAllowed)
                .as("TLSv1.3 should be allowed")
                .isTrue();
        } else {
            assertThat(isAllowed)
                .as("TLS version %s should not be allowed", tlsVersion)
                .isFalse();
        }
    }
    
    /**
     * Property: The isVersionRejected method should return true for all 
     * deprecated versions.
     */
    @Property(tries = 100)
    void deprecatedVersionsShouldBeRejected(
            @ForAll("deprecatedTlsVersions") String tlsVersion) {
        
        // Act
        boolean isRejected = tlsConfiguration.isVersionRejected(tlsVersion);
        
        // Assert
        assertThat(isRejected)
            .as("Deprecated TLS version %s should be rejected", tlsVersion)
            .isTrue();
    }
    
    /**
     * Property: TLS 1.3 should not be in the rejected list.
     */
    @Property(tries = 100)
    void tls13ShouldNotBeRejected() {
        // Act
        boolean isRejected = tlsConfiguration.isVersionRejected("TLSv1.3");
        
        // Assert
        assertThat(isRejected)
            .as("TLSv1.3 should not be in the rejected list")
            .isFalse();
    }
    
    /**
     * Property: The allowed protocols set should only contain TLS 1.3.
     */
    @Property(tries = 100)
    void allowedProtocolsShouldOnlyContainTls13() {
        // Act
        Set<String> allowedProtocols = tlsConfiguration.getAllowedProtocols();
        
        // Assert
        assertThat(allowedProtocols)
            .as("Allowed protocols should only contain TLSv1.3")
            .containsExactly("TLSv1.3");
    }
    
    /**
     * Property: For any cipher suite, only TLS 1.3 cipher suites should be recommended.
     */
    @Property(tries = 100)
    void onlyTls13CipherSuitesShouldBeRecommended(
            @ForAll("cipherSuites") String cipherSuite) {
        
        // Act
        boolean isRecommended = tlsConfiguration.isRecommendedCipherSuite(cipherSuite);
        
        // Assert - Only TLS 1.3 cipher suites should be recommended
        if (cipherSuite.startsWith("TLS_AES_") || cipherSuite.startsWith("TLS_CHACHA20_")) {
            // These are TLS 1.3 cipher suites
            assertThat(isRecommended)
                .as("TLS 1.3 cipher suite %s should be recommended", cipherSuite)
                .isTrue();
        } else {
            // Legacy cipher suites should not be recommended
            assertThat(isRecommended)
                .as("Legacy cipher suite %s should not be recommended", cipherSuite)
                .isFalse();
        }
    }
    
    /**
     * Property: The minimum TLS version should always be TLSv1.3.
     */
    @Property(tries = 100)
    void minimumVersionShouldBeTls13() {
        // Act
        String minimumVersion = tlsConfiguration.getMinimumVersion();
        
        // Assert
        assertThat(minimumVersion)
            .as("Minimum TLS version should be TLSv1.3")
            .isEqualTo("TLSv1.3");
    }
    
    // ==================== Providers ====================
    
    @Provide
    Arbitrary<String> insecureTlsVersions() {
        return Arbitraries.of(
            "SSLv2", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"
        );
    }
    
    @Provide
    Arbitrary<String> deprecatedTlsVersions() {
        return Arbitraries.of(
            "SSLv2", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"
        );
    }
    
    @Provide
    Arbitrary<String> allTlsVersions() {
        return Arbitraries.of(
            "SSLv2", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"
        );
    }
    
    @Provide
    Arbitrary<String> nullOrEmptyStrings() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }
    
    @Provide
    Arbitrary<String> cipherSuites() {
        return Arbitraries.of(
            // TLS 1.3 cipher suites (recommended)
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256",
            "TLS_CHACHA20_POLY1305_SHA256",
            // Legacy cipher suites (not recommended)
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA"
        );
    }
}
