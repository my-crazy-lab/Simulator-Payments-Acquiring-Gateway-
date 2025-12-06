package com.paymentgateway.authorization.psp;

import com.paymentgateway.authorization.domain.PSPConfiguration;
import com.paymentgateway.authorization.repository.PSPConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for PSP routing and failover logic.
 * Selects the appropriate PSP based on merchant configuration and handles failover.
 */
@Service
public class PSPRoutingService {
    
    private static final Logger logger = LoggerFactory.getLogger(PSPRoutingService.class);
    
    private final PSPConfigurationRepository pspConfigurationRepository;
    private final Map<String, PSPClient> pspClients;
    
    public PSPRoutingService(PSPConfigurationRepository pspConfigurationRepository,
                            List<PSPClient> pspClientList) {
        this.pspConfigurationRepository = pspConfigurationRepository;
        this.pspClients = pspClientList.stream()
            .collect(Collectors.toMap(PSPClient::getPSPName, client -> client));
        
        logger.info("Initialized PSP routing service with {} PSP clients: {}",
                   pspClients.size(), pspClients.keySet());
    }
    
    /**
     * Route authorization request to appropriate PSP with failover support
     */
    public PSPAuthorizationResponse authorizeWithFailover(PSPAuthorizationRequest request) {
        UUID merchantId = request.getMerchantId();
        
        // Get PSP configurations for merchant, ordered by priority
        List<PSPConfiguration> pspConfigs = pspConfigurationRepository
            .findByMerchantIdAndIsActiveTrueOrderByPriorityAsc(merchantId);
        
        if (pspConfigs.isEmpty()) {
            logger.warn("No PSP configurations found for merchant: {}", merchantId);
            // Use default PSP order if no configuration exists
            pspConfigs = getDefaultPSPConfigurations(merchantId);
        }
        
        PSPAuthorizationResponse lastResponse = null;
        PSPException lastException = null;
        
        // Try each PSP in priority order
        for (PSPConfiguration config : pspConfigs) {
            PSPClient pspClient = pspClients.get(config.getPspName());
            
            if (pspClient == null) {
                logger.warn("PSP client not found for: {}", config.getPspName());
                continue;
            }
            
            if (!pspClient.isAvailable()) {
                logger.warn("PSP {} is not available, trying next PSP", config.getPspName());
                continue;
            }
            
            try {
                logger.info("Attempting authorization with PSP: {} (priority: {})",
                           config.getPspName(), config.getPriority());
                
                PSPAuthorizationResponse response = pspClient.authorize(request);
                
                if (response.isSuccess()) {
                    logger.info("Authorization successful with PSP: {}", config.getPspName());
                    return response;
                } else if ("DECLINED".equals(response.getStatus())) {
                    // Card declined - don't try other PSPs
                    logger.info("Authorization declined by PSP: {} - {}", 
                               config.getPspName(), response.getDeclineMessage());
                    return response;
                } else {
                    // Error response - try next PSP
                    logger.warn("Authorization error from PSP: {} - {}", 
                               config.getPspName(), response.getErrorMessage());
                    lastResponse = response;
                }
                
            } catch (PSPException e) {
                logger.error("PSP {} failed with exception: {}", config.getPspName(), e.getMessage());
                lastException = e;
                
                // If not retryable, don't try other PSPs
                if (!e.isRetryable()) {
                    throw e;
                }
                
                // Continue to next PSP for retryable errors
            }
        }
        
        // All PSPs failed
        if (lastException != null) {
            logger.error("All PSPs failed, throwing last exception");
            throw lastException;
        }
        
        if (lastResponse != null) {
            logger.error("All PSPs failed, returning last error response");
            return lastResponse;
        }
        
        // No PSPs available
        logger.error("No PSPs available for merchant: {}", merchantId);
        return PSPAuthorizationResponse.error("NO_PSP_AVAILABLE", 
                                             "No payment service providers are currently available");
    }
    
    /**
     * Select primary PSP for merchant based on configuration
     */
    public PSPClient selectPSP(UUID merchantId) {
        List<PSPConfiguration> pspConfigs = pspConfigurationRepository
            .findByMerchantIdAndIsActiveTrueOrderByPriorityAsc(merchantId);
        
        if (pspConfigs.isEmpty()) {
            logger.warn("No PSP configurations found for merchant: {}, using default", merchantId);
            return pspClients.get("STRIPE"); // Default to Stripe
        }
        
        PSPConfiguration primaryConfig = pspConfigs.get(0);
        PSPClient pspClient = pspClients.get(primaryConfig.getPspName());
        
        if (pspClient == null) {
            logger.warn("PSP client not found for: {}, using default", primaryConfig.getPspName());
            return pspClients.get("STRIPE");
        }
        
        return pspClient;
    }
    
    /**
     * Get PSP client by name
     */
    public PSPClient getPSPClient(String pspName) {
        PSPClient client = pspClients.get(pspName);
        if (client == null) {
            throw new IllegalArgumentException("PSP not found: " + pspName);
        }
        return client;
    }
    
    /**
     * Get all available PSP clients
     */
    public Collection<PSPClient> getAllPSPClients() {
        return pspClients.values();
    }
    
    /**
     * Get default PSP configurations when none exist for merchant
     */
    private List<PSPConfiguration> getDefaultPSPConfigurations(UUID merchantId) {
        List<PSPConfiguration> defaults = new ArrayList<>();
        
        // Default: Stripe (priority 1), Adyen (priority 2)
        defaults.add(new PSPConfiguration(merchantId, "STRIPE", 1));
        defaults.add(new PSPConfiguration(merchantId, "ADYEN", 2));
        
        return defaults;
    }
}
