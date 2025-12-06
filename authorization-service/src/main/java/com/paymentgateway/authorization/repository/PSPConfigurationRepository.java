package com.paymentgateway.authorization.repository;

import com.paymentgateway.authorization.domain.PSPConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PSPConfigurationRepository extends JpaRepository<PSPConfiguration, UUID> {
    
    List<PSPConfiguration> findByMerchantIdAndIsActiveTrueOrderByPriorityAsc(UUID merchantId);
    
    List<PSPConfiguration> findByMerchantIdOrderByPriorityAsc(UUID merchantId);
}
