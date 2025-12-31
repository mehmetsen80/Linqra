import os
import sys
from pymilvus import connections, utility

# Target (Zilliz Cloud) - passed via ENV
TARGET_URI = os.getenv("TARGET_URI", "") 
TARGET_TOKEN = os.getenv("TARGET_TOKEN", "")

def main():
    if not TARGET_URI or not TARGET_TOKEN:
        print("❌ TARGET_URI and TARGET_TOKEN must be set.")
        sys.exit(1)

    print(f"🔌 Connecting to Cloud: {TARGET_URI} ...")
    
    try:
        connections.connect(
            alias="default",
            uri=TARGET_URI,
            token=TARGET_TOKEN
        )
        print("✅ Connected.")
    except Exception as e:
        print(f"❌ Failed to connect: {e}")
        sys.exit(1)

    # List Databases
    try:
        print("\n🔍 Checking Databases...")
        dbs = utility.list_database()
        print(f"   Available Databases: {dbs}")
    except Exception as e:
        print(f"   ⚠️  Could not list databases (likely standard/free tier): {e}")
        print("   (Assuming 'default' database only)")

    # List Collections in 'default'
    try:
        print("\n🔍 Checking Collections in 'default' database:")
        colls = utility.list_collections()
        for c in colls:
             print(f"   - {c}")
             try:
                 print(f"     Stats: {utility.get_collection_stats(c)}")
             except:
                 pass
    except Exception as e:
        print(f"   ❌ Failed to list collections in default: {e}")

    # Try listing in 'Linqra' if it wasn't valid above
    print("\n🔍 Attempting to switch to 'Linqra' (if it exists)...")
    try:
        # pymilvus doesn't have a simple 'use' command for global connection, 
        # but we can try listing collections specifying 'using' if we had a connection alias,
        # but utility.list_collections doesn't take db_name in all versions.
        # instead we try to connect with db_name if possible or just use utility.
        
        # NOTE: Zilliz Serverless usually only has 'default'.
        pass 
    except Exception:
        pass

if __name__ == "__main__":
    main()
