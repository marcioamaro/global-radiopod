import urllib.request
import re
import sys
sys.path.append(".")
from extract_radiosnet_sitemap import HEADERS

u = "https://www.radios.com.br/aovivo/radio-rondonia-fm-933/10292"
req = urllib.request.Request(u, headers=HEADERS)
try:
    with urllib.request.urlopen(req, timeout=5) as resp:
        html = resp.read().decode("utf-8", errors="ignore")
        print("Page fetched! Length:", len(html))
        # Look for iframe or player or scripts
        scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
        print("Found scripts:", len(scripts))
        for sc in scripts:
            if "stream" in sc or "player" in sc or "http" in sc:
                for line in sc.splitlines():
                    if any(w in line for w in ["http", "url", "file", "src", "stream"]):
                        print("  ", line.strip()[:100])
except Exception as e:
    print("Error:", e)
