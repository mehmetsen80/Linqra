#!/bin/bash

# Security Scan Script using Snyk
# Usage: ./scripts/security-scan.sh

echo "🛡️  Starting Local Security Scan..."

# Check if Snyk is installed
if ! command -v snyk &> /dev/null; then
    echo "❌ Snyk CLI is not installed."
    echo "👉 Please install it: npm install -g snyk"
    echo "👉 Then authenticate: snyk auth"
    exit 1
fi

# Check if authenticated
if ! snyk auth --test &> /dev/null; then
    echo "⚠️  Snyk is not authenticated."
    echo "👉 Please run: snyk auth"
    exit 1
fi

FAILURES=0

echo ""
echo "📦 Scanning Backend (Maven/Java)..."
if [ -f "pom.xml" ]; then
    snyk test --file=pom.xml
    if [ $? -ne 0 ]; then
        echo "❌ Backend vulnerabilities found!"
        FAILURES=$((FAILURES+1))
    else
        echo "✅ Backend looks good."
    fi
elif [ -f "api-gateway/pom.xml" ]; then
     snyk test --file=api-gateway/pom.xml
     if [ $? -ne 0 ]; then
         echo "❌ Backend (api-gateway) vulnerabilities found!"
         FAILURES=$((FAILURES+1))
     else
         echo "✅ Backend looks good."
     fi
else
    echo "⚠️  pom.xml not found, skipping backend scan."
fi

echo ""
echo "🌐 Scanning Frontend (Node/React)..."
if [ -d "edge-service" ]; then
    cd edge-service
    snyk test
    if [ $? -ne 0 ]; then
        echo "❌ Frontend vulnerabilities found!"
        FAILURES=$((FAILURES+1))
    else
        echo "✅ Frontend looks good."
    fi
    cd ..
else
    echo "⚠️  edge-service directory not found, skipping frontend scan."
fi

echo ""
if [ $FAILURES -eq 0 ]; then
    echo "✅ SECURITY SCAN PASSED: No high-severity vulnerabilities found."
    exit 0
else
    echo "❌ SECURITY SCAN FAILED: Found $FAILURES vulnerable projects."
    exit 1
fi
