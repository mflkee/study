"""
AsyncIO Example: Async Context Managers

This file demonstrates async context managers using 'async with' statement.
Async context managers are useful for resource management in asynchronous code
like database connections, file handles, network connections, etc.
"""

import asyncio


class AsyncDatabaseConnection:
    """
    An example of an async context manager for a database connection.
    """
    def __init__(self, db_name):
        self.db_name = db_name
        self.connected = False
    
    async def __aenter__(self):
        """
        Called when entering the 'async with' block.
        Similar to __enter__ in synchronous context managers.
        """
        print(f"Connecting to database: {self.db_name}")
        await asyncio.sleep(0.1)  # Simulate connection time
        self.connected = True
        print(f"Connected to {self.db_name}")
        return self  # Value returned to the 'as' variable
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """
        Called when exiting the 'async with' block.
        Similar to __exit__ in synchronous context managers.
        Handles cleanup regardless of whether an exception occurred.
        """
        print(f"Disconnecting from {self.db_name}")
        await asyncio.sleep(0.1)  # Simulate disconnection time
        self.connected = False
        print(f"Disconnected from {self.db_name}")
        
        # If an exception was raised, log it
        if exc_type:
            print(f"Exception occurred: {exc_type.__name__}: {exc_val}")
        
        # Return False to propagate exceptions (or True to suppress them)
        return False  # Don't suppress exceptions
    
    async def query(self, sql):
        """
        Simulate a database query.
        """
        if not self.connected:
            raise RuntimeError("Not connected to database!")
        
        print(f"Executing query: {sql}")
        await asyncio.sleep(0.2)  # Simulate query execution time
        return f"Results for: {sql}"


class AsyncFileHandler:
    """
    Another example: async file handler context manager.
    """
    def __init__(self, filename, mode):
        self.filename = filename
        self.mode = mode
        self.file_opened = False
    
    async def __aenter__(self):
        print(f"Opening file: {self.filename}")
        # Simulate async file opening
        await asyncio.sleep(0.05)
        self.file_opened = True
        print(f"File {self.filename} opened")
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        print(f"Closing file: {self.filename}")
        # Simulate async file closing
        await asyncio.sleep(0.05)
        self.file_opened = False
        print(f"File {self.filename} closed")
        
        if exc_type:
            print(f"Exception in file handling: {exc_type.__name__}: {exc_val}")
        
        return False  # Don't suppress exceptions
    
    async def write(self, content):
        if not self.file_opened:
            raise RuntimeError("File not opened!")
        
        print(f"Writing to file: {content[:20]}...")
        await asyncio.sleep(0.05)  # Simulate write time
        return len(content)


async def main():
    """
    Demonstrates usage of async context managers
    """
    print("=== Using async context manager: Database Connection ===")
    
    # Using async with - the connection will be automatically closed
    async with AsyncDatabaseConnection("myapp_db") as db:
        print("Inside the async with block")
        result = await db.query("SELECT * FROM users LIMIT 5")
        print(f"Query result: {result}")
        
        # Connection will be closed automatically when exiting the block
        print("Still inside the block...")
    
    print("Outside the async with block - connection is closed\n")
    
    print("=== Using async context manager: File Handler ===")
    
    # Using async with for file handling
    async with AsyncFileHandler("example.txt", "w") as file:
        print("Inside file async with block")
        size = await file.write("This is some example content for the async file.")
        print(f"Wrote {size} characters")
        print("Still inside the file block...")
    
    print("Outside file async with block - file is closed\n")
    
    print("=== Async context manager with exception handling ===")
    
    # Even if an exception occurs, the context manager still cleans up
    try:
        async with AsyncDatabaseConnection("test_db") as db:
            result = await db.query("SELECT * FROM users")
            print(f"Query result: {result}")
            # Raise an exception to see cleanup still happens
            raise ValueError("Something went wrong!")
        
    except ValueError as e:
        print(f"Caught exception: {e}")
    
    print("Notice that the database was still properly disconnected!\n")
    
    print("=== Multiple async context managers ===")
    
    # Using multiple async context managers
    async with AsyncDatabaseConnection("primary_db") as db, \
               AsyncFileHandler("log.txt", "a") as log_file:
        
        db_result = await db.query("SELECT COUNT(*) FROM items")
        print(f"DB result: {db_result}")
        
        await log_file.write(f"Query executed: SELECT COUNT(*) FROM items")
        print("Both resources are properly managed!")


# We can also create async context managers using asynccontextmanager decorator
from contextlib import asynccontextmanager

@asynccontextmanager
async def temporary_connection(host, port):
    """
    An async context manager created with the decorator approach.
    """
    print(f"Establishing temporary connection to {host}:{port}")
    await asyncio.sleep(0.1)  # Simulate connection
    conn = f"connection_to_{host}:{port}"
    try:
        yield conn  # This is what gets assigned to the 'as' variable
    finally:
        print(f"Closing temporary connection to {host}:{port}")
        await asyncio.sleep(0.05)  # Simulate cleanup


async def demo_decorator_approach():
    """
    Demonstrate the decorator approach to async context managers.
    """
    print("\n=== Using decorator-based async context manager ===")
    async with temporary_connection("localhost", 5432) as conn:
        print(f"Using connection: {conn}")
        await asyncio.sleep(0.1)
        print("Doing work with the connection...")
    
    print("Connection was automatically cleaned up!")


if __name__ == "__main__":
    print("Demonstrating async context managers...\n")
    asyncio.run(main())
    asyncio.run(demo_decorator_approach())
    print("\nProgram completed!")