"""
AsyncIO Example: Async vs Sync Comparison

This file demonstrates the performance difference between synchronous and 
asynchronous code by simulating I/O-bound tasks like network requests.
"""

import asyncio
import time

# Synchronous version
def sync_fetch_data(task_id, delay):
    """
    Simulates a synchronous I/O operation.
    The thread is blocked during the sleep period.
    """
    print(f"Sync task {task_id}: Starting...")
    time.sleep(delay)  # Blocks the entire thread
    print(f"Sync task {task_id}: Completed after {delay}s!")
    return f"Result from sync task {task_id}"


def run_sync_tasks():
    """
    Run multiple synchronous tasks sequentially.
    Each task must complete before the next one starts.
    """
    print("=== Running Synchronous Tasks ===")
    start_time = time.time()
    
    # Run 3 tasks sequentially (one after another)
    results = []
    results.append(sync_fetch_data(1, 1))
    results.append(sync_fetch_data(2, 2))
    results.append(sync_fetch_data(3, 1))
    
    end_time = time.time()
    print(f"Synchronous execution completed in {end_time - start_time:.2f} seconds")
    print(f"Results: {results}\n")
    
    return results


# Asynchronous version
async def async_fetch_data(task_id, delay):
    """
    Simulates an asynchronous I/O operation.
    Other coroutines can run during the sleep period.
    """
    print(f"Async task {task_id}: Starting...")
    await asyncio.sleep(delay)  # Doesn't block other coroutines
    print(f"Async task {task_id}: Completed after {delay}s!")
    return f"Result from async task {task_id}"


async def run_async_tasks():
    """
    Run multiple asynchronous tasks concurrently.
    All tasks start at once and run in parallel.
    """
    print("=== Running Asynchronous Tasks ===")
    start_time = time.time()
    
    # Run 3 tasks concurrently (all start at once)
    # Using gather to wait for all tasks to complete
    results = await asyncio.gather(
        async_fetch_data(1, 1),
        async_fetch_data(2, 2),
        async_fetch_data(3, 1)
    )
    
    end_time = time.time()
    print(f"Asynchronous execution completed in {end_time - start_time:.2f} seconds")
    print(f"Results: {results}\n")
    
    return results


async def main():
    """
    Compare synchronous vs asynchronous execution
    """
    print("Demonstrating the difference between sync and async execution:\n")
    
    # Run sync version
    sync_results = run_sync_tasks()
    
    # Run async version
    async_results = await run_async_tasks()
    
    print("=== Key Differences ===")
    print("1. Synchronous execution: Tasks run sequentially, blocking the thread")
    print("2. Asynchronous execution: Tasks run concurrently, no blocking")
    print("3. For I/O-bound operations, async is much more efficient")
    print("4. In this example, sync takes ~4 seconds, async takes ~2 seconds")


if __name__ == "__main__":
    print("Comparing Sync vs Async execution...")
    asyncio.run(main())
    print("Program completed!")