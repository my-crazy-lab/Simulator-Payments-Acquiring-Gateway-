package main

import (
	"log"
	"net"
	"time"

	"github.com/paymentgateway/tokenization-service/internal/hsm"
	"github.com/paymentgateway/tokenization-service/internal/server"
	"github.com/paymentgateway/tokenization-service/internal/tokenization"
	"google.golang.org/grpc"
)

const (
	port          = ":8445"
	hsmAddress    = "localhost:8444"
	keyID         = "tokenization-key-1"
	tokenTTL      = 24 * time.Hour * 365 // 1 year
)

func main() {
	log.Println("Starting Tokenization Service...")
	
	// Connect to HSM
	log.Printf("Connecting to HSM at %s...", hsmAddress)
	hsmClient, err := hsm.NewClient(hsmAddress)
	if err != nil {
		log.Fatalf("Failed to connect to HSM: %v", err)
	}
	defer hsmClient.Close()
	
	// Generate key if needed
	log.Printf("Ensuring key %s exists...", keyID)
	if err := hsmClient.GenerateKey(keyID, "AES-256-GCM"); err != nil {
		log.Printf("Key may already exist: %v", err)
	}
	
	// Create tokenization service
	tokenService := tokenization.NewService(hsmClient, keyID, tokenTTL)
	
	// Create gRPC server
	grpcServer := grpc.NewServer()
	server.RegisterTokenizationServiceServer(grpcServer, server.NewServer(tokenService))
	
	// Start listening
	listener, err := net.Listen("tcp", port)
	if err != nil {
		log.Fatalf("Failed to listen: %v", err)
	}
	
	log.Printf("Tokenization Service listening on %s", port)
	if err := grpcServer.Serve(listener); err != nil {
		log.Fatalf("Failed to serve: %v", err)
	}
}
