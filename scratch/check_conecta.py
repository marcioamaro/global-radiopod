import urllib.request
import re

url = 'https://www.fmconecta.com.br/'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        print("fmconecta len:", len(html))
        streams = re.findall(r'https?://[^\s"\'<>]+\.(?:mp3|aac|m3u8)[^\s"\'<>]*', html)
        streams += re.findall(r'https?://[^\s"\'<>]*(?:stream|icecast|shoutcast|cast)[^\s"\'<>]*', html)
        for s in set(streams):
            print("Candidate:", s)
except Exception as e:
    print("Error:", e)
