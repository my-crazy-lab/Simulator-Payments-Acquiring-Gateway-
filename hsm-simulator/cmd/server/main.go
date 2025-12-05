package main

import (
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"

	"github.com/paymentgateway/hsm-simulator/internal/hsm"
)

const (
	defaultPort = "8444"
)

func main() {
	port := os.Getenv("HSM_PORT")
	if port == "" {
		port = defaultPort
	}

	// Create HSM instance
	hsmService := hsm.NewHSM()

	// For now, just start a simple TCP listener
	// In a full implementation, this would be a gRPC server
	listener, err := net.Listen("tcp", fmt.Sprintf(":%s", port))
	if err != nil {
		log.Fatalf("Failed to listen on port %s: %v", port, err)
	}
	defer listener.Close()

	log.Printf("HSM Simulator started on port %s", port)
	log.Printf("HSM instance initialized: %v", hsmService != nil)

	// Handle graceful shutdown
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-sigChan
		log.Println("Shutting down HSM Simulator...")
		listener.Close()
		os.Exit(0)
	}()

	// Keep the server running
	select {}
}
