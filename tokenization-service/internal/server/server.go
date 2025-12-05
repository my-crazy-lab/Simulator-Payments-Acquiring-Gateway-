package server

import (
	"context"
	"fmt"
	"log"

	"github.com/paymentgateway/tokenization-service/internal/tokenization"
)

// Server implements the TokenizationService gRPC server
type Server struct {
	UnimplementedTokenizationServiceServer
	service *tokenization.Service
}

// NewServer creates a new gRPC server
func NewServer(service *tokenization.Service) *Server {
	return &Server{
		service: service,
	}
}

// TokenizeCard tokenizes a card PAN
func (s *Server) TokenizeCard(ctx context.Context, req *TokenizeRequest) (*TokenizeResponse, error) {
	log.Printf("TokenizeCard request: last4=%s, expiry=%d/%d", 
		req.Pan[len(req.Pan)-4:], req.ExpiryMonth, req.ExpiryYear)
	
	tokenData, err := s.service.TokenizeCard(
		req.Pan,
		int(req.ExpiryMonth),
		int(req.ExpiryYear),
		req.Cvv,
	)
	if err != nil {
		log.Printf("TokenizeCard error: %v", err)
		return nil, fmt.Errorf("tokenization failed: %w", err)
	}
	
	return &TokenizeResponse{
		Token:     tokenData.Token,
		LastFour:  tokenData.LastFour,
		CardBrand: tokenData.CardBrand,
		ExpiresAt: tokenData.ExpiresAt.Unix(),
	}, nil
}

// DetokenizeCard retrieves the original PAN from a token
func (s *Server) DetokenizeCard(ctx context.Context, req *DetokenizeRequest) (*DetokenizeResponse, error) {
	log.Printf("DetokenizeCard request: token=%s", req.Token)
	
	pan, expiryMonth, expiryYear, err := s.service.DetokenizeCard(req.Token)
	if err != nil {
		log.Printf("DetokenizeCard error: %v", err)
		return nil, fmt.Errorf("detokenization failed: %w", err)
	}
	
	return &DetokenizeResponse{
		Pan:         pan,
		ExpiryMonth: int32(expiryMonth),
		ExpiryYear:  int32(expiryYear),
	}, nil
}

// ValidateToken validates a token
func (s *Server) ValidateToken(ctx context.Context, req *ValidateRequest) (*ValidateResponse, error) {
	log.Printf("ValidateToken request: token=%s", req.Token)
	
	valid, err := s.service.ValidateToken(req.Token)
	if err != nil {
		return &ValidateResponse{
			Valid:        false,
			ErrorMessage: err.Error(),
		}, nil
	}
	
	return &ValidateResponse{
		Valid:        valid,
		ErrorMessage: "",
	}, nil
}
