#!/bin/bash

# Configuration
LOCAL_DATA_DIR="./.kube/mongodb/data1"
KEYFILE="./.kube/mongodb/mongo-keyfile"
ATLAS_URI="$1"

if [ -z "$ATLAS_URI" ]; then
    echo "Usage: ./scripts/migrate_to_atlas.sh <your_atlas_connection_string>"
    exit 1
fi

docker stop mongodb1 mongodb2 mongodb3 temp-mongo-migration 2>/dev/null || true
docker rm -f mongodb1 mongodb2 mongodb3 temp-mongo-migration 2>/dev/null || true

echo "Step 1b: Removing stale lock file..."
# Run a quick alpine container to delete the lock file if it exists, to fix DBPathInUse
docker run --rm -v $(pwd)/$LOCAL_DATA_DIR:/data/db alpine rm -f /data/db/mongod.lock

echo "Step 2: Starting temporary container (Standalone Mode)..."
# Start as standalone to bypass Auth/KeyFile and ReplicaSet requirements
docker run -d \
    --name temp-mongo-migration \
    -v $(pwd)/$LOCAL_DATA_DIR:/data/db \
    mongo:8.0.13

echo "Waiting 15 seconds for MongoDB to Initialize..."
sleep 15

# Check if alive
if [ ! "$(docker ps -q -f name=temp-mongo-migration)" ]; then
    echo "ERROR: Container died. Showing logs:"
    docker logs temp-mongo-migration
    docker rm temp-mongo-migration
    exit 1
fi

echo "Step 3: Dumping data (No Auth)..."
# Dump data directly
docker exec temp-mongo-migration mongodump --archive --gzip > mongo-dump.archive.gz

echo "Step 5: Restoring to Atlas..."
if [ -f "mongo-dump.archive.gz" ]; then
    # Run restore
    if docker run --rm \
        -v $(pwd)/mongo-dump.archive.gz:/dump.gz \
        mongo:8.0.13 \
        mongorestore --uri="$ATLAS_URI" --archive=/dump.gz --gzip --nsInclude="Linqra.*"; then

        echo "Migration Complete!"
        docker stop temp-mongo-migration
        docker rm temp-mongo-migration
        rm mongo-dump.archive.gz
    else
        echo "ERROR: Restore failed (Check your Atlas Firewall/Network Access)."
        echo "The dump file 'mongo-dump.archive.gz' has been SAVED locally so you can try restoring again."
        docker stop temp-mongo-migration
        docker rm temp-mongo-migration
        exit 1
    fi
else
    echo "Failed to create dump file."
    exit 1
fi
