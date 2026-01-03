#!/bin/bash

# Manual Usage:
#   ./backup-postgres-polaris.sh
#   nohup ./backup-postgres-polaris.sh >> /var/www/linqra/logs/backup.log 2>&1 &

# Configuration
CONTAINER_NAME="postgres-service"
# Fetch credentials from running container environment
DB_USER=$(docker exec $CONTAINER_NAME printenv POSTGRES_USER)
DB_PASS=$(docker exec $CONTAINER_NAME printenv POSTGRES_PASSWORD)
DB_NAME=$(docker exec $CONTAINER_NAME printenv POSTGRES_DB)

# Fallbacks
: ${DB_USER:="keycloak"}
: ${DB_NAME:="keycloak"}

BACKUP_DIR="/var/www/linqra/.kube/postgres/backups"
S3_BUCKET="s3://backup-linqra-postgres"
AWS_REGION="us-east-1"
RETENTION_DAYS=7
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
BACKUP_FILE="postgres-backup-${TIMESTAMP}.sql.gz"
LOG_FILE="/var/www/linqra/logs/backup.log"

# Ensure directories exist
mkdir -p "$BACKUP_DIR"
mkdir -p "$(dirname "$LOG_FILE")"

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log "🔄 Starting PostgreSQL backup for database: $DB_NAME"

# Check if container is running
if ! docker ps | grep -q "$CONTAINER_NAME"; then
    log "❌ Error: Container $CONTAINER_NAME is not running!"
    exit 1
fi

# Check AWS S3 Connection
log "🔍 Verifying AWS connection..."
if ! aws s3 ls "$S3_BUCKET" > /dev/null 2>&1; then
    log "❌ Error: Cannot connect to S3 bucket $S3_BUCKET. Check AWS credentials or IAM role."
    exit 1
fi
log "✅ AWS connection successful."

# Perform Backup (Stream directly from docker exec to gzip on host)
# Note: We assume .pgpass or trust auth is configured, or we pass PGPASSWORD. 
# Since we are root/docker user on host, we can exec without password if pg_hba allows, 
# but usually need env var. We'll try to rely on 'postgres' user trust within container.
log "📦 Dumping database..."
if docker exec -e PGPASSWORD="$DB_PASS" "$CONTAINER_NAME" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_DIR/$BACKUP_FILE"; then
    log "✅ Backup created: $BACKUP_DIR/$BACKUP_FILE"
    
    # Get size
    SIZE=$(du -h "$BACKUP_DIR/$BACKUP_FILE" | cut -f1)
    log "📊 Backup size: $SIZE"

    # Upload to S3
    log "☁️ Uploading to S3 ($S3_BUCKET)..."
    if aws s3 cp "$BACKUP_DIR/$BACKUP_FILE" "$S3_BUCKET/$BACKUP_FILE" --region "$AWS_REGION"; then
        log "✅ Upload successful"
    else
        log "⚠️ Upload failed! Keeping local file."
        # Don't exit, we still have local backup
    fi
else
    log "❌ pg_dump failed!"
    rm -f "$BACKUP_DIR/$BACKUP_FILE"
    exit 1
fi

# Cleanup old local backups
log "🧹 Cleaning up local backups older than $RETENTION_DAYS days..."
find "$BACKUP_DIR" -name "postgres-backup-*.sql.gz" -mtime +$RETENTION_DAYS -exec rm {} \; -exec echo "Deleted: {}" \; >> "$LOG_FILE"

log "🎉 Backup process completed."
