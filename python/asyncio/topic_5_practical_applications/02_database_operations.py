"""
AsyncIO Example: Database Operations

This file demonstrates async database operations using aiosqlite.
It shows how to perform concurrent database operations efficiently.
"""

import asyncio
import aiosqlite
import time
import random


async def create_sample_database(db_path):
    """
    Create a sample database with a users table for our examples.
    """
    async with aiosqlite.connect(db_path) as db:
        await db.execute('''
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        
        # Insert some sample data if table is empty
        cursor = await db.execute("SELECT COUNT(*) FROM users")
        count = await cursor.fetchone()
        
        if count[0] == 0:
            sample_users = [
                ("Alice Johnson", "alice@example.com"),
                ("Bob Smith", "bob@example.com"),
                ("Charlie Brown", "charlie@example.com"),
                ("Diana Prince", "diana@example.com"),
                ("Eve Wilson", "eve@example.com")
            ]
            
            await db.executemany(
                "INSERT INTO users (name, email) VALUES (?, ?)",
                sample_users
            )
            await db.commit()


async def fetch_user(db_path, user_id):
    """
    Fetch a user by ID from the database.
    """
    print(f"Fetching user with ID: {user_id}")
    
    async with aiosqlite.connect(db_path) as db:
        async with db.execute("SELECT * FROM users WHERE id = ?", (user_id,)) as cursor:
            user = await cursor.fetchone()
            
            if user:
                print(f"✓ Found user: ID={user[0]}, Name={user[1]}, Email={user[2]}")
                return user
            else:
                print(f"✗ User with ID {user_id} not found")
                return None


async def insert_user(db_path, name, email):
    """
    Insert a new user into the database.
    """
    print(f"Inserting user: {name} ({email})")
    
    # Simulate some processing time
    await asyncio.sleep(0.1)
    
    async with aiosqlite.connect(db_path) as db:
        try:
            await db.execute(
                "INSERT INTO users (name, email) VALUES (?, ?)",
                (name, email)
            )
            await db.commit()
            
            # Get the ID of the inserted user
            async with db.execute("SELECT id FROM users WHERE email = ?", (email,)) as cursor:
                new_id = await cursor.fetchone()
                
            print(f"✓ User {name} inserted with ID: {new_id[0]}")
            return new_id[0]
        except Exception as e:
            print(f"✗ Error inserting user {name}: {e}")
            return None


async def update_user_email(db_path, user_id, new_email):
    """
    Update a user's email in the database.
    """
    print(f"Updating user {user_id} email to: {new_email}")
    
    async with aiosqlite.connect(db_path) as db:
        cursor = await db.execute(
            "UPDATE users SET email = ? WHERE id = ?",
            (new_email, user_id)
        )
        await db.commit()
        
        if cursor.rowcount > 0:
            print(f"✓ User {user_id} email updated to {new_email}")
            return True
        else:
            print(f"✗ User {user_id} not found for update")
            return False


async def basic_database_operations():
    """
    Basic example of concurrent database operations.
    """
    print("=== Basic concurrent database operations ===")
    
    db_path = "sample.db"
    await create_sample_database(db_path)
    
    # Perform multiple database operations concurrently
    operations = [
        fetch_user(db_path, 1),
        fetch_user(db_path, 3),
        fetch_user(db_path, 5),
        insert_user(db_path, "Frank Miller", "frank@example.com"),
        insert_user(db_path, "Grace Lee", "grace@example.com")
    ]
    
    start_time = time.time()
    results = await asyncio.gather(*operations)
    end_time = time.time()
    
    print(f"\nCompleted {len(results)} operations in {end_time - start_time:.2f} seconds")
    
    # Print results
    for i, result in enumerate(results):
        print(f"  Operation {i+1}: {'Success' if result is not None else 'Failed'}")


async def database_transaction_example():
    """
    Example showing database transactions with async/await.
    """
    print("\n=== Database transactions example ===")
    
    db_path = "sample.db"
    
    async def transfer_points(from_user_id, to_user_id, points):
        """
        Simulate transferring points between users in a transaction.
        """
        print(f"Transferring {points} points from user {from_user_id} to user {to_user_id}")
        
        async with aiosqlite.connect(db_path) as db:
            try:
                # Start transaction (by default, each statement is auto-committed,
                # but we can control this by not committing until the end)
                await db.execute("UPDATE users SET id = id WHERE id = ?", (from_user_id,))
                
                # Check if from_user has enough points (in a real app, we'd have a points column)
                # For this example, we'll just simulate the transfer
                await asyncio.sleep(0.1)  # Simulate processing time
                
                # Actually perform the "transfer"
                print(f"✓ Transferred {points} points from user {from_user_id} to user {to_user_id}")
                
                # Commit the transaction
                await db.commit()
                return True
            except Exception as e:
                # Rollback the transaction in case of error
                await db.rollback()
                print(f"✗ Transfer failed, rolled back: {e}")
                return False
    
    # Perform multiple transfers concurrently
    transfers = [
        transfer_points(1, 2, 10),
        transfer_points(3, 4, 5),
        transfer_points(2, 5, 8)
    ]
    
    results = await asyncio.gather(*transfers)
    print(f"Transfer results: {results}")


async def batch_database_operations():
    """
    Example of batch database operations for better performance.
    """
    print("\n=== Batch database operations ===")
    
    db_path = "sample.db"
    
    # Function to insert multiple users in a batch
    async def insert_users_batch(users_data):
        print(f"Inserting {len(users_data)} users in batch...")
        
        async with aiosqlite.connect(db_path) as db:
            try:
                await db.executemany(
                    "INSERT INTO users (name, email) VALUES (?, ?)",
                    users_data
                )
                await db.commit()
                print(f"✓ Batch insert of {len(users_data)} users completed")
                return len(users_data)
            except Exception as e:
                print(f"✗ Batch insert failed: {e}")
                return 0
    
    # Prepare batch data
    batch_users = [
        (f"Batch User {i}", f"batchuser{i}@example.com") for i in range(10)
    ]
    
    # Run batch insert with some individual operations
    batch_task = insert_users_batch(batch_users)
    individual_tasks = [
        fetch_user(db_path, 1),
        fetch_user(db_path, 2)
    ]
    
    # Execute batch and individual operations concurrently
    results = await asyncio.gather(batch_task, *individual_tasks)
    
    batch_result = results[0]
    individual_results = results[1:]
    
    print(f"Batch operation result: {batch_result} users inserted")
    print(f"Individual operations: {len(individual_results)} completed")


async def database_pool_simulation():
    """
    Simulate using a database connection pool (aiosqlite doesn't have built-in pooling,
    but this shows the concept).
    """
    print("\n=== Database connection management ===")
    
    db_path = "sample.db"
    
    # Simulate a connection pool with asyncio.Semaphore
    connection_pool = asyncio.Semaphore(3)  # Max 3 concurrent connections
    
    async def operation_with_connection_pool(user_id):
        """
        Perform database operation using "pooled" connection.
        """
        async with connection_pool:
            print(f"Acquired connection for user {user_id}")
            
            # Simulate using the connection
            await asyncio.sleep(0.2)  # Simulate query time
            
            # Perform the actual database operation
            async with aiosqlite.connect(db_path) as db:
                async with db.execute("SELECT name FROM users WHERE id = ?", (user_id,)) as cursor:
                    result = await cursor.fetchone()
            
            print(f"Released connection for user {user_id}")
            return result[0] if result else None
    
    # Execute multiple operations that would use connection pool
    user_ids = [1, 2, 3, 4, 5, 6]
    results = await asyncio.gather(*[
        operation_with_connection_pool(uid) for uid in user_ids
    ])
    
    print("Results from connection-pooled operations:")
    for i, result in enumerate(results):
        print(f"  User ID {user_ids[i]}: {result}")


async def error_handling_in_database():
    """
    Example of error handling in database operations.
    """
    print("\n=== Database error handling ===")
    
    db_path = "sample.db"
    
    async def safe_insert_user(name, email):
        """
        Insert user with proper error handling.
        """
        try:
            return await insert_user(db_path, name, email)
        except aiosqlite.IntegrityError as e:
            print(f"✗ Integrity error (likely duplicate email): {e}")
            return None
        except aiosqlite.Error as e:
            print(f"✗ Database error: {e}")
            return None
        except Exception as e:
            print(f"✗ Unexpected error: {e}")
            return None
    
    # Try to insert users, including one that will cause an error (duplicate email)
    operations = [
        safe_insert_user("John Doe", "johndoe@example.com"),
        safe_insert_user("Jane Doe", "janedoe@example.com"),
        safe_insert_user("John Doe 2", "johndoe@example.com"),  # This will fail - duplicate email
        safe_insert_user("Alice Cooper", "alicecooper@example.com")
    ]
    
    results = await asyncio.gather(*operations, return_exceptions=True)
    
    print("\nError handling results:")
    for i, result in enumerate(results):
        if isinstance(result, Exception):
            print(f"  Operation {i+1}: Exception - {result}")
        else:
            print(f"  Operation {i+1}: {'Success' if result is not None else 'Failed'}")


async def complex_database_workflow():
    """
    A more complex workflow combining multiple database operations.
    """
    print("\n=== Complex database workflow ===")
    
    db_path = "sample.db"
    
    async def user_exists(email):
        """Check if a user exists with the given email."""
        async with aiosqlite.connect(db_path) as db:
            async with db.execute("SELECT 1 FROM users WHERE email = ?", (email,)) as cursor:
                return await cursor.fetchone() is not None
    
    async def get_user_count():
        """Get the total number of users."""
        async with aiosqlite.connect(db_path) as db:
            async with db.execute("SELECT COUNT(*) FROM users") as cursor:
                result = await cursor.fetchone()
                return result[0]
    
    # Workflow steps
    print("Step 1: Check if user exists")
    exists = await user_exists("alice@example.com")
    print(f"User with email 'alice@example.com' exists: {exists}")
    
    print("Step 2: Get user count")
    count = await get_user_count()
    print(f"Total users in database: {count}")
    
    print("Step 3: Concurrent operations")
    # Combine multiple operations
    operations = [
        fetch_user(db_path, 1),
        insert_user(db_path, f"Workflow User {random.randint(100, 999)}", f"workflow{random.randint(100, 999)}@example.com"),
        get_user_count(),
        update_user_email(db_path, 1, f"newalice{random.randint(100, 999)}@example.com")
    ]
    
    results = await asyncio.gather(*operations)
    
    print("Workflow results:")
    print(f"  Fetched user 1: {results[0]}")
    print(f"  Inserted new user with ID: {results[1]}")
    print(f"  Updated user count: {results[2]}")
    print(f"  Email update successful: {results[3]}")


if __name__ == "__main__":
    print("Demonstrating async database operations...\n")
    
    # Note: For this to work, you need to install aiosqlite: pip install aiosqlite
    try:
        asyncio.run(basic_database_operations())
        asyncio.run(database_transaction_example())
        asyncio.run(batch_database_operations())
        asyncio.run(database_pool_simulation())
        asyncio.run(error_handling_in_database())
        asyncio.run(complex_database_workflow())
    except ImportError:
        print("To run this example, please install aiosqlite:")
        print("pip install aiosqlite")
    
    print("\nProgram completed!")