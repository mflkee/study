"""
AsyncIO Example: AsyncIO with Threads

This file demonstrates how to combine asyncio with threading for CPU-bound
operations, file I/O, or other blocking operations that would otherwise
block the event loop.
"""

import asyncio
import time
import concurrent.futures
import requests  # For sync HTTP example
import threading
from PIL import Image  # For image processing example, need to install Pillow
import io
import hashlib


def cpu_bound_task(n):
    """
    A CPU-bound task that calculates prime numbers up to n.
    This would block the event loop if run directly in async code.
    """
    print(f"Starting CPU-bound task for n={n} in thread {threading.current_thread().name}")
    start_time = time.time()
    
    # Calculate prime numbers up to n
    primes = []
    for num in range(2, n + 1):
        is_prime = True
        for i in range(2, int(num ** 0.5) + 1):
            if num % i == 0:
                is_prime = False
                break
        if is_prime:
            primes.append(num)
    
    elapsed = time.time() - start_time
    print(f"CPU-bound task completed in {elapsed:.2f}s, found {len(primes)} primes")
    return len(primes)


def blocking_io_task(filename, data, delay=0):
    """
    A blocking I/O task that writes data to a file.
    """
    print(f"Starting blocking I/O task for {filename} in thread {threading.current_thread().name}")
    if delay > 0:
        time.sleep(delay)  # Simulate slow I/O
    
    # Write data to file
    with open(filename, 'w') as f:
        f.write(data)
    
    print(f"Blocking I/O task completed for {filename}")
    return f"Written {len(data)} chars to {filename}"


def sync_http_request(url):
    """
    A synchronous HTTP request that would block the event loop.
    """
    print(f"Making sync HTTP request to {url} in thread {threading.current_thread().name}")
    start_time = time.time()
    
    response = requests.get(url)
    
    elapsed = time.time() - start_time
    print(f"Sync HTTP request completed in {elapsed:.2f}s with status {response.status_code}")
    
    return {
        'url': url,
        'status_code': response.status_code,
        'content_length': len(response.content),
        'elapsed': elapsed
    }


async def basic_thread_pool_executor():
    """
    Basic example of using ThreadPoolExecutor with asyncio.
    """
    print("=== Basic ThreadPoolExecutor usage ===")
    
    loop = asyncio.get_event_loop()
    
    # Run CPU-bound tasks in a thread pool
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        start_time = time.time()
        
        # Submit tasks to the thread pool
        tasks = [
            loop.run_in_executor(executor, cpu_bound_task, 100),
            loop.run_in_executor(executor, cpu_bound_task, 150),
            loop.run_in_executor(executor, blocking_io_task, "file1.txt", "Data for file 1", 1),
            loop.run_in_executor(executor, blocking_io_task, "file2.txt", "Data for file 2", 1)
        ]
        
        # Wait for all tasks to complete
        results = await asyncio.gather(*tasks)
        
        end_time = time.time()
        
        print(f"All thread pool tasks completed in {end_time - start_time:.2f} seconds")
        for i, result in enumerate(results):
            print(f"  Task {i+1} result: {result}")


async def async_to_thread_wrapper():
    """
    Using asyncio.to_thread() (available in Python 3.9+) as a more convenient way.
    """
    print("\n=== Using asyncio.to_thread() ===")
    
    start_time = time.time()
    
    # Using asyncio.to_thread() - simpler syntax
    tasks = [
        asyncio.to_thread(cpu_bound_task, 80),
        asyncio.to_thread(blocking_io_task, "async_file.txt", "Data written via asyncio.to_thread", 0.5),
        asyncio.to_thread(sync_http_request, "https://httpbin.org/delay/1")
    ]
    
    results = await asyncio.gather(*tasks)
    
    end_time = time.time()
    
    print(f"All asyncio.to_thread() tasks completed in {end_time - start_time:.2f} seconds")
    for i, result in enumerate(results):
        print(f"  Task {i+1} result: {result}")


async def mixing_async_and_sync_operations():
    """
    Example of mixing async and sync operations efficiently.
    """
    print("\n=== Mixing async and sync operations ===")
    
    start_time = time.time()
    
    # Async operations that can run concurrently
    async def async_api_call():
        print("Starting async operation...")
        await asyncio.sleep(1)  # Simulate async I/O
        print("Async operation completed")
        return "Async result"
    
    # Run async operation concurrently with sync operations in threads
    async_task = asyncio.create_task(async_api_call())
    
    # Sync operations in threads
    sync_results = await asyncio.gather(
        asyncio.to_thread(cpu_bound_task, 50),
        asyncio.to_thread(blocking_io_task, "mixed_file.txt", "Mixed async-sync data", 0.3),
        asyncio.to_thread(sync_http_request, "https://httpbin.org/json")
    )
    
    # Wait for the async task too
    async_result = await async_task
    
    end_time = time.time()
    
    print(f"Mixed operations completed in {end_time - start_time:.2f} seconds")
    print(f"  Async result: {async_result}")
    print(f"  Sync results: {sync_results}")


def image_processing_simulation(image_size, operation):
    """
    Simulate CPU-intensive image processing.
    """
    print(f"Processing image {image_size[0]}x{image_size[1]} with {operation}")
    
    # Simulate creating an image
    start_time = time.time()
    pixels = []
    for y in range(image_size[1]):
        row = []
        for x in range(image_size[0]):
            # Simulate pixel processing
            pixel_value = (x * y) % 256
            row.append(pixel_value)
        pixels.append(row)
    
    # Simulate image operation (like resize, filter, etc.)
    if operation == "resize":
        # Simulate resize operation
        time.sleep(0.5)
        result_size = (image_size[0] // 2, image_size[1] // 2)
        print(f"Resized from {image_size} to {result_size}")
    elif operation == "blur":
        # Simulate blur operation
        time.sleep(0.7)
        print(f"Applied blur to {image_size}")
    elif operation == "convert":
        # Simulate format conversion
        time.sleep(0.3)
        print(f"Converted {image_size} image")
    
    elapsed = time.time() - start_time
    print(f"Image processing completed in {elapsed:.2f}s")
    
    return {
        'original_size': image_size,
        'operation': operation,
        'time_taken': elapsed
    }


async def image_processing_example():
    """
    Example of processing images in threads to avoid blocking the event loop.
    """
    print("\n=== Image processing in threads ===")
    
    start_time = time.time()
    
    # Process multiple images concurrently in threads
    image_tasks = [
        asyncio.to_thread(image_processing_simulation, (1000, 1000), "resize"),
        asyncio.to_thread(image_processing_simulation, (800, 600), "blur"),
        asyncio.to_thread(image_processing_simulation, (1200, 900), "convert"),
        asyncio.to_thread(image_processing_simulation, (640, 480), "resize"),
    ]
    
    results = await asyncio.gather(*image_tasks)
    
    end_time = time.time()
    
    print(f"Image processing completed in {end_time - start_time:.2f} seconds")
    for i, result in enumerate(results):
        print(f"  Image {i+1}: {result['operation']} took {result['time_taken']:.2f}s")


def file_hash_calculator(file_path):
    """
    Calculate hash of a file - a CPU-intensive operation.
    """
    print(f"Calculating hash for {file_path}")
    start_time = time.time()
    
    hash_sha256 = hashlib.sha256()
    with open(file_path, "rb") as f:
        # Read file in chunks to handle large files
        for chunk in iter(lambda: f.read(4096), b""):
            hash_sha256.update(chunk)
    
    elapsed = time.time() - start_time
    hash_result = hash_sha256.hexdigest()
    
    print(f"Hash calculation completed in {elapsed:.2f}s for {file_path}")
    return {
        'file': file_path,
        'hash': hash_result[:16] + "...",  # Truncate for display
        'time': elapsed
    }


async def file_processing_example():
    """
    Example of processing files (CPU-intensive hashing) using threads.
    """
    print("\n=== File processing in threads ===")
    
    # First, create some files to process
    test_files = ["file_a.txt", "file_b.txt", "file_c.txt"]
    for file_name in test_files:
        with open(file_name, "w") as f:
            f.write(f"Test content for {file_name}\n" * 1000)  # Create larger files
    
    start_time = time.time()
    
    # Calculate hashes of multiple files concurrently
    hash_tasks = [
        asyncio.to_thread(file_hash_calculator, file_name)
        for file_name in test_files
    ]
    
    results = await asyncio.gather(*hash_tasks)
    
    end_time = time.time()
    
    print(f"File processing completed in {end_time - start_time:.2f} seconds")
    for result in results:
        print(f"  {result['file']}: Hash={result['hash']} (in {result['time']:.2f}s)")


def complex_data_processing(data_batch):
    """
    Simulate complex data processing that would block the event loop.
    """
    print(f"Processing data batch of {len(data_batch)} items")
    start_time = time.time()
    
    # Simulate complex data processing
    processed = []
    for item in data_batch:
        # Simulate complex calculation
        processed_item = {
            'original': item,
            'processed': item ** 2 + item ** 0.5,
            'checksum': sum(ord(c) for c in str(item)) % 1000
        }
        processed.append(processed_item)
    
    elapsed = time.time() - start_time
    print(f"Data processing completed in {elapsed:.2f}s for {len(data_batch)} items")
    
    return {
        'batch_size': len(data_batch),
        'processed_count': len(processed),
        'time_taken': elapsed
    }


async def data_processing_pipeline():
    """
    Example of a data processing pipeline combining async and thread operations.
    """
    print("\n=== Data processing pipeline ===")
    
    start_time = time.time()
    
    # Define data batches
    data_batches = [
        list(range(100, 110)),
        list(range(200, 210)),
        list(range(300, 310)),
    ]
    
    # Async task: simulate data fetching
    async def fetch_additional_data():
        print("Fetching additional async data...")
        await asyncio.sleep(0.5)  # Simulate async I/O
        print("Additional data fetched")
        return "Additional data from async source"
    
    # Run async data fetch alongside CPU-intensive processing
    fetch_task = asyncio.create_task(fetch_additional_data())
    
    # Process data batches in threads
    processing_tasks = [
        asyncio.to_thread(complex_data_processing, batch)
        for batch in data_batches
    ]
    
    # Wait for both async and thread tasks
    processing_results = await asyncio.gather(*processing_tasks)
    additional_data = await fetch_task
    
    end_time = time.time()
    
    print(f"Pipeline completed in {end_time - start_time:.2f} seconds")
    print(f"  Additional async data: {additional_data}")
    for i, result in enumerate(processing_results):
        print(f"  Batch {i+1}: Processed {result['processed_count']} items in {result['time_taken']:.2f}s")


if __name__ == "__main__":
    print("Demonstrating asyncio with threads...\n")
    
    # Note: For image processing example, install Pillow: pip install Pillow
    try:
        asyncio.run(basic_thread_pool_executor())
        asyncio.run(async_to_thread_wrapper())
        asyncio.run(mixing_async_and_sync_operations())
        asyncio.run(image_processing_example())
        asyncio.run(file_processing_example())
        asyncio.run(data_processing_pipeline())
    except ImportError as e:
        print(f"Missing dependency: {e}")
        print("For full functionality, install required packages:")
        print("pip install requests Pillow")
        print("\nRunning without image processing...")
        # Run examples without image processing
        asyncio.run(basic_thread_pool_executor())
        asyncio.run(async_to_thread_wrapper())
        asyncio.run(mixing_async_and_sync_operations())
        asyncio.run(file_processing_example())
        asyncio.run(data_processing_pipeline())
    
    print("\nProgram completed!")