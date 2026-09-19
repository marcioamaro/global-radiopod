import urllib.request
import re

url = 'http://www.fraternidadefm.com.br'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        print(f"Read {len(html)} bytes from {url}")
        urls = set(re.findall(r'https?://[^\s"\'<>]+\b', html))
        stream_candidates = [u for u in urls if any(k in u.lower() for k in ['.mp3', '.aac', '.m3u8', ':8', ':7', ':9', 'stream', 'icecast', 'shoutcast', 'cast'])]
        print("Stream candidates:")
        for s in stream_candidates[:10]:
            print("  ", s)
except Exception as e:
    print("Error:", e)
