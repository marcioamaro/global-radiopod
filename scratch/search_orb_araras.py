import urllib.request
import re

url = 'https://onlineradiobox.com/search?q=Araras'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        matches = re.findall(r'href=["\'](/br/[^"\']+)["\'][^>]*>(.*?)</a>', html)
        for href, name in matches:
            if 'radio' not in href and 'station' not in href and not any(k in href for k in ['fraternidade', 'araras', 'clube']):
                continue
            clean = re.sub(r'<[^>]+>', '', name).strip()
            print(f"{clean}: https://onlineradiobox.com{href}")
except Exception as e:
    print("Error:", e)
