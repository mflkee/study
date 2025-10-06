"""
AsyncIO Example: Exception Handling

This file demonstrates how to handle exceptions in asynchronous code,
including catching exceptions in coroutines, propagating them properly,
and handling them at different levels.
"""

import asyncio


async def task_that_fails(task_id):
    """
    A task that raises an exception after a delay.
    """
    print(f"Task {task_id}: Starting...")
    await asyncio.sleep(1)
    print(f"Task {task_id}: About to fail...")
    raise ValueError(f"Something went wrong in task {task_id}!")


async def successful_task(task_id):
    """
    A task that completes successfully.
    """
    print(f"Successful task {task_id}: Starting...")
    await asyncio.sleep(0.5)
    print(f"Successful task {task_id}: Completed")
    return f"Success from {task_id}"


async def main_with_exception():
    """
    Demonstrates basic exception handling in async code.
    """
    print("=== Basic exception handling in async functions ===")
    
    try:
        # This will raise an exception
        result = await task_that_fails("A")
        print(f"This won't print: {result}")
    except ValueError as e:
        print(f"Caught exception: {e}")
    
    print("\n=== Exception handling with multiple tasks (using gather) ===")
    
    try:
        # When using gather, if any task raises an exception, 
        # gather will raise that exception
        results = await asyncio.gather(
            successful_task("B"),
            task_that_fails("C"),  # This will fail
            successful_task("D")
        )
        print(f"This won't print: {results}")
    except ValueError as e:
        print(f"Caught exception from gather: {e}")
    
    print("\n=== Handling exceptions individually with return_exceptions=True ===")
    
    # Using return_exceptions=True prevents exceptions from stopping gather
    results = await asyncio.gather(
        successful_task("E"),
        task_that_fails("F"),  # This will fail but not stop everything
        successful_task("G"),
        return_exceptions=True  # Return exceptions as results instead of raising them
    )
    
    for i, result in enumerate(results):
        if isinstance(result, Exception):
            print(f"Task {i}: Exception occurred - {type(result).__name__}: {result}")
        else:
            print(f"Task {i}: Success - {result}")


async def main_with_task_objects():
    """
    Demonstrates exception handling when using task objects.
    """
    print("\n=== Exception handling with Task objects ===")
    
    # Create tasks
    task1 = asyncio.create_task(successful_task("Task-1"))
    task2 = asyncio.create_task(task_that_fails("Task-2"))
    task3 = asyncio.create_task(successful_task("Task-3"))
    
    try:
        # If we await task2 directly, it will raise the exception
        result = await task2
        print(f"This won't print: {result}")
    except ValueError as e:
        print(f"Caught exception from specific task: {e}")
    
    # Wait for other tasks to complete
    try:
        result1 = await task1
        print(f"Task-1 result: {result1}")
    except Exception as e:
        print(f"Task-1 exception: {e}")
    
    try:
        result3 = await task3
        print(f"Task-3 result: {result3}")
    except Exception as e:
        print(f"Task-3 exception: {e}")


class CustomAsyncException(Exception):
    """
    A custom exception for our async application.
    """
    def __init__(self, message, error_code=None):
        super().__init__(message)
        self.error_code = error_code


async def task_with_custom_exception():
    """
    A task that raises our custom exception.
    """
    print("Task with custom exception: Starting...")
    await asyncio.sleep(1)
    raise CustomAsyncException("This is a custom exception", error_code=42)


async def demo_custom_exceptions():
    """
    Demonstrates handling custom exceptions in async code.
    """
    print("\n=== Handling custom exceptions ===")
    
    try:
        await task_with_custom_exception()
    except CustomAsyncException as e:
        print(f"Caught custom exception: {e}")
        print(f"Error code: {e.error_code}")
    except Exception as e:
        print(f"Caught general exception: {e}")


async def nested_exception_example():
    """
    Demonstrates exception propagation through nested async calls.
    """
    
    async def inner_function():
        print("Inner function: About to raise an exception...")
        raise RuntimeError("Error from inner function")
    
    async def middle_function():
        print("Middle function: Calling inner function...")
        await inner_function()  # Exception will propagate through here
        print("Middle function: This won't print")
    
    async def outer_function():
        print("Outer function: Calling middle function...")
        await middle_function()  # Exception will propagate to here
        print("Outer function: This won't print")
    
    print("\n=== Nested exception propagation ===")
    
    try:
        await outer_function()
    except RuntimeError as e:
        print(f"Caught exception that propagated through nested calls: {e}")


async def exception_in_background_task():
    """
    Demonstrates what happens with exceptions in background tasks.
    """
    print("\n=== Exception in background task ===")
    
    async def background_task():
        await asyncio.sleep(0.5)
        raise ValueError("Exception in background task")
    
    # Start a background task without awaiting it directly
    task = asyncio.create_task(background_task())
    
    # Do some other work
    await asyncio.sleep(1)
    
    # The exception is still pending in the task
    # Accessing the task result will raise the exception
    try:
        await task  # This will raise the ValueError from the background task
    except ValueError as e:
        print(f"Caught exception from background task: {e}")


if __name__ == "__main__":
    print("Demonstrating exception handling in async code...\n")
    asyncio.run(main_with_exception())
    asyncio.run(main_with_task_objects())
    asyncio.run(demo_custom_exceptions())
    asyncio.run(nested_exception_example())
    asyncio.run(exception_in_background_task())
    print("\nProgram completed!")