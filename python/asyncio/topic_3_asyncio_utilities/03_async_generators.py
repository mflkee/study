"""
AsyncIO Example: Async Generators

This file demonstrates async generators using 'async def' with 'yield'.
Async generators are useful for producing streams of values asynchronously,
such as processing large datasets, handling streaming APIs, etc.
"""

import asyncio
import random


async def async_counter(max_count):
    """
    An async generator that yields numbers from 1 to max_count with delays.
    Each yield is awaited by the consumer.
    """
    count = 0
    while count < max_count:
        count += 1
        print(f"Generating number: {count}")
        await asyncio.sleep(0.5)  # Simulate async work before yielding
        yield count  # This yield can be awaited by the consumer


async def async_data_stream(data_source):
    """
    An async generator that yields data items from a simulated async source.
    """
    for i, item in enumerate(data_source):
        # Simulate fetching data asynchronously
        await asyncio.sleep(0.2)
        print(f"Fetching data item {i}: {item}")
        yield item


async def async_range(start, stop, delay=0.1):
    """
    An async generator that works like range() but with async delays.
    """
    current = start
    while current < stop:
        await asyncio.sleep(delay)
        yield current
        current += 1


async def main():
    """
    Demonstrates using async generators
    """
    print("=== Basic async generator usage ===")
    
    # Using async for to iterate over the async generator
    async for number in async_counter(3):
        print(f"Received number: {number}")
    
    print("\n=== Async generator with data processing ===")
    
    sample_data = ["item1", "item2", "item3", "item4"]
    async for item in async_data_stream(sample_data):
        # Process each item as it comes from the generator
        processed = item.upper()
        print(f"Processed: {processed}")
    
    print("\n=== Using async generator like a range ===")
    
    # Using our async range generator
    async for num in async_range(5, 10, delay=0.3):
        print(f"Async range value: {num}")


async def async_generator_with_exception():
    """
    Example of how exceptions are handled in async generators.
    """
    try:
        for i in range(5):
            await asyncio.sleep(0.1)
            if i == 3:
                raise ValueError(f"Error at item {i}")
            yield f"Item {i}"
    except ValueError as e:
        print(f"Exception caught in generator: {e}")
        yield "Error handled item"
    finally:
        print("Generator cleanup")


async def demo_exception_handling():
    """
    Demonstrate exception handling with async generators.
    """
    print("\n=== Async generator with exception handling ===")
    try:
        async for item in async_generator_with_exception():
            print(f"Received: {item}")
    except ValueError as e:
        print(f"Exception propagated to consumer: {e}")


# Advanced example: Producer-Consumer with async generators
async def producer(queue, num_items):
    """
    Produces items and puts them in a queue.
    """
    for i in range(num_items):
        item = f"item_{i}"
        print(f"Producing: {item}")
        await asyncio.sleep(0.1)  # Simulate work
        await queue.put(item)
    await queue.put(None)  # Sentinel to signal completion


async def async_queue_consumer(queue):
    """
    An async generator that consumes from a queue asynchronously.
    """
    while True:
        item = await queue.get()
        if item is None:  # Sentinel value
            break
        print(f"Yielding: {item}")
        yield item
        queue.task_done()


async def demo_producer_consumer():
    """
    Demonstrates a producer-consumer pattern with async generators.
    """
    print("\n=== Producer-Consumer with async generator ===")
    
    # Create a queue for communication
    queue = asyncio.Queue()
    
    # Start producer as a background task
    producer_task = asyncio.create_task(producer(queue, 5))
    
    # Consume using our async generator
    async for item in async_queue_consumer(queue):
        print(f"Consuming: {item}")
        await asyncio.sleep(0.2)  # Simulate processing time
    
    # Wait for producer to complete
    await producer_task
    print("Producer-consumer completed")


# Real-world example: Async pagination
async def fetch_page(page_number):
    """
    Simulates fetching a page of data asynchronously.
    """
    print(f"Fetching page {page_number}")
    await asyncio.sleep(0.3)  # Simulate network delay
    
    # Generate some fake data
    page_data = [f"item_{page_number}_{i}" for i in range(3)]
    return page_data


async def paginated_data_fetcher(total_pages):
    """
    An async generator that fetches paginated data.
    """
    for page_num in range(1, total_pages + 1):
        page_data = await fetch_page(page_num)
        for item in page_data:
            yield item


async def demo_pagination():
    """
    Demonstrate real-world pagination use case.
    """
    print("\n=== Pagination with async generator ===")
    
    count = 0
    async for item in paginated_data_fetcher(3):  # 3 pages
        print(f"Processing: {item}")
        count += 1
        
        # Just process first 5 items as example
        if count >= 5:
            print("(stopping early for demo)")
            break


if __name__ == "__main__":
    print("Demonstrating async generators...\n")
    asyncio.run(main())
    asyncio.run(demo_exception_handling())
    asyncio.run(demo_producer_consumer())
    asyncio.run(demo_pagination())
    print("\nProgram completed!")