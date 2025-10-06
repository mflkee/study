"""
AsyncIO Example: asyncio.as_completed()

This file demonstrates asyncio.as_completed(), which returns an iterator 
that yields coroutines/tasks as they complete, in the order they finish.
"""

import asyncio
import random

async def download_file(filename, max_delay):
    """
    Simulates downloading a file with a random delay up to max_delay seconds.
    """
    delay = random.uniform(0.5, max_delay)  # Random delay between 0.5 and max_delay
    print(f"Starting download of {filename} (will take ~{delay:.1f}s)...")
    await asyncio.sleep(delay)
    print(f"✓ Completed download of {filename}")
    return f"{filename} content (downloaded in {delay:.1f}s)"


async def main():
    """
    Demonstrates using asyncio.as_completed()
    """
    print("=== Using asyncio.as_completed() ===")
    
    # Create coroutines with different expected completion times
    downloads = [
        download_file("large_file.zip", 3.0),
        download_file("medium_file.pdf", 2.0), 
        download_file("small_file.txt", 1.0),
        download_file("image.jpg", 1.5),
        download_file("document.docx", 2.5)
    ]
    
    print("Waiting for downloads to complete (in order of completion)...\n")
    
    # as_completed returns an iterator that yields results as each coroutine finishes
    # This is different from gather() which waits for all to complete
    results = []
    for completed_coro in asyncio.as_completed(downloads):
        result = await completed_coro  # Wait for the next coroutine to complete
        results.append(result)
        print(f"Received result: {result}")
    
    print(f"\nAll downloads completed in completion order: {len(results)} total")
    
    print("\n=== Processing results as they arrive ===")
    
    # Another way to use as_completed - process results immediately as they finish
    downloads2 = [
        download_file("file_A.bin", 2.0),
        download_file("file_B.bin", 1.0),
        download_file("file_C.bin", 3.0)
    ]
    
    completed_count = 0
    # Process each result as it becomes available
    for completed_coro in asyncio.as_completed(downloads2, timeout=5.0):
        try:
            result = await completed_coro
            completed_count += 1
            print(f"Progress: {completed_count}/3 completed - {result}")
        except asyncio.TimeoutError:
            print("Timeout waiting for next completed task")
            break
    
    print("\n=== Comparing with gather() (completes in original order) ===")
    
    # For comparison, here's how gather() works (maintains original order)
    downloads3 = [
        download_file("gather1.txt", 3.0),
        download_file("gather2.txt", 1.0), 
        download_file("gather3.txt", 2.0)
    ]
    
    print("Using asyncio.gather() - will maintain original order:")
    gathered_results = await asyncio.gather(*downloads3)
    print("Gather results:")
    for i, result in enumerate(gathered_results):
        print(f"  {i+1}. {result}")


if __name__ == "__main__":
    print("Demonstrating asyncio.as_completed()...\n")
    start_time = asyncio.get_event_loop().time()
    asyncio.run(main())
    end_time = asyncio.get_event_loop().time()
    print(f"\nTotal execution time: {end_time - start_time:.2f} seconds")