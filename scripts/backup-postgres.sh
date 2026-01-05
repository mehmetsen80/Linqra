#!/bin/bash
#
# PostgreSQL Backup Script
# Backs up PostgreSQL database (Keycloak) to S3 with local retention
#
# Usage: ./backup-postgres.sh <environment>
#   environment: dev or ec2
#
# Example:
#   ./backup-postgres.sh dev
#   ./backup-postgres.sh ec2
#

set -e  # Exit on error

# Parse arguments
ENVIRONMENT="${1:-}"

if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>"
    echo "  environment: dev or ec2"
    echo ""
    echo "Examples:"
    echo "  $0 dev    # Backup to s3://backup-linqra-postgres-dev"
    echo "  $0 ec2    # Backup to s3://backup-linqra-postgres"
    exit 1
fi

# Environment-specific configuration
case $ENVIRONMENT in
    dev)
        S3_BUCKET="s3://backup-linqra-postgres-dev"
        LINQRA_HOME="/Users/mehmetsen/IdeaProjects/Linqra"
        ;;
    ec2)
        S3_BUCKET="s3://backup-linqra-postgres"
        LINQRA_HOME="/var/www/linqra"
        ;;
    *)
        echo "Unknown environment: ${ENVIRONMENT}. Use 'dev' or 'ec2'"
        exit 1
        ;;
esac

# Configuration
RETENTION_DAYS=7
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="postgres-backup-${TIMESTAMP}.sql.gz"
S3_REGION="us-east-1"
BACKUP_DIR="${LINQRA_HOME}/.kube/postgres/backups"

# Auto-detect container name
CONTAINER_NAME=$(docker ps --format '{{.Names}}' | grep "postgres-service" | head -n 1)
if [ -z "$CONTAINER_NAME" ]; then
    CONTAINER_NAME="postgres-service" # Fallback
fi

# PostgreSQL connection (reads from environment or uses defaults)
POSTGRES_USER="${POSTGRES_USER:-keycloak}"
POSTGRES_DB="${POSTGRES_DB:-keycloak}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')] INFO:${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARN:${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1"
}

# Cleanup function
cleanup() {
    if [ -n "${BACKUP_FILE}" ]; then
        docker exec ${CONTAINER_NAME} rm -f /tmp/${BACKUP_FILE} 2>/dev/null || true
    fi
}
trap cleanup EXIT

log_info "=========================================="
log_info "PostgreSQL Backup Starting"
log_info "  Environment: ${ENVIRONMENT}"
log_info "  S3 Bucket: ${S3_BUCKET}"
log_info "  Database: ${POSTGRES_DB}"
log_info "  User: ${POSTGRES_USER}"
log_info "=========================================="

# Create backup directory
log_info "Creating backup directory: ${BACKUP_DIR}"
mkdir -p ${BACKUP_DIR}

# Check if PostgreSQL container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_error "PostgreSQL container '${CONTAINER_NAME}' is not running!"
    exit 1
fi

# Create backup using pg_dump
log_info "Starting pg_dump..."
log_info "Backup file: ${BACKUP_FILE}"

# pg_dump with gzip compression
docker exec ${CONTAINER_NAME} bash -c "pg_dump -U ${POSTGRES_USER} ${POSTGRES_DB} | gzip > /tmp/${BACKUP_FILE}" 2>&1 | while read line; do
    log_info "pg_dump: $line"
done

# Verify backup was created
if ! docker exec ${CONTAINER_NAME} test -f /tmp/${BACKUP_FILE}; then
    log_error "Backup file was not created inside container!"
    exit 1
fi

# Get backup size
BACKUP_SIZE=$(docker exec ${CONTAINER_NAME} stat -c%s /tmp/${BACKUP_FILE} 2>/dev/null || echo "unknown")
log_info "Backup size: ${BACKUP_SIZE} bytes"

# Copy from container to host
log_info "Copying backup from container to ${BACKUP_DIR}/"
docker cp ${CONTAINER_NAME}:/tmp/${BACKUP_FILE} ${BACKUP_DIR}/

# Verify local copy
if [ ! -f "${BACKUP_DIR}/${BACKUP_FILE}" ]; then
    log_error "Failed to copy backup to host!"
    exit 1
fi

# Upload to S3
log_info "Uploading backup to ${S3_BUCKET}/${BACKUP_FILE}"
if aws s3 cp ${BACKUP_DIR}/${BACKUP_FILE} ${S3_BUCKET}/${BACKUP_FILE} --region ${S3_REGION}; then
    log_info "Successfully uploaded to S3"
else
    log_error "Failed to upload to S3!"
    # Don't exit - keep local backup even if S3 fails
fi

# Clean up old local backups (keep RETENTION_DAYS days)
log_info "Cleaning up local backups older than ${RETENTION_DAYS} days..."
DELETED_COUNT=$(find ${BACKUP_DIR} -name "postgres-backup-*.sql.gz" -mtime +${RETENTION_DAYS} -delete -print | wc -l)
log_info "Deleted ${DELETED_COUNT} old backup(s)"

# Clean up backup inside container
log_info "Cleaning up backup inside container..."
docker exec ${CONTAINER_NAME} rm -f /tmp/${BACKUP_FILE}

# Summary
log_info "=========================================="
log_info "PostgreSQL Backup Completed Successfully!"
log_info "  File: ${BACKUP_FILE}"
log_info "  Size: ${BACKUP_SIZE} bytes"
log_info "  Local: ${BACKUP_DIR}/${BACKUP_FILE}"
log_info "  S3: ${S3_BUCKET}/${BACKUP_FILE}"
log_info "=========================================="
