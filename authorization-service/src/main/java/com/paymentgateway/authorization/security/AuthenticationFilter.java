package com.paymentgateway.authorization.security;

import com.paymentgateway.authorization.domain.Merchant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AuthenticationFilter extends OncePerRequestFilter {
    
    private final MerchantAuthenticationService authenticationService;
    
    public AuthenticationFilter(MerchantAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        try {
            Optional<Merchant> merchant = authenticateMerchant(request);
            
            if (merchant.isPresent()) {
                Merchant m = merchant.get();
                
                // Create authentication token with roles
                var authorities = m.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());
                
                UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                                m.getMerchantId(), 
                                null, 
                                authorities);
                
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Store merchant in request attribute for easy access
                request.setAttribute("merchant", m);
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Could not set merchant authentication", e);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private Optional<Merchant> authenticateMerchant(HttpServletRequest request) {
        // Try API Key authentication first (X-API-Key header)
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return authenticationService.authenticateByApiKey(apiKey);
        }
        
        // Try JWT authentication (Authorization: Bearer <token>)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return authenticationService.authenticateByJwt(token);
        }
        
        return Optional.empty();
    }
}
