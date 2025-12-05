package hsm

import (
	"context"
	"fmt"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// Client wraps the HSM gRPC client
type Client struct {
	conn   *grpc.ClientConn
	client HSMServiceClient
}

// NewClient creates a new HSM client
func NewClient(address string) (*Client, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	conn, err := grpc.DialContext(ctx, address,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithBlock(),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to HSM: %w", err)
	}
	
	client := NewHSMServiceClient(conn)
	
	return &Client{
		conn:   conn,
		client: client,
	}, nil
}

// Close closes the HSM client connection
func (c *Client) Close() error {
	return c.conn.Close()
}

// Encrypt encrypts plaintext using the HSM
func (c *Client) Encrypt(keyID string, plaintext, aad []byte) (ciphertext, nonce []byte, keyVersion int, err error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	req := &EncryptRequest{
		KeyId:     keyID,
		Plaintext: plaintext,
		Aad:       aad,
	}
	
	resp, err := c.client.Encrypt(ctx, req)
	if err != nil {
		return nil, nil, 0, fmt.Errorf("HSM encrypt failed: %w", err)
	}
	
	return resp.Ciphertext, resp.Nonce, int(resp.KeyVersion), nil
}

// Decrypt decrypts ciphertext using the HSM
func (c *Client) Decrypt(keyID string, ciphertext, nonce, aad []byte, keyVersion int) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	req := &DecryptRequest{
		KeyId:      keyID,
		Ciphertext: ciphertext,
		Nonce:      nonce,
		Aad:        aad,
		KeyVersion: int32(keyVersion),
	}
	
	resp, err := c.client.Decrypt(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("HSM decrypt failed: %w", err)
	}
	
	return resp.Plaintext, nil
}

// GenerateKey generates a new key in the HSM
func (c *Client) GenerateKey(keyID, algorithm string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	req := &GenerateKeyRequest{
		KeyId:     keyID,
		Algorithm: algorithm,
	}
	
	_, err := c.client.GenerateKey(ctx, req)
	if err != nil {
		return fmt.Errorf("HSM generate key failed: %w", err)
	}
	
	return nil
}
