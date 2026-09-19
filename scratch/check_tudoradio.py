import urllib.request
import re

url = 'https://tudoradio.com/dials/cidade/178-araras'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        print("Tudoradio Araras len:", len(html))
        stations = re.findall(r'<a[^>]+href=["\'](/player/[^"\']+)["\'][^>]*>(.*?)</a>', html)
        for href, name in stations:
            print(f"{name.strip()}: https://tudoradio.com{href}")
except Exception as e:
    print("Error:", e)
