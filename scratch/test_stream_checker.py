import asyncio
import time
import aiohttp

STREAM_REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Icy-MetaData": "1",
    "Range": "bytes=0-2048",
    "Accept": "*/*",
    "Connection": "keep-alive"
}

async def test_stream(session: aiohttp.ClientSession, url: str) -> tuple:
    if not url or not url.startswith("http"):
        return False, "invalid_url", 0
    start = time.time()
    try:
        timeout = aiohttp.ClientTimeout(sock_connect=4.0, sock_read=3.0, total=7.0)
        async with session.get(url, headers=STREAM_REQUEST_HEADERS, timeout=timeout, allow_redirects=True) as resp:
            ctype = resp.headers.get("Content-Type", "").lower()
            if resp.status in (200, 206):
                chunk = await resp.content.read(2048)
                if len(chunk) > 0:
                    return True, ctype, round((time.time() - start) * 1000)
    except Exception as e:
        return False, str(e)[:30], 0
    return False, "bad_status", 0

async def main():
    test_urls = [
        "https://cast.youngtech.radio.br/radio/8480/radio",
        "https://ssl.cxradio.com.br:8443/live",
        "https://stream.zeno.fm/f3wvbbqmdg8uv",
        "http://invalid.stream.test/stream"
    ]
    async with aiohttp.ClientSession() as session:
        tasks = [test_stream(session, u) for u in test_urls]
        results = await asyncio.gather(*tasks)
        for u, r in zip(test_urls, results):
            print(f"{u[:50]} -> Valid: {r[0]}, Type: {r[1]}, Latency: {r[2]}ms")

if __name__ == "__main__":
    asyncio.run(main())
