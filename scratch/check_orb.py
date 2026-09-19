import urllib.request
import re

url = 'https://onlineradiobox.com/br/fraternidade/'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        streams = re.findall(r'https?://[^\s"\'<>]+\.(?:mp3|aac|m3u8|pls)[^\s"\'<>]*', html)
        stream_attrs = re.findall(r'stream=["\']([^"\']+)["\']', html)
        data_streams = re.findall(r'data-stream=["\']([^"\']+)["\']', html)
        print("Streams found:", streams)
        print("Stream attrs:", stream_attrs)
        print("Data streams:", data_streams)
except Exception as e:
    print("Error:", e)
