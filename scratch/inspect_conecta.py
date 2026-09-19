import urllib.request
import re

url = 'https://www.fmconecta.com.br/'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req, timeout=10) as resp:
    html = resp.read().decode('utf-8', errors='ignore')
    for line in html.split('\n'):
        if any(w in line.lower() for w in ['http', 'stream', 'audio', 'source', 'player']):
            print(line.strip()[:140])
