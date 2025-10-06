"""
AsyncIO Example: Task Management

This file demonstrates how to manage asyncio tasks including:
- Checking task status
- Canceling tasks
- Waiting for tasks with timeouts
- Getting task results
"""

import asyncio

async def long_running_task(task_id, duration):
    """
    A task that runs for a specified duration unless canceled.
    """
    try:
        print(f"Task {task_id}: Starting...")
        await asyncio.sleep(duration)
        print(f"Task {task_id}: Completed after {duration}s")
        return f"Result from task {task_id}"
    except asyncio.CancelledError:
        print(f"Task {task_id}: Was cancelled!")
        raise  # Reraise to properly handle cancellation


async def main():
    """
    Demonstrates various task management techniques
    """
    print("=== Creating and checking task status ===")
    
    # Create a task
    task = asyncio.create_task(
        long_running_task("A", 3),
        name="Long Running Task A"
    )
    
    # Check task status before it completes
    print(f"Task name: {task.get_name()}")
    print(f"Task done: {task.done()}")  # False, because it's still running
    print(f"Task cancelled: {task.cancelled()}")  # False, because it wasn't cancelled
    
    # Wait a bit to let the task progress
    await asyncio.sleep(0.5)
    print(f"Task done after 0.5s: {task.done()}")
    print(f"Task cancelled: {task.cancelled()}")
    
    # Now wait for the task to complete
    result = await task
    print(f"Task result: {result}")
    print(f"Task done after completion: {task.done()}")
    print(f"Task cancelled after completion: {task.cancelled()}\n")
    
    print("=== Cancelling tasks ===")
    
    # Create another task
    cancellable_task = asyncio.create_task(
        long_running_task("B", 5),
        name="Cancellable Task B"
    )
    
    # Wait a bit
    await asyncio.sleep(1)
    
    # Check status before cancellation
    print(f"Before cancellation - Done: {cancellable_task.done()}, Cancelled: {cancellable_task.cancelled()}")
    
    # Cancel the task
    cancellable_task.cancel()
    print(f"After cancellation call - Done: {cancellable_task.done()}, Cancelled: {cancellable_task.cancelled()}")
    
    try:
        # When we await a cancelled task, it raises CancelledError
        result = await cancellable_task
        print(f"This won't print: {result}")
    except asyncio.CancelledError:
        print("Caught CancelledError as expected\n")
    
    print("=== Using asyncio.wait_for() with timeout ===")
    
    # Create a task that takes longer than our timeout
    slow_task = asyncio.create_task(
        long_running_task("C", 4),
        name="Slow Task C"
    )
    
    try:
        # Wait for the task with a timeout of 2 seconds
        result = await asyncio.wait_for(slow_task, timeout=2.0)
        print(f"This won't print: {result}")
    except asyncio.TimeoutError:
        print("Timeout occurred - task took longer than 2 seconds")
        print(f"Task still running: {not slow_task.done()}")
        
        # The task is still running in the background, so we should cancel it
        if not slow_task.done():
            slow_task.cancel()
            try:
                await slow_task  # Wait for cancellation to complete
            except asyncio.CancelledError:
                print("Slow task was properly cancelled\n")
    
    print("=== Waiting for multiple tasks with conditions ===")
    
    # Create multiple tasks with different durations
    task1 = asyncio.create_task(long_running_task("Fast", 1))
    task2 = asyncio.create_task(long_running_task("Medium", 2))
    task3 = asyncio.create_task(long_running_task("Slow", 3))
    
    # Wait for ANY task to complete
    done, pending = await asyncio.wait([task1, task2, task3], 
                                       return_when=asyncio.FIRST_COMPLETED)
    print(f"First task completed. Done: {len(done)}, Pending: {len(pending)}")
    
    # Cancel remaining tasks since we got our first result
    for pending_task in pending:
        pending_task.cancel()
        try:
            await pending_task
        except asyncio.CancelledError:
            print("Cancelled remaining task")
    
    print("\n=== Task results and exception handling ===")
    
    async def task_with_error():
        await asyncio.sleep(1)
        raise ValueError("Something went wrong in the task!")
    
    # Create a task that will raise an exception
    error_task = asyncio.create_task(task_with_error())
    
    try:
        result = await error_task
        print(f"This won't print: {result}")
    except ValueError as e:
        print(f"Caught exception from task: {e}")


if __name__ == "__main__":
    print("Demonstrating task management techniques...\n")
    asyncio.run(main())
    print("\nProgram completed!")