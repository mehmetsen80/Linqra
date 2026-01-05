#!/bin/bash
#
# Milvus Complete Backup Script
# Backs up Milvus + etcd + MinIO (all required components) to S3
#
# Usage: ./backup-milvus.sh <environment>
# Cron:  0 2 * * * /var/www/linqra/scripts/backup-milvus.sh ec2 >> /var/log/milvus-backup.log 2>&1
#

set -e  # Exit on error

# Parse arguments
ENVIRONMENT="${1:-}"

if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>"
    echo "  environment: dev or ec2"
    echo ""
    echo "Examples:"
    echo "  $0 dev    # Backup to s3://backup-linqra-milvus-dev"
    echo "  $0 ec2    # Backup to s3://backup-linqra-milvus"
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
RETENTION_DAYS=30
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="milvus-complete-backup-${TIMESTAMP}.tar.gz"
S3_REGION="us-east-1"
BACKUP_DIR="${LINQRA_HOME}/.kube/backups/milvus"

# Data directories to backup (all 3 components)
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

# Create backup directory
log_info "Creating backup directory: ${BACKUP_DIR}"
mkdir -p ${BACKUP_DIR}

# Verify data directories exist
log_info "Verifying data directories..."
for dir in "${MILVUS_DATA}" "${ETCD_DATA}" "${MINIO_DATA}"; do
    if [ ! -d "$dir" ]; then
        log_error "Data directory not found: $dir"
        exit 1
    fi
    log_info "  Found: $dir"
done

# Check if containers are running (optional - backup can work from host volumes)
log_info "Checking container status..."
for container in "${MILVUS_CONTAINER}" "${ETCD_CONTAINER}" "${MINIO_CONTAINER}"; do
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        log_info "  ✓ ${container} is running"
    else
        log_warn "  ⚠ ${container} is not running (backup will proceed from host volumes)"
    fi
done

# Create combined backup from host (backing up volume mount directories)
log_info "Starting Milvus complete backup..."
log_info "Backup file: ${BACKUP_FILE}"
log_info "Components: milvus, etcd, minio"

# Create a temporary directory structure for clean backup
TEMP_BACKUP_DIR=$(mktemp -d)
trap "rm -rf ${TEMP_BACKUP_DIR}" EXIT

log_info "Copying data to temp directory..."
mkdir -p "${TEMP_BACKUP_DIR}/milvus" "${TEMP_BACKUP_DIR}/etcd" "${TEMP_BACKUP_DIR}/minio"

# Copy each component
log_info "  Copying Milvus data..."
cp -r "${MILVUS_DATA}/." "${TEMP_BACKUP_DIR}/milvus/" 2>/dev/null || true

log_info "  Copying etcd data..."
cp -r "${ETCD_DATA}/." "${TEMP_BACKUP_DIR}/etcd/" 2>/dev/null || true

log_info "  Copying MinIO data..."
cp -r "${MINIO_DATA}/." "${TEMP_BACKUP_DIR}/minio/" 2>/dev/null || true

# Create tarball
log_info "Creating compressed archive..."
tar -czf "${BACKUP_DIR}/${BACKUP_FILE}" -C "${TEMP_BACKUP_DIR}" .

# Verify backup was created
if [ ! -f "${BACKUP_DIR}/${BACKUP_FILE}" ]; then
    log_error "Backup file was not created!"
    exit 1
fi

# Get backup size
BACKUP_SIZE=$(stat -f%z "${BACKUP_DIR}/${BACKUP_FILE}" 2>/dev/null || stat -c%s "${BACKUP_DIR}/${BACKUP_FILE}" 2>/dev/null || echo "unknown")
BACKUP_SIZE_MB=$(echo "scale=2; ${BACKUP_SIZE} / 1024 / 1024" | bc 2>/dev/null || echo "N/A")
log_info "Backup size: ${BACKUP_SIZE_MB} MB"

# Upload to S3
log_info "Uploading backup to ${S3_BUCKET}/${BACKUP_FILE}"
if aws s3 cp "${BACKUP_DIR}/${BACKUP_FILE}" "${S3_BUCKET}/${BACKUP_FILE}" --region ${S3_REGION}; then
    log_info "Successfully uploaded to S3"
else
    log_error "Failed to upload to S3!"
    # Don't exit - keep local backup even if S3 fails
fi

# Clean up old local backups (keep RETENTION_DAYS days)
log_info "Cleaning up local backups older than ${RETENTION_DAYS} days..."
DELETED_COUNT=$(find ${BACKUP_DIR} -name "milvus-complete-backup-*.tar.gz" -mtime +${RETENTION_DAYS} -delete -print 2>/dev/null | wc -l)
log_info "Deleted ${DELETED_COUNT} old backup(s)"

# Summary
log_info "=========================================="
log_info "Milvus Complete Backup Finished!"
log_info "  File: ${BACKUP_FILE}"
log_info "  Size: ${BACKUP_SIZE_MB} MB"
log_info "  Local: ${BACKUP_DIR}/${BACKUP_FILE}"
log_info "  S3: ${S3_BUCKET}/${BACKUP_FILE}"
log_info "  Components: milvus, etcd, minio"
log_info "=========================================="
