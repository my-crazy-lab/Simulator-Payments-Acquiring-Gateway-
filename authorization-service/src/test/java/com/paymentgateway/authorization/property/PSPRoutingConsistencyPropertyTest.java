package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.domain.PSPConfiguration;
import com.paymentgateway.authorization.psp.*;
import com.paymentgateway.authorization.repository.PSPConfigurationRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Feature: payment-acquiring-gateway, Property 6: PSP Routing Consistency
 * 
 * For any payment request with specific merchant configuration and routing rules,
 * the same PSP should be selected consistently given the same inputs.
 * 
 * Validates: Requirements 3.1
 */
class PSPRoutingConsistencyPropertyTest {
    
    private PSPRoutingService createPSPRoutingService(PSPConfigurationRepository mockRepository) {
        StripePSPClient stripePSPClient = new StripePSPClient();
        AdyenPSPClient adyenPSPClient = new AdyenPSPClient();
        List<PSPClient> pspClients = Arrays.asList(stripePSPClient, adyenPSPClient);
        return new PSPRoutingService(mockRepository, pspClients);
    }
    
    @Property(tries = 100)
    void pspRoutingIsConsistent(
            @ForAll("merchantIds") UUID merchantId,
            @ForAll @Positive BigDecimal amount,
            @ForAll("currencies") String currency,
            @ForAll("pspConfigurations") List<PSPConfiguration> pspConfigs) {
        
        // Given: A merchant with specific PSP configuration
        PSPConfigurationRepository mockRepository = Mockito.mock(PSPConfigurationRepository.class);
        when(mockRepository.findByMerchantIdAndIsActiveTrueOrderByPriorityAsc(merchantId))
            .thenReturn(pspConfigs);
        
        PSPRoutingService pspRoutingService = createPSPRoutingService(mockRepository);
        
        // When: We create authorization requests with the same inputs
        PSPAuthorizationRequest request1 = createAuthorizationRequest(merchantId, amount, currency);
        PSPAuthorizationRequest request2 = createAuthorizationRequest(merchantId, amount, currency);
        
        // Then: The same PSP should be selected for both requests
        // We verify this by checking that the routing logic produces consistent results
        PSPClient selectedPSP1 = pspRoutingService.selectPSP(merchantId);
        PSPClient selectedPSP2 = pspRoutingService.selectPSP(merchantId);
        
        assertThat(selectedPSP1.getPSPName())
            .as("PSP selection should be consistent for the same merchant configuration")
            .isEqualTo(selectedPSP2.getPSPName());
        
        // Additionally verify that the selected PSP matches the highest priority configuration
        if (!pspConfigs.isEmpty()) {
            String expectedPSP = pspConfigs.get(0).getPspName();
            assertThat(selectedPSP1.getPSPName())
                .as("Selected PSP should match the highest priority configuration")
                .isEqualTo(expectedPSP);
        }
    }
    
    @Property(tries = 100)
    void sameMerchantConfigurationProducesSamePSPSelection(
            @ForAll("merchantIds") UUID merchantId,
            @ForAll @IntRange(min = 1, max = 2) int priority) {
        
        // Given: A specific PSP configuration for a merchant
        PSPConfiguration config = new PSPConfiguration(merchantId, "STRIPE", priority);
        config.setIsActive(true);
        
        List<PSPConfiguration> configs = Collections.singletonList(config);
        PSPConfigurationRepository mockRepository = Mockito.mock(PSPConfigurationRepository.class);
        when(mockRepository.findByMerchantIdAndIsActiveTrueOrderByPriorityAsc(merchantId))
            .thenReturn(configs);
        
        PSPRoutingService pspRoutingService = createPSPRoutingService(mockRepository);
        
        // When: We select PSP multiple times
        PSPClient psp1 = pspRoutingService.selectPSP(merchantId);
        PSPClient psp2 = pspRoutingService.selectPSP(merchantId);
        PSPClient psp3 = pspRoutingService.selectPSP(merchantId);
        
        // Then: All selections should be identical
        assertThat(psp1.getPSPName())
            .isEqualTo(psp2.getPSPName())
            .isEqualTo(psp3.getPSPName())
            .isEqualTo("STRIPE");
    }
    
    @Property(tries = 100)
    void pspSelectionRespectsConfiguredPriority(
            @ForAll("merchantIds") UUID merchantId,
            @ForAll("pspNames") String primaryPSP,
            @ForAll("pspNames") String secondaryPSP) {
        
        Assume.that(!primaryPSP.equals(secondaryPSP));
        
        // Given: Two PSP configurations with different priorities
        PSPConfiguration primary = new PSPConfiguration(merchantId, primaryPSP, 1);
        primary.setIsActive(true);
        
        PSPConfiguration secondary = new PSPConfiguration(merchantId, secondaryPSP, 2);
        secondary.setIsActive(true);
        
        List<PSPConfiguration> configs = Arrays.asList(primary, secondary);
        PSPConfigurationRepository mockRepository = Mockito.mock(PSPConfigurationRepository.class);
        when(mockRepository.findByMerchantIdAndIsActiveTrueOrderByPriorityAsc(merchantId))
            .thenReturn(configs);
        
        PSPRoutingService pspRoutingService = createPSPRoutingService(mockRepository);
        
        // When: We select PSP
        PSPClient selectedPSP = pspRoutingService.selectPSP(merchantId);
        
        // Then: The primary PSP (lower priority number) should be selected
        assertThat(selectedPSP.getPSPName())
            .as("PSP with lower priority number should be selected")
            .isEqualTo(primaryPSP);
    }
    
    // Arbitraries
    
    @Provide
    Arbitrary<UUID> merchantIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> currencies() {
        return Arbitraries.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD");
    }
    
    @Provide
    Arbitrary<String> pspNames() {
        return Arbitraries.of("STRIPE", "ADYEN");
    }
    
    @Provide
    Arbitrary<List<PSPConfiguration>> pspConfigurations() {
        return Combinators.combine(
            merchantIds(),
            pspNames(),
            Arbitraries.integers().between(1, 5)
        ).as((merchantId, pspName, priority) -> {
            PSPConfiguration config = new PSPConfiguration(merchantId, pspName, priority);
            config.setIsActive(true);
            return Collections.singletonList(config);
        });
    }
    
    private PSPAuthorizationRequest createAuthorizationRequest(UUID merchantId, BigDecimal amount, String currency) {
        PSPAuthorizationRequest request = new PSPAuthorizationRequest();
        request.setMerchantId(merchantId);
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setCardTokenId(UUID.randomUUID());
        return request;
    }
}
