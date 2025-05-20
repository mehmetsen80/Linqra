#!/bin/bash

# Check if gateway keystore exists
if [ ! -f "../keys/gateway-keystore-container.jks" ]; then
    echo "Error: gateway-keystore-container.jks not found in ../keys/"
    echo "Cannot proceed with HAProxy certificate generation"
    exit 1
fi

echo "Found gateway keystore, proceeding with HAProxy certificate generation..."

# Create haproxy directory if it doesn't exist
echo "Creating HAProxy directory..."
mkdir -p ../keys/haproxy

# Export certificate in proper PEM format
echo "Exporting certificate in PEM format..."
keytool -exportcert \
        -alias gateway-app-container \
        -keystore ../keys/gateway-keystore-container.jks \
        -storepass 123456 \
        -rfc \
        -file ../keys/haproxy/gateway-cert-container.pem

# Convert JKS to P12
echo "Converting JKS to P12 format..."
keytool -importkeystore \
        -srckeystore ../keys/gateway-keystore-container.jks \
        -destkeystore ../keys/haproxy/gateway-keystore-container.p12 \
        -deststoretype PKCS12 \
        -srcalias gateway-app-container \
        -srcstorepass 123456 \
        -deststorepass 123456

# Extract private key from P12
echo "Extracting private key from P12..."
openssl pkcs12 \
        -in ../keys/haproxy/gateway-keystore-container.p12 \
        -nocerts \
        -out ../keys/haproxy/gateway-private-container.pem \
        -nodes \
        -passin pass:123456

# Combine certificate and private key
echo "Combining certificate and private key..."
cat ../keys/haproxy/gateway-cert-container.pem ../keys/haproxy/gateway-private-container.pem > ../keys/haproxy/gateway-combined-container.pem

# Set proper permissions
echo "Setting file permissions..."
chmod 600 ../keys/haproxy/gateway-cert-container.pem
chmod 600 ../keys/haproxy/gateway-private-container.pem
chmod 600 ../keys/haproxy/gateway-keystore-container.p12
chmod 644 ../keys/haproxy/gateway-combined-container.pem

echo "HAProxy certificates generated successfully"
