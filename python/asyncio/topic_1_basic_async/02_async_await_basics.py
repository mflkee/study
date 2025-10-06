"""
AsyncIO Example: Async/Await Basics

This file demonstrates the fundamental concepts of async/await syntax:
- What coroutines are
- How to define them
- How to call them correctly
- The difference between calling an async function vs awaiting it
"""

import asyncio

async def fetch_data():
    """
    Simulate fetching data from an external source (like an API).
    This is a coroutine that represents an asynchronous operation.
    """
    print("Starting to fetch data...")
    await asyncio.sleep(2)  # Simulate network delay
    print("Data fetched successfully!")
    return {"data": "Important data", "timestamp": "2023-01-01"}


async def process_data():
    """
    Simulate processing data asynchronously.
    """
    print("Starting data processing...")
    await asyncio.sleep(1)  # Simulate processing time
    print("Data processed!")
    return "processed_result"


async def main():
    """
    Main coroutine that orchestrates other coroutines.
    This function shows the correct way to call and await coroutines.
    """
    print("=== Example 1: Calling a coroutine vs awaiting it ===")
    
    # If we just call fetch_data() without await, we get a coroutine object
    # This doesn't actually run the function - it just creates a coroutine object
    coroutine_obj = fetch_data()
    print(f"fetch_data() returns: {coroutine_obj}")
    print(f"Type: {type(coroutine_obj)}")
    
    # Close the coroutine object to avoid warnings
    coroutine_obj.close()
    
    print("\n=== Example 2: Actually running a coroutine with await ===")
    
    # To actually run the coroutine, we need to await it
    result = await fetch_data()
    print(f"Result from fetch_data: {result}")
    
    print("\n=== Example 3: Multiple sequential operations ===")
    
    # Operations will happen sequentially (one after another)
    data_result = await fetch_data()
    process_result = await process_data()
    
    print(f"Final results - Data: {data_result}, Process: {process_result}")


if __name__ == "__main__":
    print("Running main async function...")
    asyncio.run(main())
    print("Program completed!")