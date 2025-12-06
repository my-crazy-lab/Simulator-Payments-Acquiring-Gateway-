package com.paymentgateway.authorization.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.authorization.domain.Merchant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(2) // Run after authentication filter
public class RateLimitFilter extends OncePerRequestFilter {
    
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    
    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Get merchant from request (set by AuthenticationFilter)
        Merchant merchant = (Merchant) request.getAttribute("merchant");
        
        if (merchant != null) {
            if (rateLimitService.isRateLimitExceeded(merchant)) {
                sendRateLimitError(response, merchant);
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private void sendRateLimitError(HttpServletResponse response, Merchant merchant) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        long resetTime = rateLimitService.getResetTime(merchant);
        
        Map<String, Object> error = new HashMap<>();
        error.put("code", "RATE_LIMIT_EXCEEDED");
        error.put("message", "Too many requests");
        error.put("retry_after", resetTime);
        
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("error", error);
        
        response.setHeader("Retry-After", String.valueOf(resetTime));
        response.setHeader("X-RateLimit-Limit", String.valueOf(merchant.getRateLimitPerSecond()));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + resetTime));
        
        response.getWriter().write(objectMapper.writeValueAsString(responseBody));
    }
}
