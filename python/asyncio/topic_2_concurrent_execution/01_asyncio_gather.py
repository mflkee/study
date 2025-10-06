"""
AsyncIO Example: asyncio.gather()

This file demonstrates asyncio.gather(), which is used to run multiple 
coroutines concurrently and collect their results in a specific order.
"""

import asyncio

async def fetch_item(item_id, delay):
    """
    Simulate fetching an item with a specific delay.
    """
    print(f"Fetching item {item_id}...")
    await asyncio.sleep(delay)
    print(f"Item {item_id} fetched!")
    return f"Item {item_id} data"


async def main():
    """
    Demonstrates various uses of asyncio.gather()
    """
    print("=== Basic usage of asyncio.gather() ===")
    
    # Run multiple coroutines concurrently and wait for all to complete
    # Results are returned in the same order as the coroutines were passed,
    # regardless of which one finishes first
    results = await asyncio.gather(
        fetch_item("A", 2),  # Takes 2 seconds
        fetch_item("B", 1),  # Takes 1 second  
        fetch_item("C", 3)   # Takes 3 seconds
    )
    
    print(f"All items fetched: {results}")
    print(f"Total time: ~3 seconds (not 6 seconds!) because they ran concurrently\n")
    
    print("=== Using gather() with return_exceptions=True ===")
    
    async def working_task():
        await asyncio.sleep(1)
        return "Success!"
    
    async def failing_task():
        await asyncio.sleep(2)
        raise ValueError("Something went wrong!")
    
    # When return_exceptions=True, exceptions are returned as results
    # instead of stopping the entire gather operation
    results_with_exceptions = await asyncio.gather(
        working_task(),
        failing_task(),
        fetch_item("D", 1),
        return_exceptions=True  # This prevents one exception from stopping everything
    )
    
    for i, result in enumerate(results_with_exceptions):
        if isinstance(result, Exception):
            print(f"Task {i}: Exception occurred - {result}")
        else:
            print(f"Task {i}: {result}")
    
    print("\n=== Gathering with unpacking ===")
    
    # You can also gather tasks created separately
    task1 = fetch_item("X", 1)
    task2 = fetch_item("Y", 1)
    task3 = fetch_item("Z", 1)
    
    results = await asyncio.gather(task1, task2, task3)
    print(f"Unpacked results: {results}")


if __name__ == "__main__":
    start_time = asyncio.get_event_loop().time()
    asyncio.run(main())
    end_time = asyncio.get_event_loop().time()
    print(f"\nTotal execution time: {end_time - start_time:.2f} seconds")