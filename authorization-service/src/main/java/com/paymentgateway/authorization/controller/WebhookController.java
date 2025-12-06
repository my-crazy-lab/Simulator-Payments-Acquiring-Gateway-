package com.paymentgateway.authorization.controller;

import com.paymentgateway.authorization.domain.WebhookDelivery;
import com.paymentgateway.authorization.webhook.WebhookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    
    @Autowired
    private WebhookService webhookService;
    
    /**
     * Get webhook delivery history for the authenticated merchant.
     */
    @GetMapping("/deliveries")
    public ResponseEntity<List<WebhookDelivery>> getDeliveryHistory(Authentication authentication) {
        // Extract merchant ID from authentication
        String merchantId = authentication.getName();
        UUID merchantUuid = UUID.fromString(merchantId);
        
        List<WebhookDelivery> deliveries = webhookService.getDeliveryHistory(merchantUuid);
        return ResponseEntity.ok(deliveries);
    }
    
    /**
     * Get webhook delivery history for a specific payment.
     */
    @GetMapping("/deliveries/payment/{paymentId}")
    public ResponseEntity<List<WebhookDelivery>> getPaymentDeliveryHistory(
            @PathVariable UUID paymentId,
            Authentication authentication) {
        
        List<WebhookDelivery> deliveries = webhookService.getPaymentDeliveryHistory(paymentId);
        return ResponseEntity.ok(deliveries);
    }
}
