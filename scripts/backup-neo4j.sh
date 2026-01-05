#!/bin/bash
#
# Neo4j Backup Script
# Backs up Neo4j graph database to S3 with local retention
#
# Usage: ./backup-neo4j.sh <environment>
# Cron:  30 2 * * * /var/www/linqra/scripts/backup-neo4j.sh ec2 >> /var/log/neo4j-backup.log 2>&1
#

set -e  # Exit on error

# Parse arguments
ENVIRONMENT="${1:-}"

if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>"
    echo "  environment: dev or ec2"
    echo ""
    echo "Examples:"
    echo "  $0 dev    # Backup to s3://backup-linqra-neo4j-dev"
    echo "  $0 ec2    # Backup to s3://backup-linqra-neo4j"
    exit 1
fi

# Environment-specific configuration
case $ENVIRONMENT in
    dev)
        S3_BUCKET="s3://backup-linqra-neo4j-dev"
        LINQRA_HOME="/Users/mehmetsen/IdeaProjects/Linqra"
        ;;
    ec2)
        S3_BUCKET="s3://backup-linqra-neo4j"
        LINQRA_HOME="/var/www/linqra"
        ;;
    *)
        echo "Unknown environment: ${ENVIRONMENT}. Use 'dev' or 'ec2'"
        exit 1
        ;;
esac

# Configuration
RETENTION_DAYS=30
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="neo4j-backup-${TIMESTAMP}.tar.gz"
CONTAINER_NAME="neo4j-service"
S3_REGION="us-east-1"
BACKUP_DIR="${LINQRA_HOME}/.kube/neo4j/backups"
NEO4J_DATA_PATH="/data"

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

# Create backup directory
log_info "Creating backup directory: ${BACKUP_DIR}"
mkdir -p ${BACKUP_DIR}

# Check if Neo4j container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_error "Neo4j container '${CONTAINER_NAME}' is not running!"
    exit 1
fi

# Create backup using tar (hot backup of data directory)
log_info "Starting Neo4j backup..."
log_info "Backup file: ${BACKUP_FILE}"
log_info "Data path: ${NEO4J_DATA_PATH}"

docker exec ${CONTAINER_NAME} tar -czf /tmp/${BACKUP_FILE} -C ${NEO4J_DATA_PATH} . 2>&1 | while read line; do
    log_info "tar: $line"
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
DELETED_COUNT=$(find ${BACKUP_DIR} -name "neo4j-backup-*.tar.gz" -mtime +${RETENTION_DAYS} -delete -print | wc -l)
log_info "Deleted ${DELETED_COUNT} old backup(s)"

# Clean up backup inside container
log_info "Cleaning up backup inside container..."
docker exec ${CONTAINER_NAME} rm -f /tmp/${BACKUP_FILE}

# Summary
log_info "=========================================="
log_info "Neo4j Backup Completed Successfully!"
log_info "  File: ${BACKUP_FILE}"
log_info "  Size: ${BACKUP_SIZE} bytes"
log_info "  Local: ${BACKUP_DIR}/${BACKUP_FILE}"
log_info "  S3: ${S3_BUCKET}/${BACKUP_FILE}"
log_info "=========================================="
