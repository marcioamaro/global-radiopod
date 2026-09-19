import urllib.request
import re

url = 'https://rca1.com.br/'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req, timeout=10) as resp:
    html = resp.read().decode('utf-8', errors='ignore')
    # search for iframe, audio, or player scripts
    iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html)
    print("Iframes:", iframes)
    audio = re.findall(r'<audio[^>]+src=["\']([^"\']+)["\']', html)
    print("Audio:", audio)
    # search for .m3u8, .pls, or port
    ports = re.findall(r'https?://[a-zA-Z0-9.-]+:\d+/[^\s"\'<>]*', html)
    print("Ports:", ports)
