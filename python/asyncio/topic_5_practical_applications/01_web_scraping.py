"""
AsyncIO Example: Web Scraping

This file demonstrates practical async web scraping using aiohttp.
It shows how to make concurrent HTTP requests efficiently.
"""

import asyncio
import aiohttp
import time


async def fetch_url(session, url, delay=0):
    """
    Fetch a URL asynchronously with an optional delay.
    """
    try:
        # Add optional delay to simulate processing time
        if delay > 0:
            await asyncio.sleep(delay)
        
        print(f"Fetching {url}...")
        async with session.get(url) as response:
            # Read response text
            content = await response.text()
            print(f"✓ Fetched {len(content)} characters from {url}")
            return {
                'url': url,
                'status': response.status,
                'length': len(content)
            }
    except Exception as e:
        print(f"✗ Error fetching {url}: {e}")
        return {
            'url': url,
            'error': str(e)
        }


async def basic_web_scraping():
    """
    Basic example of concurrent web scraping.
    """
    print("=== Basic concurrent web scraping ===")
    
    # URLs to scrape
    urls = [
        'https://httpbin.org/delay/1',
        'https://httpbin.org/delay/2', 
        'https://httpbin.org/delay/1',
        'https://jsonplaceholder.typicode.com/posts/1',
        'https://jsonplaceholder.typicode.com/posts/2'
    ]
    
    # Create an aiohttp session
    async with aiohttp.ClientSession() as session:
        start_time = time.time()
        
        # Fetch all URLs concurrently
        results = await asyncio.gather(*[
            fetch_url(session, url) for url in urls
        ])
        
        end_time = time.time()
        
        print(f"\nCompleted {len(results)} requests in {end_time - start_time:.2f} seconds")
        print("Results:")
        for result in results:
            if 'error' in result:
                print(f"  {result['url']}: ERROR - {result['error']}")
            else:
                print(f"  {result['url']}: {result['status']} - {result['length']} chars")


async def web_scraping_with_semaphores():
    """
    Web scraping with concurrency limiting using semaphores.
    This prevents overwhelming the server with too many requests.
    """
    print("\n=== Web scraping with concurrency limiting ===")
    
    # Semaphore to limit concurrent requests to 2
    semaphore = asyncio.Semaphore(2)
    
    async def fetch_with_limit(session, url):
        # Acquire semaphore before making request
        async with semaphore:
            print(f"Requesting {url} (with concurrency limit)...")
            return await fetch_url(session, url)
    
    urls = [
        'https://httpbin.org/delay/1',
        'https://httpbin.org/delay/1',
        'https://httpbin.org/delay/1',
        'https://httpbin.org/delay/1',
        'https://httpbin.org/delay/1',
    ]
    
    async with aiohttp.ClientSession() as session:
        start_time = time.time()
        
        # All requests will be made concurrently, but only 2 will execute at a time
        results = await asyncio.gather(*[
            fetch_with_limit(session, url) for url in urls
        ])
        
        end_time = time.time()
        
        print(f"\nCompleted {len(results)} requests with concurrency limit in {end_time - start_time:.2f} seconds")


async def web_scraping_with_error_handling():
    """
    Web scraping with proper error handling and timeout management.
    """
    print("\n=== Web scraping with error handling ===")
    
    async def safe_fetch_with_timeout(session, url, timeout_duration=5):
        """
        Fetch URL with timeout handling.
        """
        try:
            timeout = aiohttp.ClientTimeout(total=timeout_duration)
            # Create a new session with timeout for this specific request
            async with aiohttp.ClientSession(timeout=timeout) as temp_session:
                async with temp_session.get(url) as response:
                    content = await response.text()
                    return {
                        'url': url,
                        'status': response.status,
                        'length': len(content),
                        'success': True
                    }
        except asyncio.TimeoutError:
            print(f"✗ Timeout occurred for {url}")
            return {
                'url': url,
                'error': 'Timeout',
                'success': False
            }
        except aiohttp.ClientError as e:
            print(f"✗ Client error for {url}: {e}")
            return {
                'url': url,
                'error': f'ClientError: {e}',
                'success': False
            }
        except Exception as e:
            print(f"✗ Unexpected error for {url}: {e}")
            return {
                'url': url,
                'error': f'Unexpected: {e}',
                'success': False
            }
    
    # Include some URLs that might cause issues
    urls = [
        'https://httpbin.org/delay/1',
        'https://httpbin.org/status/404',  # This will return 404
        'https://httpbin.org/delay/10',    # This might timeout
        'https://jsonplaceholder.typicode.com/posts/1',
        'https://invalid-url-that-does-not-exist.com'  # This will fail
    ]
    
    async with aiohttp.ClientSession() as session:
        results = await asyncio.gather(*[
            safe_fetch_with_timeout(session, url) for url in urls
        ], return_exceptions=True)  # Return exceptions instead of raising them
        
        print("\nResults with error handling:")
        for result in results:
            if isinstance(result, Exception):
                print(f"  Exception occurred: {result}")
            elif not result['success']:
                print(f"  {result['url']}: FAILED - {result['error']}")
            else:
                print(f"  {result['url']}: {result['status']} - {result['length']} chars")


async def progressive_web_scraping():
    """
    Progressive scraping - process results as they complete.
    """
    print("\n=== Progressive web scraping (process as complete) ===")
    
    urls = [
        'https://httpbin.org/delay/2',
        'https://httpbin.org/delay/1',
        'https://httpbin.org/delay/3',
        'https://jsonplaceholder.typicode.com/posts/1'
    ]
    
    async with aiohttp.ClientSession() as session:
        # Create tasks for all requests
        tasks = [fetch_url(session, url) for url in urls]
        
        # Process results as they complete
        completed_count = 0
        async for result in asyncio.as_completed(tasks):
            completed_count += 1
            if 'error' in result:
                print(f"[{completed_count}/{len(urls)}] {result['url']}: ERROR - {result['error']}")
            else:
                print(f"[{completed_count}/{len(urls)}] {result['url']}: {result['status']} - {result['length']} chars")


# Example with real-world scraping considerations
async def real_world_scraping_example():
    """
    More realistic web scraping example with headers, delays, etc.
    """
    print("\n=== Real-world scraping considerations ===")
    
    # Common headers to appear more like a real browser
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    
    async def fetch_with_headers(session, url):
        try:
            print(f"Fetching {url} with headers...")
            # Add small random delay to be respectful to the server
            await asyncio.sleep(0.1)
            
            async with session.get(url, headers=headers) as response:
                content = await response.text()
                
                # Simple extraction - get title from HTML
                title_start = content.find('<title>') + 7
                title_end = content.find('</title>')
                if title_start > 6 and title_end > title_start:
                    title = content[title_start:title_end]
                else:
                    title = "No title found"
                
                return {
                    'url': url,
                    'status': response.status,
                    'title': title[:50] + "..." if len(title) > 50 else title,
                    'length': len(content)
                }
        except Exception as e:
            return {
                'url': url,
                'error': str(e)
            }
    
    urls = [
        'https://httpbin.org/html',
        'https://jsonplaceholder.typicode.com/posts/1'
    ]
    
    # Using a connector with limits to be respectful
    connector = aiohttp.TCPConnector(limit=10)  # Max 10 connections
    timeout = aiohttp.ClientTimeout(total=10)  # 10 second timeout
    
    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        results = await asyncio.gather(*[
            fetch_with_headers(session, url) for url in urls
        ])
        
        print("\nReal-world scraping results:")
        for result in results:
            if 'error' in result:
                print(f"  {result['url']}: ERROR - {result['error']}")
            else:
                print(f"  {result['url']}: {result['status']} - Title: {result['title']}")


if __name__ == "__main__":
    print("Demonstrating async web scraping techniques...\n")
    
    # Note: For this to work, you need to install aiohttp: pip install aiohttp
    try:
        asyncio.run(basic_web_scraping())
        asyncio.run(web_scraping_with_semaphores())
        asyncio.run(web_scraping_with_error_handling())
        asyncio.run(progressive_web_scraping())
        asyncio.run(real_world_scraping_example())
    except ImportError:
        print("To run this example, please install aiohttp:")
        print("pip install aiohttp")
    
    print("\nProgram completed!")