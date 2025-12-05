.PHONY: proto build test run clean

proto:
	protoc --go_out=. --go_opt=paths=source_relative \
		--go-grpc_out=. --go-grpc_opt=paths=source_relative \
		proto/tokenization.proto
	protoc --go_out=. --go_opt=paths=source_relative \
		--go-grpc_out=. --go-grpc_opt=paths=source_relative \
		../hsm-simulator/proto/hsm.proto

build: proto
	go build -o bin/tokenization-server cmd/server/main.go

test:
	go test -v ./...

test-property:
	go test -v -run Property ./...

run: build
	./bin/tokenization-server

clean:
	rm -rf bin/
	rm -f proto/*.pb.go
	rm -f internal/hsm/*.pb.go
