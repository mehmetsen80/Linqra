#!/bin/bash
# scripts/migrate_keycloak_data.sh

# Configuration
# If running ON the source server, leave SOURCE_HOST empty.
SOURCE_HOST="" 
SOURCE_DB_CONTAINER="${SOURCE_DB_CONTAINER:-postgres-service}"
DB_USER="${DB_USER:-keycloak}"
DB_NAME="${DB_NAME:-keycloak}" 

# Target Configuration (Polaris)
TARGET_HOST="${TARGET_HOST:-ec2-44-224-78-183.us-west-2.compute.amazonaws.com}"
TARGET_USER="${TARGET_USER:-ubuntu}"
TARGET_DB_CONTAINER="${TARGET_DB_CONTAINER:-postgres-service}"

BACKUP_FILE="keycloak_backup_$(date +%F_%H-%M).sql"

echo "🚀 Starting Support Migration..."
echo "📍 Source: Local (Docker Container: $SOURCE_DB_CONTAINER)"
echo "📍 Target: $TARGET_HOST (Docker Container: $TARGET_DB_CONTAINER)"

# 1. Dump Data (Locally)
echo "📦 Dumping database '$DB_NAME'..."
docker exec $SOURCE_DB_CONTAINER pg_dump -U $DB_USER $DB_NAME > $BACKUP_FILE

if [ $? -ne 0 ]; then
    echo "❌ Error: Dump failed. Check container name ($SOURCE_DB_CONTAINER) or DB name ($DB_NAME)."
    rm -f $BACKUP_FILE
    exit 1
fi
echo "✅ Dump successful: $BACKUP_FILE"

# 2. Transfer to Target
echo "🚚 Transferring backup to Target..."
scp $BACKUP_FILE $TARGET_USER@$TARGET_HOST:/tmp/$BACKUP_FILE

if [ $? -ne 0 ]; then
    echo "❌ Error: SCP failed. Check SSH keys and Hostname."
    exit 1
fi

# 3. Restore on Target
echo "📥 Restoring database on Target..."
ssh $TARGET_USER@$TARGET_HOST "cat /tmp/$BACKUP_FILE | sudo docker exec -i $TARGET_DB_CONTAINER psql -U $DB_USER $DB_NAME"

if [ $? -ne 0 ]; then
    echo "❌ Error: Restore failed."
    exit 1
fi

echo "✅ Migration Complete! Verify by logging into Keycloak on $TARGET_HOST."
rm -f $BACKUP_FILE
