package com.paymentgateway.fraud.repository;

import com.paymentgateway.fraud.domain.Blacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, String> {
    Optional<Blacklist> findByEntryTypeAndValue(String entryType, String value);
    boolean existsByEntryTypeAndValue(String entryType, String value);
    void deleteByEntryTypeAndValue(String entryType, String value);
}
