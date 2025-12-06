package com.paymentgateway.authorization.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymentgateway.authorization.event.PaymentEventMessage;
import com.paymentgateway.authorization.event.PaymentEventType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Feature: payment-acquiring-gateway, Property 26: Event Schema Validation
 * 
 * For any event published to Kafka, the event payload must validate successfully 
 * against the registered schema in the Schema Registry.
 * 
 * Validates: Requirements 14.2
 */
public class EventSchemaValidationPropertyTest {
    
    private static final JsonSchema eventSchema;
    private static final ObjectMapper objectMapper;
    
    static {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for Instant
        // Configure to write dates as ISO-8601 strings, not timestamps
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        // Define JSON Schema for PaymentEventMessage
        String schemaJson = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["event_id", "event_type", "timestamp", "correlation_id", "trace_id", "payload"],
              "properties": {
                "event_id": {
                  "type": "string",
                  "pattern": "^evt_[a-zA-Z0-9]{24}$"
                },
                "event_type": {
                  "type": "string",
                  "enum": ["PAYMENT_CREATED", "PAYMENT_AUTHORIZED", "PAYMENT_DECLINED", "PAYMENT_CAPTURED", "PAYMENT_CANCELLED", "PAYMENT_REFUNDED", "PAYMENT_FAILED"]
                },
                "timestamp": {
                  "type": "string",
                  "format": "date-time"
                },
                "correlation_id": {
                  "type": "string",
                  "minLength": 1
                },
                "trace_id": {
                  "type": "string",
                  "minLength": 1
                },
                "payload": {
                  "type": "object",
                  "required": ["payment_id", "merchant_id", "amount", "currency", "status"],
                  "properties": {
                    "payment_id": {
                      "type": "string",
                      "pattern": "^pay_[a-zA-Z0-9]{24}$"
                    },
                    "merchant_id": {
                      "type": "string",
                      "minLength": 1
                    },
                    "amount": {
                      "type": "number",
                      "minimum": 0
                    },
                    "currency": {
                      "type": "string",
                      "pattern": "^[A-Z]{3}$"
                    },
                    "status": {
                      "type": "string",
                      "minLength": 1
                    },
                    "psp_transaction_id": {
                      "type": ["string", "null"]
                    },
                    "fraud_score": {
                      "type": ["number", "null"],
                      "minimum": 0,
                      "maximum": 1
                    },
                    "three_ds_status": {
                      "type": ["string", "null"]
                    }
                  }
                }
              }
            }
            """;
        
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        eventSchema = factory.getSchema(schemaJson);
    }
    
    @Property(tries = 100)
    void eventMessageShouldValidateAgainstSchema(
            @ForAll("validEventMessages") PaymentEventMessage event) throws Exception {
        
        // Convert event to JSON
        String eventJson = objectMapper.writeValueAsString(event);
        JsonNode eventNode = objectMapper.readTree(eventJson);
        
        // Validate against schema
        Set<ValidationMessage> errors = eventSchema.validate(eventNode);
        
        // Assert no validation errors
        if (!errors.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("Schema validation failed:\n");
            for (ValidationMessage error : errors) {
                errorMsg.append("  - ").append(error.getMessage()).append("\n");
            }
            throw new AssertionError(errorMsg.toString());
        }
    }
    
    @Provide
    Arbitrary<PaymentEventMessage> validEventMessages() {
        return Combinators.combine(
                validEventIds(),
                Arbitraries.of(PaymentEventType.values()),
                Arbitraries.just(Instant.now()),
                validPaymentIds(),
                validTraceIds(),
                validPayloads()
        ).as((eventId, eventType, timestamp, correlationId, traceId, payload) -> 
                new PaymentEventMessage(eventId, eventType, timestamp, correlationId, traceId, payload)
        );
    }
    
    @Provide
    Arbitrary<String> validEventIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(24)
                .map(s -> "evt_" + s);
    }
    
    @Provide
    Arbitrary<String> validPaymentIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(24)
                .map(s -> "pay_" + s);
    }
    
    @Provide
    Arbitrary<String> validTraceIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(32);
    }
    
    @Provide
    Arbitrary<String> validMerchantIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(36); // UUID format
    }
    
    @Provide
    Arbitrary<BigDecimal> validAmounts() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(999999.99))
                .ofScale(2);
    }
    
    @Provide
    Arbitrary<String> validCurrencies() {
        return Arbitraries.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD");
    }
    
    @Provide
    Arbitrary<String> validStatuses() {
        return Arbitraries.of("PENDING", "AUTHORIZED", "CAPTURED", "DECLINED", "CANCELLED", "REFUNDED");
    }
    
    @Provide
    Arbitrary<BigDecimal> validFraudScores() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.ONE)
                .ofScale(2);
    }
    
    @Provide
    Arbitrary<PaymentEventMessage.PaymentEventPayload> validPayloads() {
        return Combinators.combine(
                validPaymentIds(),
                validMerchantIds(),
                validAmounts(),
                validCurrencies(),
                validStatuses(),
                Arbitraries.strings().alpha().numeric().ofLength(20).injectNull(0.3),
                validFraudScores().injectNull(0.3),
                Arbitraries.of("AUTHENTICATED", "NOT_ENROLLED", "FAILED").injectNull(0.3)
        ).as((paymentId, merchantId, amount, currency, status, pspTxnId, fraudScore, threeDsStatus) -> {
            PaymentEventMessage.PaymentEventPayload payload = new PaymentEventMessage.PaymentEventPayload();
            payload.setPaymentId(paymentId);
            payload.setMerchantId(merchantId);
            payload.setAmount(amount);
            payload.setCurrency(currency);
            payload.setStatus(status);
            payload.setPspTransactionId(pspTxnId);
            payload.setFraudScore(fraudScore);
            payload.setThreeDsStatus(threeDsStatus);
            return payload;
        });
    }
}
