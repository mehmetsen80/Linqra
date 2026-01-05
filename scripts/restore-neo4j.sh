#!/bin/bash
#
# Neo4j Restore Script
# Restores Neo4j graph database from a local backup or S3
#
# Usage: ./restore-neo4j.sh <environment> <backup-file>
#

set -e  # Exit on error

# Parse arguments
ENVIRONMENT="${1:-}"
BACKUP_FILE="${2:-}"

if [ -z "$ENVIRONMENT" ] || [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <environment> <backup-file>"
    echo "  environment: dev or ec2"
    echo "  backup-file: Name of the backup file (e.g., neo4j-backup-20241211-023000.tar.gz)"
    echo ""
    echo "Examples:"
    echo "  $0 dev neo4j-backup-20241211-023000.tar.gz    # Restore from local or S3"
    echo "  $0 ec2 neo4j-backup-20241211-023000.tar.gz"
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

# Check if Neo4j container exists
if ! docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_error "Neo4j container '${CONTAINER_NAME}' does not exist!"
    exit 1
fi

# Warning
log_warn "=========================================="
log_warn "WARNING: This will REPLACE all Neo4j data!"
log_warn "Container: ${CONTAINER_NAME}"
log_warn "Backup: ${BACKUP_FILE}"
log_warn "=========================================="
echo ""
read -p "Are you sure you want to continue? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    log_info "Restore cancelled."
    exit 0
fi

# Stop Neo4j container
log_info "Stopping Neo4j container..."
docker stop ${CONTAINER_NAME} || true

# Copy backup to container
log_info "Copying backup to container..."
docker cp ${LOCAL_BACKUP} ${CONTAINER_NAME}:/tmp/${BACKUP_FILE}

# Start container temporarily to restore
log_info "Starting container for restore..."
docker start ${CONTAINER_NAME}
sleep 5

# Clear existing data and restore
log_info "Restoring Neo4j data..."
docker exec ${CONTAINER_NAME} sh -c "rm -rf ${NEO4J_DATA_PATH}/* && tar -xzf /tmp/${BACKUP_FILE} -C ${NEO4J_DATA_PATH}"

# Clean up
log_info "Cleaning up..."
docker exec ${CONTAINER_NAME} rm -f /tmp/${BACKUP_FILE}

# Restart container to apply restored data
log_info "Restarting Neo4j container..."
docker restart ${CONTAINER_NAME}

# Wait for Neo4j to be ready
log_info "Waiting for Neo4j to be ready..."
sleep 15

# Check health
if docker exec ${CONTAINER_NAME} wget --quiet --tries=1 --spider http://localhost:7474/ 2>/dev/null; then
    log_info "Neo4j is healthy!"
else
    log_warn "Neo4j health check failed - may still be starting up"
fi

# Summary
log_info "=========================================="
log_info "Neo4j Restore Completed!"
log_info "  Backup: ${BACKUP_FILE}"
log_info "  Container: ${CONTAINER_NAME}"
log_info "=========================================="
