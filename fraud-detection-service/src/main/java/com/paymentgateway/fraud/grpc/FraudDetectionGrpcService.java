package com.paymentgateway.fraud.grpc;

import com.paymentgateway.fraud.domain.Blacklist;
import com.paymentgateway.fraud.domain.FraudRule;
import com.paymentgateway.fraud.repository.BlacklistRepository;
import com.paymentgateway.fraud.repository.FraudRuleRepository;
import com.paymentgateway.fraud.service.FraudDetectionService;
import com.paymentgateway.fraud.service.FraudEvaluationRequest;
import com.paymentgateway.fraud.service.FraudEvaluationResult;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;

@GrpcService
public class FraudDetectionGrpcService extends FraudDetectionServiceGrpc.FraudDetectionServiceImplBase {
    
    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionGrpcService.class);
    
    private final FraudDetectionService fraudDetectionService;
    private final FraudRuleRepository fraudRuleRepository;
    private final BlacklistRepository blacklistRepository;
    
    public FraudDetectionGrpcService(
            FraudDetectionService fraudDetectionService,
            FraudRuleRepository fraudRuleRepository,
            BlacklistRepository blacklistRepository) {
        this.fraudDetectionService = fraudDetectionService;
        this.fraudRuleRepository = fraudRuleRepository;
        this.blacklistRepository = blacklistRepository;
    }
    
    @Override
    public void evaluateTransaction(FraudRequest request, StreamObserver<FraudResponse> responseObserver) {
        try {
            logger.info("Received fraud evaluation request for transaction: {}", request.getTransactionId());
            
            // Convert gRPC request to service request
            FraudEvaluationRequest evalRequest = convertToEvaluationRequest(request);
            
            // Evaluate transaction
            FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(evalRequest);
            
            // Convert result to gRPC response
            FraudResponse response = FraudResponse.newBuilder()
                .setFraudScore(result.getFraudScore())
                .setStatus(convertStatus(result.getStatus()))
                .addAllTriggeredRules(result.getTriggeredRules())
                .setRequire3Ds(result.isRequire3DS())
                .setRiskLevel(result.getRiskLevel())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error evaluating transaction: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    @Transactional
    public void updateRules(RuleUpdateRequest request, StreamObserver<RuleUpdateResponse> responseObserver) {
        try {
            logger.info("Updating fraud rule: {}", request.getRuleName());
            
            FraudRule rule;
            if (request.getRuleId() != null && !request.getRuleId().isEmpty()) {
                rule = fraudRuleRepository.findById(request.getRuleId())
                    .orElse(new FraudRule());
            } else {
                rule = new FraudRule();
            }
            
            rule.setRuleName(request.getRuleName());
            rule.setRuleCondition(request.getRuleCondition());
            rule.setPriority(request.getPriority());
            rule.setEnabled(request.getEnabled());
            rule.setUpdatedAt(java.time.Instant.now());
            
            fraudRuleRepository.save(rule);
            
            RuleUpdateResponse response = RuleUpdateResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Rule updated successfully")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error updating rule: {}", e.getMessage(), e);
            
            RuleUpdateResponse response = RuleUpdateResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Error: " + e.getMessage())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    @Transactional
    public void addToBlacklist(BlacklistRequest request, StreamObserver<BlacklistResponse> responseObserver) {
        try {
            logger.info("Adding to blacklist: type={}, value={}", request.getEntryType(), request.getValue());
            
            Blacklist entry = new Blacklist(
                request.getEntryType(),
                request.getValue(),
                request.getReason()
            );
            
            blacklistRepository.save(entry);
            
            BlacklistResponse response = BlacklistResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Entry added to blacklist")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error adding to blacklist: {}", e.getMessage(), e);
            
            BlacklistResponse response = BlacklistResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Error: " + e.getMessage())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    @Transactional
    public void removeFromBlacklist(BlacklistRequest request, StreamObserver<BlacklistResponse> responseObserver) {
        try {
            logger.info("Removing from blacklist: type={}, value={}", request.getEntryType(), request.getValue());
            
            blacklistRepository.deleteByEntryTypeAndValue(request.getEntryType(), request.getValue());
            
            BlacklistResponse response = BlacklistResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Entry removed from blacklist")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error removing from blacklist: {}", e.getMessage(), e);
            
            BlacklistResponse response = BlacklistResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Error: " + e.getMessage())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    private FraudEvaluationRequest convertToEvaluationRequest(FraudRequest request) {
        FraudEvaluationRequest.Address address = null;
        if (request.hasBillingAddress()) {
            Address grpcAddress = request.getBillingAddress();
            address = new FraudEvaluationRequest.Address(
                grpcAddress.getStreet(),
                grpcAddress.getCity(),
                grpcAddress.getState(),
                grpcAddress.getPostalCode(),
                grpcAddress.getCountry()
            );
        }
        
        return new FraudEvaluationRequest(
            request.getTransactionId(),
            new BigDecimal(request.getAmount()),
            request.getCurrency(),
            request.getCardToken(),
            request.getIpAddress(),
            request.getDeviceFingerprint(),
            address,
            request.getMerchantId(),
            new HashMap<>(request.getMetadataMap())
        );
    }
    
    private FraudStatus convertStatus(com.paymentgateway.fraud.domain.FraudStatus status) {
        return switch (status) {
            case CLEAN -> FraudStatus.CLEAN;
            case REVIEW -> FraudStatus.REVIEW;
            case BLOCK -> FraudStatus.BLOCK;
        };
    }
}
