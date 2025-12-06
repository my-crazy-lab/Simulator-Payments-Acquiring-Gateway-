package com.paymentgateway.authorization.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * TLS configuration service for enforcing TLS 1.3 on all external connections.
 * 
 * Requirements: 1.5
 */
@Component
public class TlsConfiguration {
    
    // Minimum required TLS version
    public static final String MINIMUM_TLS_VERSION = "TLSv1.3";
    
    // Allowed TLS protocols
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("TLSv1.3");
    
    // Deprecated/insecure protocols that must be rejected
    private static final Set<String> REJECTED_PROTOCOLS = Set.of(
        "SSLv2", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"
    );
    
    // Recommended cipher suites for TLS 1.3
    private static final List<String> RECOMMENDED_CIPHER_SUITES = List.of(
        "TLS_AES_256_GCM_SHA384",
        "TLS_AES_128_GCM_SHA256",
        "TLS_CHACHA20_POLY1305_SHA256"
    );
    
    @Value("${tls.enforce-version:true}")
    private boolean enforceVersion = true;
    
    @Value("${tls.minimum-version:TLSv1.3}")
    private String minimumVersion = MINIMUM_TLS_VERSION;
    
    /**
     * Validates that a TLS version meets the minimum requirement.
     * 
     * @param tlsVersion The TLS version to validate (e.g., "TLSv1.3")
     * @return true if the version is allowed, false otherwise
     */
    public boolean isVersionAllowed(String tlsVersion) {
        if (tlsVersion == null || tlsVersion.isEmpty()) {
            return false;
        }
        
        // Only TLS 1.3 is allowed
        return ALLOWED_PROTOCOLS.contains(tlsVersion);
    }
    
    /**
     * Checks if a TLS version should be rejected.
     * 
     * @param tlsVersion The TLS version to check
     * @return true if the version should be rejected, false otherwise
     */
    public boolean isVersionRejected(String tlsVersion) {
        if (tlsVersion == null || tlsVersion.isEmpty()) {
            return true;
        }
        
        return REJECTED_PROTOCOLS.contains(tlsVersion);
    }
    
    /**
     * Gets the minimum required TLS version.
     * 
     * @return The minimum TLS version string
     */
    public String getMinimumVersion() {
        return minimumVersion;
    }
    
    /**
     * Gets the list of allowed TLS protocols.
     * 
     * @return Set of allowed protocol names
     */
    public Set<String> getAllowedProtocols() {
        return ALLOWED_PROTOCOLS;
    }
    
    /**
     * Gets the list of rejected TLS protocols.
     * 
     * @return Set of rejected protocol names
     */
    public Set<String> getRejectedProtocols() {
        return REJECTED_PROTOCOLS;
    }
    
    /**
     * Gets the recommended cipher suites for TLS 1.3.
     * 
     * @return List of recommended cipher suite names
     */
    public List<String> getRecommendedCipherSuites() {
        return RECOMMENDED_CIPHER_SUITES;
    }
    
    /**
     * Validates a cipher suite is appropriate for TLS 1.3.
     * 
     * @param cipherSuite The cipher suite to validate
     * @return true if the cipher suite is recommended, false otherwise
     */
    public boolean isRecommendedCipherSuite(String cipherSuite) {
        if (cipherSuite == null || cipherSuite.isEmpty()) {
            return false;
        }
        return RECOMMENDED_CIPHER_SUITES.contains(cipherSuite);
    }
    
    /**
     * Creates SSL parameters configured for TLS 1.3 only.
     * 
     * @return SSLParameters configured for TLS 1.3
     */
    public SSLParameters createSecureSSLParameters() {
        SSLParameters params = new SSLParameters();
        params.setProtocols(ALLOWED_PROTOCOLS.toArray(new String[0]));
        params.setCipherSuites(RECOMMENDED_CIPHER_SUITES.toArray(new String[0]));
        params.setEndpointIdentificationAlgorithm("HTTPS");
        params.setUseCipherSuitesOrder(true);
        return params;
    }
    
    /**
     * Validates that the system supports TLS 1.3.
     * 
     * @return true if TLS 1.3 is supported, false otherwise
     */
    public boolean isTls13Supported() {
        try {
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            return context != null;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
    
    /**
     * Gets the default SSL context protocols.
     * 
     * @return Array of supported protocols
     */
    public String[] getDefaultProtocols() {
        try {
            SSLContext context = SSLContext.getDefault();
            return context.getDefaultSSLParameters().getProtocols();
        } catch (NoSuchAlgorithmException e) {
            return new String[0];
        }
    }
    
    /**
     * Validates a connection's TLS version.
     * 
     * @param connectionProtocol The protocol used by the connection
     * @return ValidationResult containing the validation outcome
     */
    public TlsValidationResult validateConnection(String connectionProtocol) {
        if (connectionProtocol == null || connectionProtocol.isEmpty()) {
            return new TlsValidationResult(false, "No TLS protocol specified");
        }
        
        if (isVersionRejected(connectionProtocol)) {
            return new TlsValidationResult(false, 
                "TLS version " + connectionProtocol + " is not allowed. Minimum required: " + MINIMUM_TLS_VERSION);
        }
        
        if (!isVersionAllowed(connectionProtocol)) {
            return new TlsValidationResult(false, 
                "TLS version " + connectionProtocol + " is not in the allowed list");
        }
        
        return new TlsValidationResult(true, "TLS version " + connectionProtocol + " is allowed");
    }
    
    /**
     * Result of TLS validation.
     */
    public static class TlsValidationResult {
        private final boolean valid;
        private final String message;
        
        public TlsValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
