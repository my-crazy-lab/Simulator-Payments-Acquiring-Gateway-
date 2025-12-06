package com.paymentgateway.fraud.repository;

import com.paymentgateway.fraud.domain.FraudRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudRuleRepository extends JpaRepository<FraudRule, String> {
    List<FraudRule> findByEnabledTrueOrderByPriorityAsc();
}
