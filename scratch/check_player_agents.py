import urllib.request
import aiohttp
import asyncio

test_urls = [
    "https://brasil.lunafm.net/listen/lunabrasil/radio.aac",
    "http://cast3.midiazdx.com.br:7166/stream",
    "http://s01.svrdedicado.org:6746/stream",
    "https://cast5.midiazdx.com.br:7262/stream",
    "https://stm7.xradios.com.br:7030/stream"
]

user_agents = [
    ("ExoPlayer Standard", "ExoPlayerLib/2.19.1 (Linux; Android 14)"),
    ("Chrome Android", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"),
    ("VLC Player", "VLC/3.5.4 (Linux; Android 14)"),
    ("No Agent (Python)", None),
    ("Site Specific", "WebRadio/1.0 (https://www.webradio.com.br)")
]

async def check():
    for name, ua in user_agents:
        print(f"\n--- Testing with User-Agent: {name} ---")
        headers = {}
        if ua: headers["User-Agent"] = ua
        headers["Icy-MetaData"] = "1"
        headers["Range"] = "bytes=0-1024"
        
        async with aiohttp.ClientSession() as session:
            for u in test_urls:
                try:
                    async with session.get(u, headers=headers, timeout=aiohttp.ClientTimeout(total=4), allow_redirects=True) as resp:
                        chunk = await resp.content.read(512)
                        print(f"  {resp.status} (bytes: {len(chunk)}) -> {u[:45]}")
                except Exception as e:
                    print(f"  FAILED -> {u[:45]} : {str(e)[:35]}")

asyncio.run(check())
