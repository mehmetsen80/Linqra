#!/bin/bash
#
# MongoDB Restore Script
# Restores MongoDB from S3 backup
#
# Usage: 
#   ./restore-mongodb.sh                     # Interactive - lists available backups
#   ./restore-mongodb.sh <backup-filename>   # Restore specific backup
#   ./restore-mongodb.sh --env dev           # Use dev environment
#   ./restore-mongodb.sh --env ec2           # Use ec2/production environment
#   ./restore-mongodb.sh --env ec2 mongodb-backup-20241211-120000.gz
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
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

# Parse arguments
ENVIRONMENT=""
BACKUP_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --env|-e)
            ENVIRONMENT="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [--env dev|ec2] [backup-filename]"
            echo ""
            echo "Options:"
            echo "  --env, -e    Environment (dev or ec2)"
            echo "  --help, -h   Show this help"
            echo ""
            echo "Examples:"
            echo "  $0                                    # Interactive mode"
            echo "  $0 --env dev                          # List dev backups"
            echo "  $0 --env ec2 mongodb-backup-xxx.gz    # Restore specific backup"
            exit 0
            ;;
        *)
            BACKUP_FILE="$1"
            shift
            ;;
    esac
done

# Check environment is specified
if [ -z "$ENVIRONMENT" ]; then
    log_error "Environment is required. Use --env dev or --env ec2"
    echo ""
    echo "Usage: $0 --env <environment> [backup-filename]"
    echo ""
    echo "Examples:"
    echo "  $0 --env dev                          # List dev backups"
    echo "  $0 --env ec2 mongodb-backup-xxx.gz    # Restore specific backup"
    exit 1
fi

# Environment-specific configuration
case $ENVIRONMENT in
    dev)
        S3_BUCKET="s3://backup-linqra-mongodb-dev"
        LINQRA_HOME="/Users/mehmetsen/IdeaProjects/Linqra"
        MONGO_USER="${MONGO_INITDB_ROOT_USERNAME:-root}"
        MONGO_PASS="${MONGO_INITDB_ROOT_PASSWORD:-mongopw}"
        ;;
    ec2)
        S3_BUCKET="s3://backup-linqra-mongodb"
        LINQRA_HOME="/var/www/linqra"
        MONGO_USER="${MONGO_INITDB_ROOT_USERNAME:-root}"
        MONGO_PASS="${MONGO_INITDB_ROOT_PASSWORD:-mongopw}"
        ;;
    *)
        log_error "Unknown environment: ${ENVIRONMENT}. Use 'dev' or 'ec2'"
        exit 1
        ;;
esac

BACKUP_DIR="${LINQRA_HOME}/.kube/mongodb/backups"
S3_REGION="us-east-1"
CONTAINER_NAME="mongodb1"
MONGO_URI="mongodb://${MONGO_USER}:${MONGO_PASS}@localhost:27017/?authSource=admin&replicaSet=rs0"

log_info "Environment: ${ENVIRONMENT}"
log_info "S3 Bucket: ${S3_BUCKET}"
log_info "Local backup dir: ${BACKUP_DIR}"

# Check if MongoDB container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_error "MongoDB container '${CONTAINER_NAME}' is not running!"
    exit 1
fi

# List available backups if no file specified
if [ -z "$BACKUP_FILE" ]; then
    echo ""
    echo -e "${CYAN}=== Available S3 Backups ===${NC}"
    aws s3 ls ${S3_BUCKET}/ --region ${S3_REGION} 2>/dev/null | tail -20 | while read line; do
        size=$(echo $line | awk '{print $3}')
        file=$(echo $line | awk '{print $4}')
        date=$(echo $line | awk '{print $1" "$2}')
        echo -e "  ${GREEN}${file}${NC} (${size} bytes, ${date})"
    done
    
    echo ""
    echo -e "${CYAN}=== Available Local Backups ===${NC}"
    if [ -d "${BACKUP_DIR}" ]; then
        ls -lh ${BACKUP_DIR}/*.gz 2>/dev/null | tail -10 | while read line; do
            echo "  $line"
        done
    else
        echo "  (no local backups found)"
    fi
    
    echo ""
    echo -e "${YELLOW}To restore, run:${NC}"
    echo "  $0 --env ${ENVIRONMENT} <backup-filename>"
    echo ""
    echo -e "${YELLOW}Example:${NC}"
    echo "  $0 --env ${ENVIRONMENT} mongodb-backup-20241211-120000.gz"
    exit 0
fi

# Confirm restore
echo ""
echo -e "${RED}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║                    ⚠️  WARNING ⚠️                           ║${NC}"
echo -e "${RED}║  This will RESTORE MongoDB from backup.                    ║${NC}"
echo -e "${RED}║  Existing data may be OVERWRITTEN!                         ║${NC}"
echo -e "${RED}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "Environment: ${ENVIRONMENT}"
echo "Backup file: ${BACKUP_FILE}"
echo "S3 Bucket: ${S3_BUCKET}"
echo ""
read -p "Are you sure you want to continue? (type 'yes' to confirm): " confirm

if [ "$confirm" != "yes" ]; then
    log_warn "Restore cancelled by user"
    exit 1
fi

# Check if backup exists locally first
LOCAL_BACKUP="${BACKUP_DIR}/${BACKUP_FILE}"
if [ -f "${LOCAL_BACKUP}" ]; then
    log_info "Using local backup: ${LOCAL_BACKUP}"
else
    # Download from S3
    log_info "Downloading backup from S3..."
    mkdir -p ${BACKUP_DIR}
    if ! aws s3 cp ${S3_BUCKET}/${BACKUP_FILE} ${LOCAL_BACKUP} --region ${S3_REGION}; then
        log_error "Failed to download backup from S3!"
        log_error "Check if file exists: aws s3 ls ${S3_BUCKET}/${BACKUP_FILE}"
        exit 1
    fi
    log_info "Downloaded: ${LOCAL_BACKUP}"
fi

# Copy backup into container
log_info "Copying backup to container..."
docker cp ${LOCAL_BACKUP} ${CONTAINER_NAME}:/data/db/${BACKUP_FILE}

# Verify copy
if ! docker exec ${CONTAINER_NAME} test -f /data/db/${BACKUP_FILE}; then
    log_error "Failed to copy backup into container!"
    exit 1
fi

# Restore using mongorestore
log_info "Starting MongoDB restore..."
log_warn "This may take a while depending on backup size..."

# Ask about drop behavior
echo ""
read -p "Drop existing collections before restore? (y/n): " drop_collections

RESTORE_OPTS="--uri=${MONGO_URI} --archive=/data/db/${BACKUP_FILE} --gzip"
if [ "$drop_collections" = "y" ] || [ "$drop_collections" = "Y" ]; then
    RESTORE_OPTS="${RESTORE_OPTS} --drop"
    log_warn "Will DROP existing collections before restore"
else
    log_info "Will MERGE with existing data (may cause duplicates)"
fi

# Run restore
docker exec ${CONTAINER_NAME} mongorestore ${RESTORE_OPTS} 2>&1 | while read line; do
    log_info "mongorestore: $line"
done

# Clean up backup inside container
log_info "Cleaning up..."
docker exec ${CONTAINER_NAME} rm -f /data/db/${BACKUP_FILE}

# Summary
log_info "=========================================="
log_info "MongoDB Restore Completed Successfully!"
log_info "  Environment: ${ENVIRONMENT}"
log_info "  Backup: ${BACKUP_FILE}"
log_info "=========================================="
