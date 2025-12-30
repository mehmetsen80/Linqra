#!/bin/bash

# Configuration
LOCAL_DATA_DIR="./.kube/mongodb/data1"
ATLAS_URI="$1" # Pass Atlas URI as first argument

if [ -z "$ATLAS_URI" ]; then
    echo "Usage: ./migrate_to_atlas.sh <your_atlas_connection_string>"
    echo "Example: ./migrate_to_atlas.sh 'mongodb+srv://user:pass@cluster.mongodb.net/?appName=Linqra'"
    exit 1
fi

echo "Step 1: Starting temporary container to access old data..."
# Run a temporary mongo container mounting the OLD data volume
docker run --rm -d \
    --name temp-mongo-migration \
    -v $(pwd)/$LOCAL_DATA_DIR:/data/db \
    mongo:8.0.13

echo "Waiting for temporary local MongoDB to start..."
sleep 10

echo "Step 2: Dumping data from local volume..."
# Dump data from the container to a local 'dump' directory
docker exec temp-mongo-migration mongodump --archive --gzip > mongo-dump.archive.gz

echo "Step 3: Stopping temporary container..."
docker stop temp-mongo-migration

if [ -f "mongo-dump.archive.gz" ]; then
    echo "Step 4: Restoring data to Atlas..."
    # Use a temporary container to run mongorestore against Atlas
    # We mount the dump file into it
    docker run --rm \
        -v $(pwd)/mongo-dump.archive.gz:/dump.gz \
        mongo:8.0.13 \
        mongorestore --uri="$ATLAS_URI" --archive=/dump.gz --gzip --nsInclude="Linqra.*"

    echo "Migration Complete!"
    rm mongo-dump.archive.gz
else
    echo "Error: Dump file not found. Migration failed."
    exit 1
fi
