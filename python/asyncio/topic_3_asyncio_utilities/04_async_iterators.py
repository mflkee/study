"""
AsyncIO Example: Async Iterators

This file demonstrates async iterators using 'async for' and classes that 
implement __aiter__ and __anext__ methods. Async iterators are useful for
traversing collections or streams of data asynchronously.
"""

import asyncio
import random


class AsyncCounter:
    """
    An async iterator that counts from 1 to a specified maximum value.
    Implements __aiter__ and __anext__ to make it an async iterator.
    """
    def __init__(self, max_count):
        self.max_count = max_count
        self.current = 0
    
    def __aiter__(self):
        """
        Returns the async iterator object (usually self).
        This method is called by 'async for' to get the iterator.
        """
        print(f"Initializing async counter to count to {self.max_count}")
        return self
    
    async def __anext__(self):
        """
        Returns the next item in the iteration.
        This method is called by 'async for' to get the next value.
        """
        if self.current >= self.max_count:
            # Stop the iteration by raising StopAsyncIteration
            raise StopAsyncIteration
        
        self.current += 1
        print(f"Counting: {self.current}")
        await asyncio.sleep(0.5)  # Simulate async work
        return self.current


class AsyncDataStream:
    """
    An async iterator that simulates streaming data from an external source.
    """
    def __init__(self, data_source):
        self.data_source = data_source
        self.index = 0
    
    def __aiter__(self):
        return self
    
    async def __anext__(self):
        # Check if we've reached the end of the data
        if self.index >= len(self.data_source):
            raise StopAsyncIteration
        
        # Get the current item
        item = self.data_source[self.index]
        self.index += 1
        
        # Simulate async fetching of the item
        print(f"Fetching data: {item}")
        await asyncio.sleep(0.2)  # Simulate network delay
        
        return item


class AsyncRandomStream:
    """
    An async iterator that yields random numbers indefinitely.
    """
    def __init__(self, max_value=100, count=None):
        self.max_value = max_value
        self.count = count  # If None, stream indefinitely
        self.current = 0
    
    def __aiter__(self):
        return self
    
    async def __anext__(self):
        if self.count is not None and self.current >= self.count:
            raise StopAsyncIteration
        
        self.current += 1
        # Generate a random number
        random_num = random.randint(1, self.max_value)
        print(f"Generated random number: {random_num}")
        await asyncio.sleep(0.3)  # Simulate async work
        return random_num


async def main():
    """
    Demonstrates usage of async iterators
    """
    print("=== Using custom async iterator: AsyncCounter ===")
    
    # Using 'async for' with our custom async iterator
    async for count in AsyncCounter(3):
        print(f"Received count: {count}")
    
    print("\n=== Using async iterator: AsyncDataStream ===")
    
    sample_data = ["data1", "data2", "data3", "data4"]
    async for item in AsyncDataStream(sample_data):
        print(f"Processed item: {item.upper()}")
    
    print("\n=== Using async iterator: AsyncRandomStream ===")
    
    # Limit to 3 items for this example
    async for random_num in AsyncRandomStream(count=3):
        print(f"Working with random number: {random_num}")


class AsyncFileIterator:
    """
    An async iterator that reads lines from a file asynchronously.
    This is a more practical example of async iteration.
    """
    def __init__(self, lines):
        self.lines = lines
        self.index = 0
    
    def __aiter__(self):
        return self
    
    async def __anext__(self):
        if self.index >= len(self.lines):
            raise StopAsyncIteration
        
        line = self.lines[self.index]
        self.index += 1
        
        # Simulate async file I/O
        await asyncio.sleep(0.1)
        print(f"Read line: {line.strip()}")
        return line


async def demo_file_iteration():
    """
    Demonstrates a more practical async iterator example.
    """
    print("\n=== Async file-like iteration ===")
    
    # Simulate file content
    file_lines = [
        "First line of the file\n",
        "Second line of the file\n", 
        "Third line of the file\n",
        "End of file\n"
    ]
    
    async for line in AsyncFileIterator(file_lines):
        # Process each line
        processed_line = f"PROCESSED: {line.strip()}"
        print(f"Processing: {processed_line}")


# Async iterator with filtering
class FilteredAsyncIterator:
    """
    An async iterator that filters items based on a condition.
    """
    def __init__(self, source_iterator, filter_func):
        self.source_iterator = source_iterator
        self.filter_func = filter_func
        self.iterator = None
    
    def __aiter__(self):
        self.iterator = aiter(self.source_iterator)  # Get the source iterator
        return self
    
    async def __anext__(self):
        while True:
            try:
                # Get the next item from the source
                item = await anext(self.iterator)
                
                # Apply the filter
                if self.filter_func(item):
                    return item
            except StopAsyncIteration:
                # If the source is exhausted, we're also exhausted
                raise


async def demo_filtered_iteration():
    """
    Demonstrates filtered async iteration.
    """
    print("\n=== Filtered async iteration ===")
    
    # Create a random stream and filter for even numbers
    random_stream = AsyncRandomStream(count=10)
    
    async def is_even(num):
        return num % 2 == 0
    
    filtered_stream = FilteredAsyncIterator(random_stream, is_even)
    
    even_count = 0
    async for even_num in filtered_stream:
        print(f"Found even number: {even_num}")
        even_count += 1
        
        # Just show first 3 even numbers
        if even_count >= 3:
            print("(stopping after 3 even numbers)")
            break


async def demo_breaking_iteration():
    """
    Shows how to break out of async iteration early.
    """
    print("\n=== Breaking out of async iteration ===")
    
    async for count in AsyncCounter(10):  # Counts to 10
        print(f"Current count: {count}")
        
        # Stop early when we reach 5
        if count >= 5:
            print("Breaking early!")
            break


if __name__ == "__main__":
    print("Demonstrating async iterators...\n")
    asyncio.run(main())
    asyncio.run(demo_file_iteration())
    asyncio.run(demo_filtered_iteration())
    asyncio.run(demo_breaking_iteration())
    print("\nProgram completed!")