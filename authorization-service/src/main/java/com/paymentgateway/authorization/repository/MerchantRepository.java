package com.paymentgateway.authorization.repository;

import com.paymentgateway.authorization.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    
    Optional<Merchant> findByMerchantId(String merchantId);
    
    Optional<Merchant> findByApiKeyHash(String apiKeyHash);
    
    boolean existsByMerchantId(String merchantId);
}
