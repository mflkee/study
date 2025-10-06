"""
AsyncIO Example: Task Creation

This file demonstrates different ways to create and manage asyncio tasks,
including asyncio.create_task(), asyncio.Task(), and their behavior.
"""

import asyncio

async def download_file(filename, download_time):
    """
    Simulates downloading a file with a specific duration.
    """
    print(f"Starting download of {filename}...")
    await asyncio.sleep(download_time)  # Simulate download time
    print(f"Finished downloading {filename}")
    return f"{filename} content"


async def main():
    """
    Demonstrates different task creation methods
    """
    print("=== Method 1: Using asyncio.create_task() ===")
    
    # asyncio.create_task() schedules the coroutine to run soon
    # It returns a Task object that can be awaited or canceled
    task1 = asyncio.create_task(download_file("file1.txt", 2))
    task2 = asyncio.create_task(download_file("file2.txt", 1))
    
    print("Both tasks created and scheduled to run concurrently")
    
    # Wait for both tasks to complete
    result1, result2 = await asyncio.gather(task1, task2)
    print(f"Download results: {result1}, {result2}\n")
    
    print("=== Method 2: Direct coroutine passing vs Task objects ===")
    
    # When you pass coroutines directly to gather(), they become tasks internally
    start_time = asyncio.get_event_loop().time()
    results = await asyncio.gather(
        download_file("direct1.txt", 1),
        download_file("direct2.txt", 1)
    )
    end_time = asyncio.get_event_loop().time()
    print(f"Gather with direct coroutines took {end_time - start_time:.2f}s")
    print(f"Results: {results}\n")
    
    print("=== Method 3: Creating tasks with custom names ===")
    
    # You can name tasks for better debugging and monitoring
    named_task1 = asyncio.create_task(
        download_file("important_file.zip", 1),
        name="Download Important File"
    )
    named_task2 = asyncio.create_task(
        download_file("backup.tar", 2),
        name="Download Backup"
    )
    
    print(f"Task 1 name: {named_task1.get_name()}")
    print(f"Task 2 name: {named_task2.get_name()}")
    
    await named_task1
    await named_task2
    print("Named tasks completed\n")
    
    print("=== Method 4: Task creation without immediately awaiting ===")
    
    # Create tasks and store them to manage later
    tasks = []
    for i in range(3):
        task = asyncio.create_task(
            download_file(f"file_{i}.dat", 1),
            name=f"Download file_{i}"
        )
        tasks.append(task)
        print(f"Created and scheduled task: {task.get_name()}")
    
    # Now wait for all tasks to complete
    results = await asyncio.gather(*tasks)
    print(f"All delayed tasks completed: {results}")


if __name__ == "__main__":
    print("Demonstrating different task creation methods...\n")
    asyncio.run(main())
    print("\nProgram completed!")