#!/bin/bash
# verify-ssl-config.sh

SERVICE_NAME=$1
if [ -z "$SERVICE_NAME" ]; then
    echo "Usage: $0 <service-name>"
    exit 1
fi

# Validate service name
if ! [[ $SERVICE_NAME =~ ^[a-zA-Z0-9-]+$ ]]; then
    echo "Error: Service name must contain only letters, numbers, and hyphens"
    exit 1
fi

# Verify keystore exists
if [ ! -f "../keys/${SERVICE_NAME}-keystore-container.jks" ]; then
    echo "Error: ${SERVICE_NAME}-keystore-container.jks not found"
    exit 1
fi

# Verify certificate exists
if [ ! -f "../keys/${SERVICE_NAME}-cert-container.pem" ]; then
    echo "Error: ${SERVICE_NAME}-cert-container.pem not found"
    exit 1
fi

# Function to verify certificate in truststore
verify_in_truststore() {
    local truststore=$1
    local truststore_name=$(basename "$truststore")
    local alias=$2
    
    echo "Verifying certificate '${alias}' in ${truststore_name}..."
    keytool -list -alias "$alias" \
        -keystore "$truststore" \
        -storepass 123456 > /dev/null 2>&1

    if [ $? -ne 0 ]; then
        echo "Error: Certificate '${alias}' not found in ${truststore_name}"
        return 1
    fi
    return 0
}

# Function to verify AWS root CA certificate in truststore
verify_aws_root_ca() {
    local truststore=$1
    local truststore_name=$(basename "$truststore")
    
    echo "Verifying AWS Root CA certificate in ${truststore_name}..."
    keytool -list -alias amazon-root-ca \
        -keystore "$truststore" \
        -storepass 123456 > /dev/null 2>&1

    if [ $? -ne 0 ]; then
        echo "Warning: AWS Root CA certificate not found in ${truststore_name}"
        echo "  SSL connections to AWS S3 may fail without this certificate"
        echo "  Run 'generate-service-certs.sh' to automatically import it"
        return 1
    fi
    return 0
}

# Verify service certificate in all truststores
echo "=== Verifying Service Certificate ==="
verify_in_truststore "../keys/gateway-truststore.jks" "${SERVICE_NAME}-container" || exit 1
verify_in_truststore "../keys/eureka-truststore.jks" "${SERVICE_NAME}-container" || exit 1
verify_in_truststore "../keys/client-truststore.jks" "${SERVICE_NAME}-container" || exit 1

# Verify AWS root CA certificate in all truststores
echo ""
echo "=== Verifying AWS Root CA Certificate ==="
verify_aws_root_ca "../keys/gateway-truststore.jks" || echo "  (Non-critical, but recommended)"
verify_aws_root_ca "../keys/eureka-truststore.jks" || echo "  (Non-critical, but recommended)"
verify_aws_root_ca "../keys/client-truststore.jks" || echo "  (Non-critical, but recommended)"

echo ""
echo "SSL configuration verified successfully for ${SERVICE_NAME}"
