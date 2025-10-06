"""
AsyncIO Example: Timeout Handling

This file demonstrates how to handle timeouts in async code using 
asyncio.wait_for(), asyncio.timeout(), and related timeout mechanisms.
"""

import asyncio


async def long_running_task(task_id, duration):
    """
    A task that runs for a specified duration.
    """
    print(f"Task {task_id}: Starting (duration: {duration}s)...")
    await asyncio.sleep(duration)
    print(f"Task {task_id}: Completed after {duration}s")
    return f"Result from {task_id}"


async def slow_network_request(url, delay):
    """
    Simulates a slow network request.
    """
    print(f"Making request to {url}...")
    await asyncio.sleep(delay)  # Simulate network delay
    return f"Response from {url}"


async def main_with_wait_for():
    """
    Demonstrates using asyncio.wait_for() with timeouts.
    """
    print("=== Using asyncio.wait_for() with timeout ===")
    
    try:
        # Wait for the task with a timeout of 1.5 seconds
        # The task takes 2 seconds, so it should timeout
        result = await asyncio.wait_for(
            long_running_task("Timeout-Test", 2.0),
            timeout=1.5
        )
        print(f"This won't print: {result}")
    except asyncio.TimeoutError:
        print("Timeout occurred! Task took longer than 1.5 seconds")
    
    print("\n=== Successful wait_for (task completes within timeout) ===")
    
    try:
        # Wait for a task that completes within the timeout
        result = await asyncio.wait_for(
            long_running_task("Within-Timeout", 0.5),
            timeout=1.0
        )
        print(f"Task completed within timeout: {result}")
    except asyncio.TimeoutError:
        print("This won't print - task completed on time")
    
    print("\n=== Timeout on network request ===")
    
    try:
        # Simulate a slow network request that times out
        result = await asyncio.wait_for(
            slow_network_request("http://slow-api.example.com", 3.0),
            timeout=1.0
        )
        print(f"This won't print: {result}")
    except asyncio.TimeoutError:
        print("Network request timed out after 1 second!")


async def main_with_timeout_context():
    """
    Demonstrates using asyncio.timeout() context manager (Python 3.11+)
    or asyncio.timeout() as a function for older versions.
    """
    print("\n=== Using asyncio.timeout() context manager ===")
    
    # Using the timeout as a context manager
    try:
        async with asyncio.timeout(1.0):  # Timeout after 1 second
            result = await long_running_task("Context-Timeout", 2.0)
            print(f"This won't print: {result}")
    except asyncio.TimeoutError:
        print("Timeout occurred in context manager!")
    
    print("\n=== Successful operation within timeout context ===")
    
    try:
        async with asyncio.timeout(2.0):  # 2 second timeout
            result = await long_running_task("Context-Success", 1.0)
            print(f"Operation completed within timeout: {result}")
    except asyncio.TimeoutError:
        print("This won't print")
    
    print("\n=== Nested timeout operations ===")
    
    try:
        async with asyncio.timeout(3.0):
            print("Outer timeout: 3 seconds")
            
            # Inner timeout should not conflict with outer
            async with asyncio.timeout(4.0):  # This is ignored since outer is shorter
                result = await long_running_task("Nested-Test", 2.0)
                print(f"Nested operation result: {result}")
    except asyncio.TimeoutError:
        print("Timeout occurred in nested context!")


async def timeout_with_cleanup():
    """
    Demonstrates proper cleanup when timeouts occur.
    """
    print("\n=== Timeout with cleanup operations ===")
    
    class AsyncResource:
        def __init__(self, name):
            self.name = name
            self.acquired = False
        
        async def acquire(self):
            print(f"Acquiring resource: {self.name}")
            await asyncio.sleep(0.1)
            self.acquired = True
            print(f"Resource {self.name} acquired")
        
        async def release(self):
            print(f"Releasing resource: {self.name}")
            await asyncio.sleep(0.1)
            self.acquired = False
            print(f"Resource {self.name} released")
    
    resource = AsyncResource("TestResource")
    
    try:
        async with asyncio.timeout(1.0):
            await resource.acquire()
            
            # Simulate operation that takes too long
            await long_running_task("Cleanup-Test", 2.0)
            
            # This cleanup won't happen due to timeout
            await resource.release()
    except asyncio.TimeoutError:
        print("Operation timed out!")
        
        # Clean up resource if it was acquired
        if resource.acquired:
            print("Manually cleaning up acquired resource...")
            await resource.release()


async def timeout_in_concurrent_operations():
    """
    Demonstrates timeouts with concurrent operations.
    """
    print("\n=== Timeouts in concurrent operations ===")
    
    # Create multiple tasks
    tasks = [
        long_running_task("Quick", 0.5),
        long_running_task("Medium", 2.0),
        long_running_task("Slow", 4.0)  # This will timeout
    ]
    
    try:
        # Set a timeout that will affect only the slow task
        results = await asyncio.wait_for(
            asyncio.gather(*tasks),
            timeout=3.0
        )
        print(f"This won't print: {results}")
    except asyncio.TimeoutError:
        print("Concurrent operation timed out!")
        
        # Note: The tasks continue running in the background
        # Cancel them to prevent them from running indefinitely
        for task in asyncio.all_tasks():
            if not task.done():
                task.cancel()


async def timeout_handling_patterns():
    """
    Demonstrates various timeout handling patterns.
    """
    print("\n=== Timeout handling patterns ===")
    
    # Pattern 1: Retry on timeout
    async def operation_with_retry(max_retries=3):
        for attempt in range(max_retries):
            try:
                print(f"Attempt {attempt + 1}")
                result = await asyncio.wait_for(
                    slow_network_request(f"http://api.example.com/data-{attempt}", 2.0),
                    timeout=1.0
                )
                return result
            except asyncio.TimeoutError:
                print(f"Attempt {attempt + 1} timed out")
                if attempt < max_retries - 1:
                    print("Retrying...")
                    await asyncio.sleep(0.5)  # Wait before retry
                else:
                    print("All attempts failed")
                    raise
    
    try:
        result = await operation_with_retry()
        print(f"Operation succeeded: {result}")
    except asyncio.TimeoutError:
        print("Operation ultimately failed after retries")
    
    print("\n--- Default timeout handling ---")
    
    # Pattern 2: Default response on timeout
    async def operation_with_default():
        try:
            result = await asyncio.wait_for(
                slow_network_request("http://slow-api.com", 3.0),
                timeout=1.0
            )
            return result
        except asyncio.TimeoutError:
            print("Returning default response due to timeout")
            return "Default response (timeout occurred)"
    
    result = await operation_with_default()
    print(f"Result with default: {result}")


if __name__ == "__main__":
    print("Demonstrating timeout handling in async code...\n")
    asyncio.run(main_with_wait_for())
    asyncio.run(main_with_timeout_context())
    asyncio.run(timeout_with_cleanup())
    asyncio.run(timeout_in_concurrent_operations())
    asyncio.run(timeout_handling_patterns())
    print("\nProgram completed!")