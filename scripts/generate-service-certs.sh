#!/bin/bash
# generate-service-certs.sh

# i.e.
# cd scripts
# sudo ./generate-service-certs.sh komunas-app
# sudo ./generate-service-certs.sh medastex-app
# sudo ./generate-service-certs.sh mytrux-app

SERVICE_NAME=$1
if [ -z "$SERVICE_NAME" ]; then
    echo "Usage: $0 <service-name>"
    exit 1
fi

# Validate service name (alphanumeric and hyphens only)
if ! [[ $SERVICE_NAME =~ ^[a-zA-Z0-9-]+$ ]]; then
    echo "Error: Service name must contain only letters, numbers, and hyphens"
    exit 1
fi

# Create keys directory if it doesn't exist
mkdir -p ../keys

# Generate keystore for the new service
if [ ! -f "../keys/${SERVICE_NAME}-keystore-container.jks" ]; then
    echo "Generating new keystore for ${SERVICE_NAME}..."
    keytool -genkeypair \
        -alias "${SERVICE_NAME}-container" \
        -keyalg RSA \
        -keysize 2048 \
        -storetype JKS \
        -keystore "../keys/${SERVICE_NAME}-keystore-container.jks" \
        -storepass 123456 \
        -validity 3650 \
        -dname "CN=${SERVICE_NAME}, OU=Software, O=Linqra, L=Richmond, ST=TX, C=US"
else
    echo "Keystore already exists for ${SERVICE_NAME}, skipping generation..."
fi

# Export certificate
echo "Exporting certificate..."
keytool -exportcert \
    -alias "${SERVICE_NAME}-container" \
    -keystore "../keys/${SERVICE_NAME}-keystore-container.jks" \
    -storepass 123456 \
    -rfc \
    -file "../keys/${SERVICE_NAME}-cert-container.pem"

# Function to check and import certificate into a truststore
import_if_not_exists() {
    local truststore=$1
    local truststore_name=$(basename "$truststore")
    local alias="${SERVICE_NAME}-container"
    
    # First check if alias exists
    if keytool -list -alias "$alias" -keystore "$truststore" -storepass 123456 >/dev/null 2>&1; then
        # If alias exists, verify the certificate content
        echo "Alias exists in ${truststore_name}, verifying certificate content..."
        
        # Export the existing certificate from truststore to a temporary file
        keytool -exportcert -alias "$alias" -keystore "$truststore" -storepass 123456 -rfc -file temp_truststore_cert.pem
        
        # Debug: Show full certificate details
        echo "=== New Certificate Details ==="
        keytool -printcert -file "../keys/${SERVICE_NAME}-cert-container.pem"
        echo "=== Existing Certificate Details ==="
        keytool -printcert -file "temp_truststore_cert.pem"
        
        # Compare the certificates using their SHA-256 fingerprints - FIXED EXTRACTION
        local new_fingerprint=$(keytool -printcert -file "../keys/${SERVICE_NAME}-cert-container.pem" | grep "SHA256:" | sed 's/.*SHA256: //')
        local existing_fingerprint=$(keytool -printcert -file "temp_truststore_cert.pem" | grep "SHA256:" | sed 's/.*SHA256: //')
        
        echo "New fingerprint: $new_fingerprint"
        echo "Existing fingerprint: $existing_fingerprint"
        
        if [ "$new_fingerprint" = "$existing_fingerprint" ]; then
            echo "Certificate content matches in ${truststore_name}"
            rm temp_truststore_cert.pem
            return 0
        else
            echo "Warning: Certificate content mismatch in ${truststore_name}"
            echo "Removing old certificate and importing new one..."
            # Remove the old certificate
            keytool -delete -alias "$alias" -keystore "$truststore" -storepass 123456
            rm temp_truststore_cert.pem
        fi
    fi
    
    # Import the new certificate
    echo "Importing certificate into ${truststore_name}..."
    keytool -importcert \
        -file "../keys/${SERVICE_NAME}-cert-container.pem" \
        -alias "$alias" \
        -keystore "$truststore" \
        -storepass 123456 \
        -noprompt
}

# Function to import AWS root CA certificate into a truststore if not present
import_aws_root_ca_if_not_exists() {
    local truststore=$1
    local truststore_name=$(basename "$truststore")
    local alias="amazon-root-ca"
    local aws_cert_file="../keys/AmazonRootCA1.pem"
    
    # Check if AWS root CA already exists in truststore
    if keytool -list -alias "$alias" -keystore "$truststore" -storepass 123456 >/dev/null 2>&1; then
        echo "AWS Root CA already exists in ${truststore_name}, skipping..."
        return 0
    fi
    
    # Download AWS root CA certificate if it doesn't exist
    if [ ! -f "$aws_cert_file" ]; then
        echo "Downloading AWS Root CA certificate..."
        cd ../keys
        curl -O https://www.amazontrust.com/repository/AmazonRootCA1.pem
        if [ $? -ne 0 ]; then
            echo "Warning: Failed to download AWS Root CA certificate. SSL connections to AWS S3 may fail."
            cd - > /dev/null
            return 1
        fi
        cd - > /dev/null
    fi
    
    # Import AWS root CA into truststore
    echo "Importing AWS Root CA into ${truststore_name}..."
    keytool -importcert \
        -file "$aws_cert_file" \
        -alias "$alias" \
        -keystore "$truststore" \
        -storepass 123456 \
        -noprompt
    
    if [ $? -eq 0 ]; then
        echo "✅ Successfully imported AWS Root CA into ${truststore_name}"
    else
        echo "Warning: Failed to import AWS Root CA into ${truststore_name}"
        return 1
    fi
}

# Import into truststores if not already present
import_if_not_exists "../keys/gateway-truststore.jks"
import_if_not_exists "../keys/client-truststore.jks"
import_if_not_exists "../keys/eureka-truststore.jks"

# Import AWS root CA certificates into truststores
# This is required for SSL connections to AWS S3
echo ""
echo "=== Importing AWS Root CA Certificates ==="
import_aws_root_ca_if_not_exists "../keys/gateway-truststore.jks"
import_aws_root_ca_if_not_exists "../keys/client-truststore.jks"
import_aws_root_ca_if_not_exists "../keys/eureka-truststore.jks"

echo ""
echo "Certificate generation and import completed successfully for ${SERVICE_NAME}"
