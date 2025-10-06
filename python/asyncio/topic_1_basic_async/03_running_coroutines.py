"""
AsyncIO Example: Running Coroutines

This file demonstrates different ways to run coroutines:
- asyncio.run()
- asyncio.create_task()
- asyncio.wait()
- asyncio.gather()
"""

import asyncio

async def task_a():
    """A simple async task that takes 2 seconds"""
    print("Task A: Starting...")
    await asyncio.sleep(2)
    print("Task A: Completed!")
    return "Result from A"


async def task_b():
    """A simple async task that takes 1 second"""
    print("Task B: Starting...")
    await asyncio.sleep(1)
    print("Task B: Completed!")
    return "Result from B"


async def main():
    """
    Demonstrates different ways to run coroutines
    """
    print("=== Method 1: Sequential execution with await ===")
    # This runs task_a first, then task_b (takes ~3 seconds total)
    result_a = await task_a()
    result_b = await task_b()
    print(f"Sequential results - A: {result_a}, B: {result_b}\n")
    
    print("=== Method 2: Concurrent execution with create_task ===")
    # Create Task objects that will run concurrently
    task_a_obj = asyncio.create_task(task_a())  # Schedule task_a to run soon
    task_b_obj = asyncio.create_task(task_b())  # Schedule task_b to run soon
    
    # Now await both tasks (they run in parallel, takes ~2 seconds total)
    result_a, result_b = await asyncio.gather(task_a_obj, task_b_obj)
    print(f"Concurrent results - A: {result_a}, B: {result_b}\n")
    
    print("=== Method 3: Using asyncio.gather directly ===")
    # Run tasks concurrently without explicitly creating Task objects
    results = await asyncio.gather(task_a(), task_b())
    print(f"Gather results: {results}\n")
    
    print("=== Method 4: Using asyncio.wait ===")
    # Create task objects
    task_a_obj = asyncio.create_task(task_a())
    task_b_obj = asyncio.create_task(task_b())
    
    # Wait for both to complete
    done, pending = await asyncio.wait([task_a_obj, task_b_obj], 
                                       return_when=asyncio.ALL_COMPLETED)
    
    # Get results from completed tasks
    results = [task.result() for task in done]
    print(f"Wait results: {results}")


if __name__ == "__main__":
    print("Running main async function...")
    asyncio.run(main())
    print("Program completed!")
