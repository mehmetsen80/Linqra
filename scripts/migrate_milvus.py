import os
import sys
import time
from pymilvus import (
    connections,
    utility,
    Collection,
    FieldSchema,
    CollectionSchema,
    DataType,
    MilvusClient
)

# Configuration
# Source (Local on EC2)
SOURCE_HOST = os.getenv("SOURCE_HOST", "localhost")
SOURCE_PORT = os.getenv("SOURCE_PORT", "19530")
SOURCE_USER = os.getenv("SOURCE_USER", "milvus_admin") 
SOURCE_PASSWORD = os.getenv("SOURCE_PASSWORD", "M!lvus@D1pme$Prod")

# Target (Zilliz Cloud) - passed via ENV or hardcoded by user
TARGET_URI = os.getenv("TARGET_URI", "") 
TARGET_TOKEN = os.getenv("TARGET_TOKEN", "")

def connect_to_milvus():
    print(f"🔌 Connecting to Source Milvus ({SOURCE_HOST}:{SOURCE_PORT})...")
    try:
        connections.connect(
            alias="source",
            host=SOURCE_HOST,
            port=SOURCE_PORT,
            user=SOURCE_USER,
            password=SOURCE_PASSWORD
        )
        print("✅ Connected to Source.")
    except Exception as e:
        print(f"❌ Failed to connect to Source: {e}")
        sys.exit(1)

    print(f"🔌 Connecting to Target Milvus (Cloud)...")
    if not TARGET_URI or not TARGET_TOKEN:
        print("❌ TARGET_URI and TARGET_TOKEN must be set.")
        sys.exit(1)
        
    try:
        connections.connect(
            alias="target",
            uri=TARGET_URI,
            token=TARGET_TOKEN
        )
        print("✅ Connected to Target.")
    except Exception as e:
        print(f"❌ Failed to connect to Target: {e}")
        sys.exit(1)

def get_collections(alias):
    try:
        colls = utility.list_collections(using=alias)
        return colls
    except Exception as e:
        print(f"❌ Failed to list collections from {alias}: {e}")
        return []

def migrate_collection(coll_name):
    print(f"\n📦 Migrating collection: {coll_name}")
    
    # 1. Get Source Schema & Data
    try:
        source_coll = Collection(coll_name, using="source")
        source_coll.load()
        
        # Get row count
        count = source_coll.num_entities
        print(f"   📊 Source Row Count: {count}")
        
        if count == 0:
            print("   ⚠️  Collection is empty, creating schema only.")
            
        schema = source_coll.schema
        
        # 2. Create Target Collection
        if utility.has_collection(coll_name, using="target"):
            print(f"   ⚠️  Collection {coll_name} already exists in Target. Appending data...")
            target_coll = Collection(coll_name, using="target")
        else:
            print(f"   🔨 Creating collection {coll_name} in Target...")
            target_coll = Collection(name=coll_name, schema=schema, using="target")
            
            # Create Index (Copy from source, or default to AutoIndex for Zilliz)
            # Zilliz AutoIndex usually handles it, but let's try to copy index if possible
            for index in source_coll.indexes:
                print(f"   indexing {index.field_name}...")
                # Simplified index creation - Zilliz usually prefers AUTOINDEX
                try:
                    target_coll.create_index(
                        field_name=index.field_name, 
                        index_params=index.params,
                        using="target"
                    )
                except Exception as ie:
                    print(f"   ⚠️  Could not copy index for {index.field_name}, Zilliz might auto-index: {ie}")

        # 3. Transfer Data
        if count > 0:
            BATCH_SIZE = 1000
            offset = 0
            
            # Simple iterator through primary keys would be ideal, but for now specific to typical RAG
            # We assume small enough dataset to query or use iterator
            try:
                # Use query iterator if available in this pymilvus version, else limit/offset
                query_iterator = source_coll.query_iterator(batch_size=BATCH_SIZE, output_fields=["*"])
                
                total_inserted = 0
                while True:
                    res = query_iterator.next()
                    if not res:
                        break
                    
                    target_coll.insert(res)
                    total_inserted += len(res)
                    print(f"   🚀 Inserted batch of {len(res)} (Total: {total_inserted}/{count})")
                    
            except Exception as e:
                print(f"   ❌ Failed to transfer data: {e}")
                
            # Flush target
            target_coll.flush()
            print("   ✅ Data transfer complete & flushed.")
            
    except Exception as e:
        print(f"   ❌ Error migrating {coll_name}: {e}")

def main():
    connect_to_milvus()
    
    collections = get_collections("source")
    print(f"Found {len(collections)} collections in Source: {collections}")
    
    for coll in collections:
        migrate_collection(coll)
        
    print("\n✨ Migration Script Finished.")

if __name__ == "__main__":
    main()
