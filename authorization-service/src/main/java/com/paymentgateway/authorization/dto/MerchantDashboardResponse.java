package com.paymentgateway.authorization.dto;

import java.math.BigDecimal;
import java.util.Map;

public class MerchantDashboardResponse {
    
    private long totalTransactions;
    private long successfulTransactions;
    private long failedTransactions;
    private BigDecimal totalVolume;
    private BigDecimal averageTransactionAmount;
    private Map<String, Long> transactionsByStatus;
    private Map<String, BigDecimal> volumeByCurrency;
    
    // Constructors
    public MerchantDashboardResponse() {}
    
    // Getters and Setters
    public long getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(long totalTransactions) { this.totalTransactions = totalTransactions; }
    
    public long getSuccessfulTransactions() { return successfulTransactions; }
    public void setSuccessfulTransactions(long successfulTransactions) { this.successfulTransactions = successfulTransactions; }
    
    public long getFailedTransactions() { return failedTransactions; }
    public void setFailedTransactions(long failedTransactions) { this.failedTransactions = failedTransactions; }
    
    public BigDecimal getTotalVolume() { return totalVolume; }
    public void setTotalVolume(BigDecimal totalVolume) { this.totalVolume = totalVolume; }
    
    public BigDecimal getAverageTransactionAmount() { return averageTransactionAmount; }
    public void setAverageTransactionAmount(BigDecimal averageTransactionAmount) { this.averageTransactionAmount = averageTransactionAmount; }
    
    public Map<String, Long> getTransactionsByStatus() { return transactionsByStatus; }
    public void setTransactionsByStatus(Map<String, Long> transactionsByStatus) { this.transactionsByStatus = transactionsByStatus; }
    
    public Map<String, BigDecimal> getVolumeByCurrency() { return volumeByCurrency; }
    public void setVolumeByCurrency(Map<String, BigDecimal> volumeByCurrency) { this.volumeByCurrency = volumeByCurrency; }
}
