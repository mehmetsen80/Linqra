#!/bin/bash
#
# PostgreSQL Restore Script
# Restores PostgreSQL database (Keycloak) from S3 backup
#
# Usage: 
#   ./restore-postgres.sh --env <environment>              # List available backups
#   ./restore-postgres.sh --env <environment> <filename>  # Restore specific backup
#
# Examples:
#   ./restore-postgres.sh --env dev
#   ./restore-postgres.sh --env ec2 postgres-backup-20241211-120000.sql.gz
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
            echo "Usage: $0 --env <environment> [backup-filename]"
            echo ""
            echo "Options:"
            echo "  --env, -e    Environment (dev or ec2)"
            echo "  --help, -h   Show this help"
            echo ""
            echo "Examples:"
            echo "  $0 --env dev                                    # List backups"
            echo "  $0 --env ec2 postgres-backup-xxx.sql.gz         # Restore specific backup"
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
    echo "  $0 --env dev                                    # List backups"
    echo "  $0 --env ec2 postgres-backup-xxx.sql.gz         # Restore specific backup"
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
        log_error "Unknown environment: ${ENVIRONMENT}. Use 'dev' or 'ec2'"
        exit 1
        ;;
esac

# Configuration
BACKUP_DIR="${LINQRA_HOME}/.kube/postgres/backups"
S3_REGION="us-east-1"

# Auto-detect container name
CONTAINER_NAME=$(docker ps --format '{{.Names}}' | grep "postgres-service" | head -n 1)
if [ -z "$CONTAINER_NAME" ]; then
    CONTAINER_NAME="postgres-service" # Fallback
fi

# PostgreSQL connection
POSTGRES_USER="${POSTGRES_USER:-keycloak}"
POSTGRES_DB="${POSTGRES_DB:-keycloak}"

log_info "Environment: ${ENVIRONMENT}"
log_info "S3 Bucket: ${S3_BUCKET}"
log_info "Local backup dir: ${BACKUP_DIR}"
log_info "Database: ${POSTGRES_DB}"

# Check if PostgreSQL container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_error "PostgreSQL container '${CONTAINER_NAME}' is not running!"
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
        ls -lh ${BACKUP_DIR}/*.sql.gz 2>/dev/null | tail -10 | while read line; do
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
    echo "  $0 --env ${ENVIRONMENT} postgres-backup-20241211-120000.sql.gz"
    exit 0
fi

# Confirm restore
echo ""
echo -e "${RED}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║                    ⚠️  WARNING ⚠️                           ║${NC}"
echo -e "${RED}║  This will RESTORE PostgreSQL (Keycloak) from backup.      ║${NC}"
echo -e "${RED}║  Existing data WILL BE OVERWRITTEN!                        ║${NC}"
echo -e "${RED}║  Keycloak users, realms, and settings will be replaced.    ║${NC}"
echo -e "${RED}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "Environment: ${ENVIRONMENT}"
echo "Backup file: ${BACKUP_FILE}"
echo "Database: ${POSTGRES_DB}"
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
docker cp ${LOCAL_BACKUP} ${CONTAINER_NAME}:/tmp/${BACKUP_FILE}

# Verify copy
if ! docker exec ${CONTAINER_NAME} test -f /tmp/${BACKUP_FILE}; then
    log_error "Failed to copy backup into container!"
    exit 1
fi

# Stop Keycloak before restore (recommended)
echo ""
read -p "Stop Keycloak service before restore? (recommended) (y/n): " stop_keycloak

if [ "$stop_keycloak" = "y" ] || [ "$stop_keycloak" = "Y" ]; then
    log_info "Stopping Keycloak service..."
    docker stop keycloak-service 2>/dev/null || log_warn "Keycloak service not running or couldn't be stopped"
    RESTART_KEYCLOAK=true
else
    log_warn "Continuing without stopping Keycloak - this may cause issues"
    RESTART_KEYCLOAK=false
fi

# Restore using psql
log_info "Starting PostgreSQL restore..."
log_warn "This may take a while depending on backup size..."

# Drop and recreate database
log_info "Dropping existing database connections..."
docker exec ${CONTAINER_NAME} psql -U ${POSTGRES_USER} -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${POSTGRES_DB}' AND pid <> pg_backend_pid();" 2>/dev/null || true

log_info "Dropping database ${POSTGRES_DB}..."
docker exec ${CONTAINER_NAME} dropdb -U ${POSTGRES_USER} --if-exists ${POSTGRES_DB} 2>&1 | while read line; do
    log_info "dropdb: $line"
done

log_info "Creating database ${POSTGRES_DB}..."
docker exec ${CONTAINER_NAME} createdb -U ${POSTGRES_USER} ${POSTGRES_DB} 2>&1 | while read line; do
    log_info "createdb: $line"
done

log_info "Restoring data..."
docker exec ${CONTAINER_NAME} bash -c "gunzip -c /tmp/${BACKUP_FILE} | psql -U ${POSTGRES_USER} ${POSTGRES_DB}" 2>&1 | while read line; do
    # Filter out noise
    if [[ ! "$line" =~ ^(SET|CREATE|ALTER|COMMENT) ]]; then
        log_info "psql: $line"
    fi
done

# Clean up backup inside container
log_info "Cleaning up..."
docker exec ${CONTAINER_NAME} rm -f /tmp/${BACKUP_FILE}

# Restart Keycloak if we stopped it
if [ "$RESTART_KEYCLOAK" = true ]; then
    log_info "Restarting Keycloak service..."
    docker start keycloak-service 2>/dev/null || log_warn "Failed to restart Keycloak"
fi

# Summary
log_info "=========================================="
log_info "PostgreSQL Restore Completed Successfully!"
log_info "  Environment: ${ENVIRONMENT}"
log_info "  Backup: ${BACKUP_FILE}"
log_info "  Database: ${POSTGRES_DB}"
if [ "$RESTART_KEYCLOAK" = true ]; then
    log_info "  Keycloak: Restarted"
fi
log_info "=========================================="
log_info ""
log_info "NOTE: You may need to restart Keycloak for changes to take effect:"
log_info "  docker restart keycloak-service"
