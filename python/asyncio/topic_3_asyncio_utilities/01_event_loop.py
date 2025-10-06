"""
AsyncIO Example: Event Loop

This file demonstrates how to work with the asyncio event loop,
including getting the current loop, creating custom loops, and
understanding the event loop's role in async programming.
"""

import asyncio
import time


async def sample_task(task_id, delay):
    """
    A simple task for demonstration.
    """
    print(f"Task {task_id} starting...")
    await asyncio.sleep(delay)
    print(f"Task {task_id} completed after {delay}s")
    return f"Result from task {task_id}"


def synchronous_function():
    """
    A synchronous function that we might want to run in an async context.
    """
    print("Running synchronous code...")
    time.sleep(1)  # Blocking sleep
    print("Synchronous code completed")
    return "sync_result"


async def main():
    """
    Demonstrates various event loop operations
    """
    print("=== Getting and inspecting the event loop ===")
    
    # Get the current event loop (the one running this coroutine)
    current_loop = asyncio.get_running_loop()
    print(f"Current event loop: {current_loop}")
    print(f"Loop is running: {current_loop.is_running()}")
    
    print("\n=== Running tasks in the event loop ===")
    
    # Run a single coroutine
    result = await sample_task("Single", 0.5)
    print(f"Single task result: {result}")
    
    print("\n=== Running multiple tasks ===")
    
    # Schedule multiple tasks
    tasks = [
        asyncio.create_task(sample_task("Multi-1", 0.5)),
        asyncio.create_task(sample_task("Multi-2", 1.0)),
        asyncio.create_task(sample_task("Multi-3", 0.3))
    ]
    
    results = await asyncio.gather(*tasks)
    print(f"Multiple task results: {results}")
    
    print("\n=== Running synchronous code in the event loop ===")
    
    # Run synchronous code that would block - this hands control back to the event loop
    # while the synchronous function runs in a thread pool
    sync_result = await asyncio.to_thread(synchronous_function)
    print(f"Synchronous function result: {sync_result}")
    
    print("\n=== Scheduling callbacks ===")
    
    # Schedule a callback to run soon in the event loop
    def callback_example():
        print("Callback executed in event loop")
    
    # Call the callback in 0.5 seconds
    handle = current_loop.call_later(0.5, callback_example)
    await asyncio.sleep(0.6)  # Wait for callback to execute
    
    # Cancel the callback if it hasn't run yet (though it probably has by now)
    if not handle.cancelled():
        handle.cancel()
        print("Cancelled callback (if it was still pending)")


def run_with_custom_loop():
    """
    Example of creating and running a custom event loop
    """
    print("\n=== Running with a custom event loop ===")
    
    # Create a new event loop
    loop = asyncio.new_event_loop()
    try:
        # Set as the current event loop for this thread
        asyncio.set_event_loop(loop)
        
        # Run a coroutine in the custom loop
        result = loop.run_until_complete(sample_task("CustomLoop", 1))
        print(f"Custom loop result: {result}")
        
    finally:
        # Always close the loop when done
        loop.close()
        # Reset to default loop
        asyncio.set_event_loop(None)


def run_until_complete_example():
    """
    Example using run_until_complete to run a coroutine in an explicit loop
    """
    print("\n=== Using run_until_complete ===")
    
    loop = asyncio.new_event_loop()
    try:
        asyncio.set_event_loop(loop)
        
        # Run the main function until complete
        results = loop.run_until_complete(main())
        print("run_until_complete finished")
        
    finally:
        loop.close()
        asyncio.set_event_loop(None)


if __name__ == "__main__":
    print("Demonstrating event loop operations...\n")
    
    # The normal way: asyncio.run() creates and manages the loop for you
    asyncio.run(main())
    
    # Example with custom loop
    run_with_custom_loop()
    
    print("\nProgram completed!")