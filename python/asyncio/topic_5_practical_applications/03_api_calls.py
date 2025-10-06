"""
AsyncIO Example: API Calls

This file demonstrates making concurrent API calls using aiohttp.
It includes examples of REST API calls, handling responses, and managing API limits.
"""

import asyncio
import aiohttp
import time
import json


async def fetch_api_data(session, url, headers=None):
    """
    Generic function to fetch data from an API endpoint.
    """
    try:
        print(f"Fetching API data from: {url}")
        async with session.get(url, headers=headers) as response:
            if response.status == 200:
                data = await response.json()
                print(f"✓ Fetched {len(str(data))} characters from {url}")
                return {
                    'url': url,
                    'status': response.status,
                    'data': data,
                    'success': True
                }
            else:
                print(f"✗ API request failed: {response.status} for {url}")
                return {
                    'url': url,
                    'status': response.status,
                    'error': f'HTTP {response.status}',
                    'success': False
                }
    except Exception as e:
        print(f"✗ Error fetching {url}: {e}")
        return {
            'url': url,
            'error': str(e),
            'success': False
        }


async def basic_api_calls():
    """
    Basic example of concurrent API calls.
    """
    print("=== Basic concurrent API calls ===")
    
    # Using JSONPlaceholder - a free testing API
    urls = [
        'https://jsonplaceholder.typicode.com/posts/1',
        'https://jsonplaceholder.typicode.com/posts/2',
        'https://jsonplaceholder.typicode.com/posts/3',
        'https://jsonplaceholder.typicode.com/users/1',
        'https://jsonplaceholder.typicode.com/users/2'
    ]
    
    headers = {
        'User-Agent': 'AsyncIO API Demo Client'
    }
    
    async with aiohttp.ClientSession() as session:
        start_time = time.time()
        
        # Make all API calls concurrently
        results = await asyncio.gather(*[
            fetch_api_data(session, url, headers) for url in urls
        ])
        
        end_time = time.time()
        
        print(f"\nCompleted {len(results)} API calls in {end_time - start_time:.2f} seconds")
        
        # Process and display results
        for result in results:
            if result['success']:
                print(f"  {result['url']}: Success - {result['status']}")
                # Show a small part of the data
                data_preview = str(result['data'])[:100] + "..." if len(str(result['data'])) > 100 else str(result['data'])
                print(f"    Data preview: {data_preview}")
            else:
                print(f"  {result['url']}: Failed - {result.get('error', 'Unknown error')}")


async def api_calls_with_rate_limiting():
    """
    API calls with rate limiting to respect API limits.
    """
    print("\n=== API calls with rate limiting ===")
    
    # Create a semaphore to limit concurrent requests
    # This can help respect API rate limits
    rate_limiter = asyncio.Semaphore(2)  # Max 2 concurrent requests
    
    async def rate_limited_fetch(session, url):
        """
        Fetch URL with rate limiting.
        """
        async with rate_limiter:
            print(f"Making rate-limited request to: {url}")
            return await fetch_api_data(session, url)
    
    # More URLs for rate limiting example
    urls = [
        'https://jsonplaceholder.typicode.com/posts/4',
        'https://jsonplaceholder.typicode.com/posts/5',
        'https://jsonplaceholder.typicode.com/todos/1',
        'https://jsonplaceholder.typicode.com/todos/2',
        'https://jsonplaceholder.typicode.com/albums/1',
        'https://jsonplaceholder.typicode.com/albums/2'
    ]
    
    async with aiohttp.ClientSession() as session:
        start_time = time.time()
        
        # These requests will be rate-limited to 2 at a time
        results = await asyncio.gather(*[
            rate_limited_fetch(session, url) for url in urls
        ])
        
        end_time = time.time()
        
        print(f"\nCompleted {len(results)} rate-limited API calls in {end_time - start_time:.2f} seconds")


async def post_api_requests():
    """
    Example of making POST requests to APIs.
    """
    print("\n=== POST API requests ===")
    
    async def post_data(session, url, data):
        """
        Make a POST request with JSON data.
        """
        try:
            print(f"POSTing to: {url}")
            async with session.post(url, json=data) as response:
                if response.status in [200, 201]:  # Success or created
                    result_data = await response.json()
                    print(f"✓ POST successful to {url}")
                    return {
                        'url': url,
                        'status': response.status,
                        'data': result_data,
                        'success': True
                    }
                else:
                    print(f"✗ POST failed: {response.status} for {url}")
                    response_text = await response.text()
                    return {
                        'url': url,
                        'status': response.status,
                        'error': f'HTTP {response.status}: {response_text}',
                        'success': False
                    }
        except Exception as e:
            print(f"✗ Error in POST to {url}: {e}")
            return {
                'url': url,
                'error': str(e),
                'success': False
            }
    
    # POST data examples
    post_tasks = []
    
    # Create a new post
    new_post = {
        "title": "AsyncIO Example Post",
        "body": "This post was created using asyncio and aiohttp",
        "userId": 1
    }
    post_tasks.append(post_data(
        aiohttp.ClientSession(), 
        'https://jsonplaceholder.typicode.com/posts', 
        new_post
    ))
    
    # Create a new user (though JSONPlaceholder is read-only for posts)
    new_user = {
        "name": "Async User",
        "username": "asyncuser",
        "email": "async@example.com"
    }
    post_tasks.append(post_data(
        aiohttp.ClientSession(), 
        'https://jsonplaceholder.typicode.com/users', 
        new_user
    ))
    
    # For proper session handling, we create a session for each request
    results = []
    for task in post_tasks:
        # Create a new session for each task since we can't share sessions
        async with aiohttp.ClientSession() as session:
            # Replace the session in the task with the new one
            task_result = await task
            results.append(task_result)
    
    # More realistic approach with context manager
    print("\nMaking POST requests with proper session handling...")
    
    post_data_json = {
        "title": "AsyncIO Test",
        "body": "Testing POST with asyncio",
        "userId": 1
    }
    
    async with aiohttp.ClientSession() as session:
        # Make the actual POST request
        try:
            print("Making POST request...")
            async with session.post('https://jsonplaceholder.typicode.com/posts', json=post_data_json) as response:
                result = await response.json()
                print(f"✓ POST successful, created ID: {result.get('id')}")
                print(f"  Posted: {result['title']}")
        except Exception as e:
            print(f"✗ POST request failed: {e}")


async def api_error_handling():
    """
    Example of handling various API errors and edge cases.
    """
    print("\n=== API error handling ===")
    
    async def safe_api_call(session, url, timeout_duration=5):
        """
        Make an API call with comprehensive error handling.
        """
        try:
            timeout = aiohttp.ClientTimeout(total=timeout_duration)
            async with session.get(url, timeout=timeout) as response:
                if response.status == 200:
                    # Try to parse JSON
                    try:
                        data = await response.json()
                        return {
                            'url': url,
                            'status': response.status,
                            'data': data,
                            'success': True
                        }
                    except Exception:
                        # If JSON parsing fails, get text instead
                        text = await response.text()
                        return {
                            'url': url,
                            'status': response.status,
                            'data': text,
                            'success': True,
                            'note': 'Response not in JSON format'
                        }
                elif response.status == 404:
                    return {
                        'url': url,
                        'status': response.status,
                        'error': 'Resource not found',
                        'success': False
                    }
                elif response.status == 429:
                    return {
                        'url': url,
                        'status': response.status,
                        'error': 'Rate limit exceeded',
                        'success': False
                    }
                else:
                    return {
                        'url': url,
                        'status': response.status,
                        'error': f'HTTP {response.status}',
                        'success': False
                    }
        except asyncio.TimeoutError:
            return {
                'url': url,
                'error': 'Request timed out',
                'success': False
            }
        except aiohttp.ClientConnectorError:
            return {
                'url': url,
                'error': 'Connection error - server unreachable',
                'success': False
            }
        except Exception as e:
            return {
                'url': url,
                'error': f'Unexpected error: {e}',
                'success': False
            }
    
    # Include some URLs that might cause different types of errors
    urls = [
        'https://jsonplaceholder.typicode.com/posts/1',  # Valid
        'https://jsonplaceholder.typicode.com/posts/999',  # Will return 404
        'https://httpbin.org/delay/10',  # Might timeout
        'https://invalid-url-that-does-not-exist.com',  # Connection error
        'https://httpbin.org/status/429'  # Rate limit error
    ]
    
    async with aiohttp.ClientSession() as session:
        results = await asyncio.gather(*[
            safe_api_call(session, url) for url in urls
        ])
        
        print("\nError handling results:")
        for result in results:
            if result['success']:
                status = f"✓ Success ({result['status']})"
                if 'note' in result:
                    status += f" - {result['note']}"
                print(f"  {result['url']}: {status}")
            else:
                print(f"  {result['url']}: ✗ Failed - {result['error']}")


async def paginated_api_calls():
    """
    Example of handling paginated API responses.
    """
    print("\n=== Paginated API calls ===")
    
    async def fetch_page(session, page_number):
        """
        Fetch a specific page from a paginated API.
        """
        url = f'https://jsonplaceholder.typicode.com/posts?_page={page_number}&_limit=3'
        print(f"Fetching page {page_number}...")
        return await fetch_api_data(session, url)
    
    async def fetch_all_pages(total_pages):
        """
        Fetch all pages concurrently.
        """
        async with aiohttp.ClientSession() as session:
            # Fetch all pages concurrently
            page_results = await asyncio.gather(*[
                fetch_page(session, page_num) 
                for page_num in range(1, total_pages + 1)
            ])
            
            # Combine all results
            all_posts = []
            for result in page_results:
                if result['success']:
                    all_posts.extend(result['data'])
            
            return all_posts
    
    # Fetch first 3 pages concurrently
    all_posts = await fetch_all_pages(3)
    print(f"Fetched {len(all_posts)} total posts from 3 pages")


async def progressive_api_processing():
    """
    Process API results as they become available (useful for large datasets).
    """
    print("\n=== Progressive API processing ===")
    
    urls = [
        'https://jsonplaceholder.typicode.com/posts/1',
        'https://jsonplaceholder.typicode.com/posts/2',
        'https://jsonplaceholder.typicode.com/posts/3',
        'https://jsonplaceholder.typicode.com/users/1',
        'https://jsonplaceholder.typicode.com/users/2'
    ]
    
    async with aiohttp.ClientSession() as session:
        # Create tasks for all requests
        tasks = [fetch_api_data(session, url) for url in urls]
        
        # Process results as they complete
        completed_count = 0
        async for result in asyncio.as_completed(tasks):
            completed_count += 1
            if result['success']:
                print(f"[{completed_count}/{len(urls)}] ✓ {result['url']}: Success")
                # Process this result immediately without waiting for others
                # For example, you might save to database, process data, etc.
                if 'title' in str(result.get('data', {})):
                    print(f"      Title found: {result['data'].get('title', 'N/A')}")
            else:
                print(f"[{completed_count}/{len(urls)}] ✗ {result['url']}: Failed - {result.get('error')}")


if __name__ == "__main__":
    print("Demonstrating async API calls...\n")
    
    # Note: For this to work, you need to install aiohttp: pip install aiohttp
    try:
        asyncio.run(basic_api_calls())
        asyncio.run(api_calls_with_rate_limiting())
        asyncio.run(post_api_requests())
        asyncio.run(api_error_handling())
        asyncio.run(paginated_api_calls())
        asyncio.run(progressive_api_processing())
    except ImportError:
        print("To run this example, please install aiohttp:")
        print("pip install aiohttp")
    
    print("\nProgram completed!")