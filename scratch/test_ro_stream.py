import urllib.request
import aiohttp
import asyncio

async def test():
    urls = [
        "https://stream.zeno.fm/pxbrdor4opmvv", # Maresol Web Rádio Porto Velho
        "http://icecast.cxradio.com.br/radiocaiari",
        "https://ssl.painelstream.net:8148/stream", # Parecis FM
        "https://painel.fabricahost.com.br:8000/stream" # Rádio Rondônia
    ]
    async with aiohttp.ClientSession() as s:
        for u in urls:
            try:
                async with s.get(u, timeout=aiohttp.ClientTimeout(total=4)) as resp:
                    print(u, resp.status, resp.headers.get("Content-Type"))
            except Exception as e:
                print(u, "Error:", e)

asyncio.run(test())
