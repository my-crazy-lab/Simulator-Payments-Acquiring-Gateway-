package com.paymentgateway.authorization.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Payment event message for Kafka publishing.
 * This represents the event schema that will be validated against Schema Registry.
 */
public class PaymentEventMessage {
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("event_type")
    private PaymentEventType eventType;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("trace_id")
    private String traceId;
    
    @JsonProperty("payload")
    private PaymentEventPayload payload;
    
    public PaymentEventMessage() {
    }
    
    public PaymentEventMessage(String eventId, PaymentEventType eventType, Instant timestamp,
                              String correlationId, String traceId, PaymentEventPayload payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.payload = payload;
    }
    
    // Getters and setters
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public PaymentEventType getEventType() {
        return eventType;
    }
    
    public void setEventType(PaymentEventType eventType) {
        this.eventType = eventType;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    public PaymentEventPayload getPayload() {
        return payload;
    }
    
    public void setPayload(PaymentEventPayload payload) {
        this.payload = payload;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentEventMessage that = (PaymentEventMessage) o;
        return Objects.equals(eventId, that.eventId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
    
    @Override
    public String toString() {
        return "PaymentEventMessage{" +
                "eventId='" + eventId + '\'' +
                ", eventType=" + eventType +
                ", timestamp=" + timestamp +
                ", correlationId='" + correlationId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", payload=" + payload +
                '}';
    }
    
    public static class PaymentEventPayload {
        @JsonProperty("payment_id")
        private String paymentId;
        
        @JsonProperty("merchant_id")
        private String merchantId;
        
        @JsonProperty("amount")
        private BigDecimal amount;
        
        @JsonProperty("currency")
        private String currency;
        
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("psp_transaction_id")
        private String pspTransactionId;
        
        @JsonProperty("fraud_score")
        private BigDecimal fraudScore;
        
        @JsonProperty("three_ds_status")
        private String threeDsStatus;
        
        public PaymentEventPayload() {
        }
        
        // Getters and setters
        public String getPaymentId() {
            return paymentId;
        }
        
        public void setPaymentId(String paymentId) {
            this.paymentId = paymentId;
        }
        
        public String getMerchantId() {
            return merchantId;
        }
        
        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }
        
        public BigDecimal getAmount() {
            return amount;
        }
        
        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
        
        public String getCurrency() {
            return currency;
        }
        
        public void setCurrency(String currency) {
            this.currency = currency;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public String getPspTransactionId() {
            return pspTransactionId;
        }
        
        public void setPspTransactionId(String pspTransactionId) {
            this.pspTransactionId = pspTransactionId;
        }
        
        public BigDecimal getFraudScore() {
            return fraudScore;
        }
        
        public void setFraudScore(BigDecimal fraudScore) {
            this.fraudScore = fraudScore;
        }
        
        public String getThreeDsStatus() {
            return threeDsStatus;
        }
        
        public void setThreeDsStatus(String threeDsStatus) {
            this.threeDsStatus = threeDsStatus;
        }
        
        @Override
        public String toString() {
            return "PaymentEventPayload{" +
                    "paymentId='" + paymentId + '\'' +
                    ", merchantId='" + merchantId + '\'' +
                    ", amount=" + amount +
                    ", currency='" + currency + '\'' +
                    ", status='" + status + '\'' +
                    ", pspTransactionId='" + pspTransactionId + '\'' +
                    ", fraudScore=" + fraudScore +
                    ", threeDsStatus='" + threeDsStatus + '\'' +
                    '}';
        }
    }
}
