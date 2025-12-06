package com.paymentgateway.authorization.security;

import com.paymentgateway.authorization.domain.Merchant;
import com.paymentgateway.authorization.repository.MerchantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MerchantAuthenticationService {
    
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    
    public MerchantAuthenticationService(
            MerchantRepository merchantRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    /**
     * Authenticate merchant using API key
     */
    public Optional<Merchant> authenticateByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        
        // Hash the provided API key
        String apiKeyHash = passwordEncoder.encode(apiKey);
        
        // Find merchant by API key hash
        // Note: In production, we'd need to iterate through merchants and verify
        // the hash since we can't query by hash directly with bcrypt
        return merchantRepository.findAll().stream()
                .filter(m -> m.getIsActive() && 
                           m.getApiKeyHash() != null &&
                           passwordEncoder.matches(apiKey, m.getApiKeyHash()))
                .findFirst();
    }
    
    /**
     * Validate JWT token and extract merchant
     */
    public Optional<Merchant> authenticateByJwt(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        
        try {
            if (!jwtTokenProvider.validateToken(token)) {
                return Optional.empty();
            }
            
            String merchantId = jwtTokenProvider.getMerchantIdFromToken(token);
            return merchantRepository.findByMerchantId(merchantId)
                    .filter(Merchant::getIsActive);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Generate JWT token for merchant
     */
    public String generateToken(Merchant merchant) {
        return jwtTokenProvider.generateToken(merchant);
    }
    
    /**
     * Create new API key for merchant
     */
    public String createApiKey(Merchant merchant) {
        // Generate a secure random API key
        String apiKey = ApiKeyGenerator.generate();
        
        // Hash and store
        merchant.setApiKeyHash(passwordEncoder.encode(apiKey));
        merchantRepository.save(merchant);
        
        // Return the plain API key (only time it's visible)
        return apiKey;
    }
    
    /**
     * Revoke API key
     */
    public void revokeApiKey(Merchant merchant) {
        merchant.setApiKeyHash(null);
        merchantRepository.save(merchant);
    }
}
