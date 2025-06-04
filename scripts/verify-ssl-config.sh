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
    
    echo "Verifying certificate in ${truststore_name}..."
    keytool -list -alias ${SERVICE_NAME}-container \
        -keystore "$truststore" \
        -storepass 123456 > /dev/null 2>&1

    if [ $? -ne 0 ]; then
        echo "Error: Certificate not found in ${truststore_name}"
        return 1
    fi
    return 0
}

# Verify certificate in all truststores
verify_in_truststore "../keys/gateway-truststore.jks" || exit 1
verify_in_truststore "../keys/eureka-truststore.jks" || exit 1
verify_in_truststore "../keys/client-truststore.jks" || exit 1

echo "SSL configuration verified successfully for ${SERVICE_NAME}"
