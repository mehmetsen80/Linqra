#!/bin/bash
#
# Milvus Complete Restore Script
# Restores Milvus + etcd + MinIO (all required components) from S3 or local backup
#
# Usage: ./restore-milvus.sh <environment> <backup-file>
#

set -e  # Exit on error

# Parse arguments
ENVIRONMENT="${1:-}"
BACKUP_FILE="${2:-}"

if [ -z "$ENVIRONMENT" ] || [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <environment> <backup-file>"
    echo "  environment: dev or ec2"
    echo "  backup-file: Name of the backup file (e.g., milvus-complete-backup-20241211-020000.tar.gz)"
    echo ""
    echo "Examples:"
    echo "  $0 dev milvus-complete-backup-20241211-020000.tar.gz"
    echo "  $0 ec2 milvus-complete-backup-20241211-020000.tar.gz"
    exit 1
fi

# Environment-specific configuration
case $ENVIRONMENT in
    dev)
        S3_BUCKET="s3://backup-linqra-milvus-dev"
        LINQRA_HOME="/Users/mehmetsen/IdeaProjects/Linqra"
        ;;
    ec2)
        S3_BUCKET="s3://backup-linqra-milvus"
        LINQRA_HOME="/var/www/linqra"
        ;;
    *)
        echo "Unknown environment: ${ENVIRONMENT}. Use 'dev' or 'ec2'"
        exit 1
        ;;
esac

# Configuration
S3_REGION="us-east-1"
BACKUP_DIR="${LINQRA_HOME}/.kube/backups/milvus"

# Data directories to restore
MILVUS_DATA="${LINQRA_HOME}/.kube/milvus/data"
ETCD_DATA="${LINQRA_HOME}/.kube/etcd/data"
MINIO_DATA="${LINQRA_HOME}/.kube/minio/data"

# Container names
MILVUS_CONTAINER="milvus-service"
ETCD_CONTAINER="etcd-service"
MINIO_CONTAINER="minio-service"

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

# Create backup directory if needed
mkdir -p ${BACKUP_DIR}

# Check if backup exists locally, if not download from S3
LOCAL_BACKUP="${BACKUP_DIR}/${BACKUP_FILE}"
if [ ! -f "${LOCAL_BACKUP}" ]; then
    log_info "Backup not found locally, downloading from S3..."
    if aws s3 cp ${S3_BUCKET}/${BACKUP_FILE} ${LOCAL_BACKUP} --region ${S3_REGION}; then
        log_info "Downloaded backup from S3"
    else
        log_error "Failed to download backup from S3!"
        exit 1
    fi
else
    log_info "Using local backup: ${LOCAL_BACKUP}"
fi

# Verify backup file exists
if [ ! -f "${LOCAL_BACKUP}" ]; then
    log_error "Backup file not found: ${LOCAL_BACKUP}"
    exit 1
fi

# Warning
log_warn "=========================================="
log_warn "WARNING: This will REPLACE all Milvus data!"
log_warn "Components: milvus, etcd, minio"
log_warn "Backup: ${BACKUP_FILE}"
log_warn "=========================================="
echo ""
read -p "Are you sure you want to continue? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    log_info "Restore cancelled."
    exit 0
fi

# Stop all Milvus-related containers
log_info "Stopping Milvus-related containers..."
for container in "${MILVUS_CONTAINER}" "${MINIO_CONTAINER}" "${ETCD_CONTAINER}"; do
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        log_info "  Stopping ${container}..."
        docker stop ${container} || true
    else
        log_info "  ${container} is not running"
    fi
done

# Wait for graceful shutdown
sleep 5

# Create temp directory for extraction
TEMP_RESTORE_DIR=$(mktemp -d)
trap "rm -rf ${TEMP_RESTORE_DIR}" EXIT

# Extract backup
log_info "Extracting backup..."
tar -xzf "${LOCAL_BACKUP}" -C "${TEMP_RESTORE_DIR}"

# Verify extracted structure
log_info "Verifying backup contents..."
for component in "milvus" "etcd" "minio"; do
    if [ -d "${TEMP_RESTORE_DIR}/${component}" ]; then
        log_info "  ✓ Found ${component} data"
    else
        log_error "  ✗ Missing ${component} data in backup!"
        exit 1
    fi
done

# Clear existing data and restore each component
log_info "Restoring Milvus data..."
rm -rf "${MILVUS_DATA:?}"/* 2>/dev/null || true
cp -r "${TEMP_RESTORE_DIR}/milvus/." "${MILVUS_DATA}/" 2>/dev/null || true

log_info "Restoring etcd data..."
rm -rf "${ETCD_DATA:?}"/* 2>/dev/null || true
cp -r "${TEMP_RESTORE_DIR}/etcd/." "${ETCD_DATA}/" 2>/dev/null || true

log_info "Restoring MinIO data..."
rm -rf "${MINIO_DATA:?}"/* 2>/dev/null || true
cp -r "${TEMP_RESTORE_DIR}/minio/." "${MINIO_DATA}/" 2>/dev/null || true

# Restart containers in correct order (etcd first, then minio, then milvus)
log_info "Starting containers in order..."

log_info "  Starting etcd..."
docker start ${ETCD_CONTAINER} || true
sleep 3

log_info "  Starting minio..."
docker start ${MINIO_CONTAINER} || true
sleep 3

log_info "  Starting milvus..."
docker start ${MILVUS_CONTAINER} || true
sleep 5

# Check health
log_info "Checking container health..."
for container in "${ETCD_CONTAINER}" "${MINIO_CONTAINER}" "${MILVUS_CONTAINER}"; do
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        log_info "  ✓ ${container} is running"
    else
        log_warn "  ⚠ ${container} failed to start"
    fi
done

# Wait for Milvus health
log_info "Waiting for Milvus to be ready..."
sleep 10
if docker exec ${MILVUS_CONTAINER} curl -sf http://localhost:9091/api/v1/health > /dev/null 2>&1; then
    log_info "Milvus is healthy!"
else
    log_warn "Milvus health check failed - may still be starting up"
fi

# Summary
log_info "=========================================="
log_info "Milvus Complete Restore Finished!"
log_info "  Backup: ${BACKUP_FILE}"
log_info "  Components: milvus, etcd, minio"
log_info "=========================================="
