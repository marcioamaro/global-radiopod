import urllib.request
import re

req = urllib.request.Request("https://portalradiorondonia.com", headers={"User-Agent": "Mozilla/5.0"})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode("utf-8", errors="ignore")
        # search for audio or stream
        urls = re.findall(r'https?://[a-zA-Z0-9\.\_\:\-\/\?\=\&]+', html)
        potential = [u for u in set(urls) if any(w in u.lower() for w in ["stream", "cast", "live", "8000", "8002", "8080", "8443", ".mp3", ".aac"])]
        print("Found potential streams:", potential)
except Exception as e:
    print("Error:", e)
