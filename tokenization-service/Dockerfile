FROM golang:1.21-alpine AS builder

WORKDIR /app

# Copy go mod files
COPY go.mod go.sum* ./
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN go build -o /app/bin/tokenization-server ./cmd/server

# Run tests
FROM golang:1.21-alpine AS test
WORKDIR /app
COPY --from=builder /app .
RUN go test -v ./...

# Final stage
FROM alpine:latest
WORKDIR /root/
COPY --from=builder /app/bin/tokenization-server .
EXPOSE 8445
CMD ["./tokenization-server"]
