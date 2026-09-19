import urllib.request
import re
from urllib.parse import urlparse

url = "https://www.webradio.com.br"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode("utf-8", errors="ignore")
        print("Fetched webradio.com.br! Length:", len(html))
        # Find links and stream references
        links = re.findall(r'href=[\'\"]([^\'\"]+)[\'\"]', html)
        print("Sample links:", links[:15])
        
        # Check audio / source tags
        sources = re.findall(r'src=[\'\"]([^\'\"]+)[\'\"]', html)
        print("Sample src:", sources[:15])
except Exception as e:
    print("Error connecting to webradio.com.br:", e)
