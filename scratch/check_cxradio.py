import urllib.request
import json
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

# Let's check CXRadio or radios.com.br for Araras
# CXRadio: https://www.cxradio.com.br/radios/brasil/sp/araras
url = 'https://www.cxradio.com.br/radios/brasil/sp/araras'
try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        print(f"CXRadio HTML len: {len(html)}")
        links = re.findall(r'href=["\'](/radio/[^"\']+)["\']', html)
        print("CXRadio links:", links)
except Exception as e:
    print("CXRadio error:", e)
