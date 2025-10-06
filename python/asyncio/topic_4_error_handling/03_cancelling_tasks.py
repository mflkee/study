"""
AsyncIO Example: Task Cancellation

This file demonstrates how to cancel asyncio tasks, handle cancellation 
gracefully, and implement proper cleanup when tasks are cancelled.
"""

import asyncio


async def cancellable_task(task_id, duration):
    """
    A task that can be cancelled and demonstrates proper cleanup.
    """
    print(f"Task {task_id}: Starting (will run for {duration}s)...")
    
    try:
        await asyncio.sleep(duration)
        print(f"Task {task_id}: Completed normally")
        return f"Result from {task_id}"
    except asyncio.CancelledError:
        # This exception is raised when the task is cancelled
        print(f"Task {task_id}: Was cancelled!")
        # Perform cleanup here if needed
        raise  # Re-raise to properly indicate the task was cancelled


async def task_with_cleanup_on_cancel(task_id):
    """
    A task that performs cleanup when cancelled.
    """
    resource = f"resource_for_{task_id}"
    print(f"Task {task_id}: Acquired {resource}")
    
    try:
        await asyncio.sleep(5.0)  # Long-running task
        print(f"Task {task_id}: Completed successfully")
        return f"Result from {task_id}"
    except asyncio.CancelledError:
        print(f"Task {task_id}: Cleaning up {resource} due to cancellation")
        # Simulate cleanup
        await asyncio.sleep(0.2)  # Cleanup time
        print(f"Task {task_id}: Cleanup completed")
        raise  # Re-raise CancelledError


async def main_with_basic_cancellation():
    """
    Demonstrates basic task cancellation.
    """
    print("=== Basic task cancellation ===")
    
    # Create a task
    task = asyncio.create_task(
        cancellable_task("Basic-Cancel", 3.0)
    )
    
    # Wait a bit
    await asyncio.sleep(1.0)
    
    # Check if task is done
    print(f"Task done before cancellation: {task.done()}")
    
    # Cancel the task
    print("Cancelling the task...")
    task.cancel()
    
    # Check if task is cancelled
    print(f"Task cancelled: {task.cancelled()}")
    
    try:
        # When we await a cancelled task, it raises CancelledError
        result = await task
        print(f"This won't print: {result}")
    except asyncio.CancelledError:
        print("Caught CancelledError from the task")
    
    print(f"Task done after cancellation: {task.done()}")
    print(f"Task cancelled after awaiting: {task.cancelled()}")


async def main_with_cleanup_cancellation():
    """
    Demonstrates cancellation with cleanup.
    """
    print("\n=== Task cancellation with cleanup ===")
    
    # Create a task that performs cleanup when cancelled
    task = asyncio.create_task(
        task_with_cleanup_on_cancel("Cleanup-Task")
    )
    
    # Wait a bit
    await asyncio.sleep(0.5)
    
    # Cancel the task
    print("Cancelling task that needs cleanup...")
    task.cancel()
    
    try:
        await task
    except asyncio.CancelledError:
        print("Task was properly cancelled with cleanup")


async def cancelling_multiple_tasks():
    """
    Demonstrates cancelling multiple tasks.
    """
    print("\n=== Cancelling multiple tasks ===")
    
    # Create several tasks
    tasks = [
        asyncio.create_task(cancellable_task("Multi-1", 4.0)),
        asyncio.create_task(cancellable_task("Multi-2", 5.0)),
        asyncio.create_task(cancellable_task("Multi-3", 6.0))
    ]
    
    # Wait for a bit
    await asyncio.sleep(1.0)
    
    print("Cancelling all tasks...")
    
    # Cancel all tasks
    for task in tasks:
        if not task.done():
            task.cancel()
    
    # Wait for all tasks to handle cancellation
    results = await asyncio.gather(*tasks, return_exceptions=True)
    
    for i, result in enumerate(results):
        if isinstance(result, asyncio.CancelledError):
            print(f"Task {i+1}: Was cancelled")
        else:
            print(f"Task {i+1}: Result - {result}")


async def conditional_cancellation():
    """
    Demonstrates cancelling tasks based on certain conditions.
    """
    print("\n=== Conditional task cancellation ===")
    
    async def long_task_with_progress(task_id):
        for i in range(10):
            print(f"Task {task_id}: Progress {i}/10")
            await asyncio.sleep(0.5)  # Simulate work
            
            # Check if we should cancel based on some condition
            # For demo purposes, we'll cancel after progress 3
            if i >= 3:
                print(f"Task {task_id}: Cancelling due to condition")
                raise asyncio.CancelledError
        
        return f"Task {task_id} completed all work"
    
    task = asyncio.create_task(long_task_with_progress("Conditional"))
    
    try:
        result = await task
        print(f"This won't print: {result}")
    except asyncio.CancelledError:
        print("Task was cancelled based on internal condition")


async def timeout_then_cancel():
    """
    Demonstrates a task that times out, which leads to cancellation.
    """
    print("\n=== Timeout leading to cancellation ===")
    
    async def slow_operation():
        try:
            await asyncio.sleep(3.0)
            return "Completed"
        except asyncio.CancelledError:
            print("Operation was cancelled due to timeout")
            raise
    
    try:
        # This will time out and cause the task to be cancelled
        result = await asyncio.wait_for(slow_operation(), timeout=1.0)
        print(f"This won't print: {result}")
    except asyncio.TimeoutError:
        print("Operation timed out and was cancelled")
    except asyncio.CancelledError:
        print("Operation was cancelled")


async def gracefully_shutting_down():
    """
    Demonstrates a pattern for gracefully shutting down multiple tasks.
    """
    print("\n=== Graceful shutdown pattern ===")
    
    async def worker(worker_id, work_items):
        """A worker that processes items until cancelled."""
        for i, item in enumerate(work_items):
            print(f"Worker {worker_id}: Processing {item}")
            try:
                # Simulate processing time
                await asyncio.sleep(0.3)
                print(f"Worker {worker_id}: Completed {item}")
            except asyncio.CancelledError:
                print(f"Worker {worker_id}: Cancellation requested during {item}")
                # Process any cleanup here
                print(f"Worker {worker_id}: Cleanup completed, stopping")
                raise
        
        return f"Worker {worker_id} completed all work"
    
    # Start multiple workers
    workers = [
        asyncio.create_task(worker("A", ["item1", "item2", "item3"])),
        asyncio.create_task(worker("B", ["item4", "item5", "item6"])),
        asyncio.create_task(worker("C", ["item7", "item8", "item9"]))
    ]
    
    # Let them work for a while
    await asyncio.sleep(1.0)
    
    print("Shutting down workers...")
    
    # Cancel all workers
    for worker_task in workers:
        if not worker_task.done():
            worker_task.cancel()
    
    # Wait for all workers to finish cancellation
    results = await asyncio.gather(*workers, return_exceptions=True)
    
    for i, result in enumerate(results):
        if isinstance(result, asyncio.CancelledError):
            print(f"Worker {i+1}: Was cancelled gracefully")
        elif isinstance(result, Exception):
            print(f"Worker {i+1}: Error - {result}")
        else:
            print(f"Worker {i+1}: Result - {result}")


async def cancellation_propagation():
    """
    Demonstrates how cancellation propagates through async call chains.
    """
    print("\n=== Cancellation propagation ===")
    
    async def deep_operation():
        print("Deep operation: Starting...")
        try:
            await asyncio.sleep(2.0)
            print("Deep operation: Would return result")
            return "Deep result"
        except asyncio.CancelledError:
            print("Deep operation: Was cancelled")
            raise
    
    async def intermediate_function():
        print("Intermediate: Calling deep operation...")
        result = await deep_operation()
        print(f"Intermediate: Got result - {result}")
        return result
    
    async def top_level_function():
        print("Top level: Calling intermediate function...")
        result = await intermediate_function()
        print(f"Top level: Final result - {result}")
        return result
    
    # Create and cancel the task
    task = asyncio.create_task(top_level_function())
    
    await asyncio.sleep(0.5)  # Let it start
    task.cancel()
    
    try:
        await task
    except asyncio.CancelledError:
        print("Top level function was cancelled (cancellation propagated through all levels)")


if __name__ == "__main__":
    print("Demonstrating task cancellation in async code...\n")
    asyncio.run(main_with_basic_cancellation())
    asyncio.run(main_with_cleanup_cancellation())
    asyncio.run(cancelling_multiple_tasks())
    asyncio.run(conditional_cancellation())
    asyncio.run(timeout_then_cancel())
    asyncio.run(gracefully_shutting_down())
    asyncio.run(cancellation_propagation())
    print("\nProgram completed!")