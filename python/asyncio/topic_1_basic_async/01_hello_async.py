"""
AsyncIO Example: Hello Async

This file demonstrates the most basic async/await syntax in Python.
The purpose is to show how to define and run a simple asynchronous function.
"""

import asyncio

# Define an asynchronous function (coroutine)
# The 'async' keyword indicates that this function returns a coroutine object
# rather than executing immediately like a regular function
async def hello_async():
    """
    A simple coroutine that prints a message and waits for 1 second.
    The 'await' keyword is used to pause execution until the awaited coroutine completes.
    """
    print("Hello from the asynchronous function!")
    # asyncio.sleep() is a coroutine that returns a coroutine object
    # it doesn't block the thread, allowing other coroutines to run
    await asyncio.sleep(1)  # Simulate some async work (like I/O)
    print("Hello async is done!")

# To run the coroutine, we need to use asyncio.run() which:
# 1. Creates a new event loop
# 2. Runs the given coroutine in that event loop
# 3. Closes the event loop when done
if __name__ == "__main__":
    print("Starting the async function...")
    asyncio.run(hello_async())  # This starts the event loop and runs the coroutine
    print("Program completed!")
