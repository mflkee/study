# AsyncIO Learning Path

This repository contains a structured learning path for mastering Python's asyncio library.
Each topic builds on the previous one, providing both theoretical knowledge and practical examples.

## Directory Structure

```
topic_1_basic_async/          # Basic async/await concepts
├── 01_hello_async.py         # Introduction to async/await
├── 02_async_await_basics.py  # Core async/await patterns
├── 03_running_coroutines.py  # Different ways to run coroutines
└── 04_async_vs_sync_comparison.py  # Performance comparison

topic_2_concurrent_execution/  # Concurrent execution patterns
├── 01_asyncio_gather.py      # Running multiple tasks concurrently
├── 02_task_creation.py       # Creating and managing tasks
├── 03_task_management.py     # Advanced task management
└── 04_as_completed.py        # Processing results as they complete

topic_3_asyncio_utilities/    # Advanced asyncio utilities
├── 01_event_loop.py          # Working with event loops
├── 02_async_context_managers.py  # Async context managers (async with)
├── 03_async_generators.py    # Async generators (async def with yield)
└── 04_async_iterators.py     # Async iterators (async for)

topic_4_error_handling/       # Error handling in async code
├── 01_exception_handling.py  # Handling exceptions in async code
├── 02_timeout_handling.py    # Timeout management
└── 03_cancelling_tasks.py    # Task cancellation

topic_5_practical_applications/  # Real-world async applications
├── 01_web_scraping.py        # Concurrent web scraping
├── 02_database_operations.py # Async database operations
├── 03_api_calls.py           # Concurrent API calls
└── 04_asyncio_with_threads.py # Combining asyncio with threads
```

## How to Use This Learning Path

1. **Start with Topic 1**: Begin with basic async/await concepts to understand the fundamentals.

2. **Progress Sequentially**: Each topic builds on the previous one, so it's recommended to go in order.

3. **Run the Examples**: Each Python file is executable. Run them to see the concepts in action:
   ```bash
   python topic_1_basic_async/01_hello_async.py
   ```

4. **Read the Comments**: Each file contains detailed comments explaining how and why things work.

5. **Experiment**: Modify the examples to see how changes affect behavior.

## Prerequisites

Before working through these examples, ensure you have:
- Python 3.7+ (some examples may require newer versions)
- For practical applications, you may need additional packages:

```bash
pip install aiohttp aiosqlite requests Pillow
```

## Key Concepts Covered

- **Coroutines and Tasks**: Understanding the core building blocks of asyncio
- **Concurrent Execution**: Running multiple operations simultaneously
- **Async Context Managers**: Proper resource management
- **Async Iteration**: Working with asynchronous sequences
- **Error Handling**: Managing exceptions in async code
- **Timeouts and Cancellation**: Controlling operation duration
- **Integration**: Combining asyncio with threads, databases, and APIs

## Learning Approach

Each file in this repository follows this pattern:
1. **Clear Purpose**: The filename and header explain what the example demonstrates
2. **Detailed Comments**: Inline comments explain each operation
3. **Practical Example**: Realistic use cases showing actual applications
4. **Runnable Code**: All examples can be run independently

## Tips for Learning

1. **Start Simple**: Begin with basic examples before moving to complex ones
2. **Run Code**: Execute each example to see the output and behavior
3. **Modify Examples**: Change parameters and observe the results
4. **Understand the Event Loop**: Pay special attention to how the event loop works
5. **Practice Error Handling**: Good error handling is crucial in async programming

## Next Steps

After completing these examples, consider exploring:
- Async frameworks like aiohttp for web development
- Async database libraries like databases or asyncpg
- Async testing with pytest-asyncio
- Production patterns and best practices