package com.paymentgateway.authorization.controller;

import com.paymentgateway.authorization.domain.Merchant;
import com.paymentgateway.authorization.dto.ApiKeyResponse;
import com.paymentgateway.authorization.dto.LoginRequest;
import com.paymentgateway.authorization.dto.TokenResponse;
import com.paymentgateway.authorization.repository.MerchantRepository;
import com.paymentgateway.authorization.security.MerchantAuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final MerchantAuthenticationService authenticationService;
    private final MerchantRepository merchantRepository;
    
    public AuthController(MerchantAuthenticationService authenticationService,
                         MerchantRepository merchantRepository) {
        this.authenticationService = authenticationService;
        this.merchantRepository = merchantRepository;
    }
    
    /**
     * Login endpoint - generates JWT token
     * In production, this would validate credentials against a secure store
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        // Find merchant by merchant ID
        Merchant merchant = merchantRepository.findByMerchantId(request.getMerchantId())
                .filter(Merchant::getIsActive)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        
        // Generate JWT token
        String token = authenticationService.generateToken(merchant);
        
        TokenResponse response = new TokenResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresIn(86400); // 24 hours
        response.setMerchantId(merchant.getMerchantId());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Create new API key for authenticated merchant
     */
    @PostMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiKeyResponse> createApiKey(Authentication authentication) {
        String merchantId = (String) authentication.getPrincipal();
        
        Merchant merchant = merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
        
        String apiKey = authenticationService.createApiKey(merchant);
        
        ApiKeyResponse response = new ApiKeyResponse();
        response.setApiKey(apiKey);
        response.setMessage("API key created successfully. Store this securely - it won't be shown again.");
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Revoke API key for authenticated merchant
     */
    @DeleteMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> revokeApiKey(Authentication authentication) {
        String merchantId = (String) authentication.getPrincipal();
        
        Merchant merchant = merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
        
        authenticationService.revokeApiKey(merchant);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "API key revoked successfully");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get current merchant info
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentMerchant(Authentication authentication) {
        String merchantId = (String) authentication.getPrincipal();
        
        Merchant merchant = merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
        
        Map<String, Object> response = new HashMap<>();
        response.put("merchantId", merchant.getMerchantId());
        response.put("merchantName", merchant.getMerchantName());
        response.put("roles", merchant.getRoles());
        response.put("rateLimitPerSecond", merchant.getRateLimitPerSecond());
        response.put("isActive", merchant.getIsActive());
        
        return ResponseEntity.ok(response);
    }
}
