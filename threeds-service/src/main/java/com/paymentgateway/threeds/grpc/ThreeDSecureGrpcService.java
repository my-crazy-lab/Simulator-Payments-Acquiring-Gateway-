package com.paymentgateway.threeds.grpc;

import com.paymentgateway.threeds.domain.BrowserInfo;
import com.paymentgateway.threeds.domain.ThreeDSTransaction;
import com.paymentgateway.threeds.service.ThreeDSService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

@GrpcService
public class ThreeDSecureGrpcService extends ThreeDSecureServiceGrpc.ThreeDSecureServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(ThreeDSecureGrpcService.class);

    private final ThreeDSService threeDSService;

    public ThreeDSecureGrpcService(ThreeDSService threeDSService) {
        this.threeDSService = threeDSService;
    }

    @Override
    public void initiateAuth(ThreeDSRequest request, StreamObserver<ThreeDSResponse> responseObserver) {
        try {
            logger.info("Received InitiateAuth request for transaction: {}", request.getTransactionId());

            BrowserInfo browserInfo = convertBrowserInfo(request.getBrowserInfo());
            BigDecimal amount = new BigDecimal(request.getAmount());

            ThreeDSTransaction transaction = threeDSService.initiateAuthentication(
                request.getTransactionId(),
                request.getMerchantId(),
                amount,
                request.getCurrency(),
                request.getCardToken(),
                request.getMerchantReturnUrl(),
                browserInfo
            );

            ThreeDSResponse response = buildResponse(transaction);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error initiating 3DS authentication", e);
            ThreeDSResponse errorResponse = ThreeDSResponse.newBuilder()
                .setStatus(com.paymentgateway.threeds.grpc.ThreeDSStatus.FAILED)
                .setErrorMessage("Failed to initiate authentication: " + e.getMessage())
                .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void completeAuth(ThreeDSCompleteRequest request, StreamObserver<ThreeDSCompleteResponse> responseObserver) {
        try {
            logger.info("Received CompleteAuth request for transaction: {}", request.getTransactionId());

            ThreeDSTransaction transaction = threeDSService.completeAuthentication(
                request.getTransactionId(),
                request.getPares()
            );

            ThreeDSCompleteResponse response = ThreeDSCompleteResponse.newBuilder()
                .setStatus(convertStatus(transaction.getStatus()))
                .setCavv(transaction.getCavv() != null ? transaction.getCavv() : "")
                .setEci(transaction.getEci() != null ? transaction.getEci() : "")
                .setXid(transaction.getXid() != null ? transaction.getXid() : "")
                .setErrorMessage(transaction.getErrorMessage() != null ? transaction.getErrorMessage() : "")
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error completing 3DS authentication", e);
            ThreeDSCompleteResponse errorResponse = ThreeDSCompleteResponse.newBuilder()
                .setStatus(com.paymentgateway.threeds.grpc.ThreeDSStatus.FAILED)
                .setErrorMessage("Failed to complete authentication: " + e.getMessage())
                .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void validateAuth(ValidateAuthRequest request, StreamObserver<ValidateAuthResponse> responseObserver) {
        try {
            logger.info("Received ValidateAuth request for transaction: {}", request.getTransactionId());

            ThreeDSTransaction transaction = threeDSService.getTransaction(request.getTransactionId());

            if (transaction == null) {
                ValidateAuthResponse response = ValidateAuthResponse.newBuilder()
                    .setIsAuthenticated(false)
                    .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            boolean isAuthenticated = transaction.getStatus() == com.paymentgateway.threeds.domain.ThreeDSStatus.AUTHENTICATED
                || transaction.getStatus() == com.paymentgateway.threeds.domain.ThreeDSStatus.FRICTIONLESS;

            ValidateAuthResponse response = ValidateAuthResponse.newBuilder()
                .setIsAuthenticated(isAuthenticated)
                .setCavv(transaction.getCavv() != null ? transaction.getCavv() : "")
                .setEci(transaction.getEci() != null ? transaction.getEci() : "")
                .setXid(transaction.getXid() != null ? transaction.getXid() : "")
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error validating 3DS authentication", e);
            ValidateAuthResponse errorResponse = ValidateAuthResponse.newBuilder()
                .setIsAuthenticated(false)
                .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    private BrowserInfo convertBrowserInfo(com.paymentgateway.threeds.grpc.BrowserInfo grpcBrowserInfo) {
        return new BrowserInfo(
            grpcBrowserInfo.getUserAgent(),
            grpcBrowserInfo.getAcceptHeader(),
            grpcBrowserInfo.getLanguage(),
            grpcBrowserInfo.getScreenWidth(),
            grpcBrowserInfo.getScreenHeight(),
            grpcBrowserInfo.getColorDepth(),
            grpcBrowserInfo.getTimezoneOffset(),
            grpcBrowserInfo.getJavaEnabled(),
            grpcBrowserInfo.getJavascriptEnabled(),
            grpcBrowserInfo.getIpAddress()
        );
    }

    private ThreeDSResponse buildResponse(ThreeDSTransaction transaction) {
        return ThreeDSResponse.newBuilder()
            .setStatus(convertStatus(transaction.getStatus()))
            .setAcsUrl(transaction.getAcsUrl() != null ? transaction.getAcsUrl() : "")
            .setTransactionId(transaction.getTransactionId())
            .setCavv(transaction.getCavv() != null ? transaction.getCavv() : "")
            .setEci(transaction.getEci() != null ? transaction.getEci() : "")
            .setXid(transaction.getXid() != null ? transaction.getXid() : "")
            .setErrorMessage(transaction.getErrorMessage() != null ? transaction.getErrorMessage() : "")
            .build();
    }

    private com.paymentgateway.threeds.grpc.ThreeDSStatus convertStatus(
            com.paymentgateway.threeds.domain.ThreeDSStatus domainStatus) {
        return switch (domainStatus) {
            case FRICTIONLESS -> com.paymentgateway.threeds.grpc.ThreeDSStatus.FRICTIONLESS;
            case CHALLENGE_REQUIRED -> com.paymentgateway.threeds.grpc.ThreeDSStatus.CHALLENGE_REQUIRED;
            case AUTHENTICATED -> com.paymentgateway.threeds.grpc.ThreeDSStatus.AUTHENTICATED;
            case FAILED -> com.paymentgateway.threeds.grpc.ThreeDSStatus.FAILED;
            case TIMEOUT -> com.paymentgateway.threeds.grpc.ThreeDSStatus.TIMEOUT;
            default -> com.paymentgateway.threeds.grpc.ThreeDSStatus.UNKNOWN;
        };
    }
}
