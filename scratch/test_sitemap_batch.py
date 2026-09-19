import re
import urllib.request
import time
import json
import sys
sys.path.append(".")
from extract_radiosnet_sitemap import extract_station_info, HEADERS

with open("sitemap_radio1.xml", "r", encoding="utf-8") as f:
    content = f.read()

urls = re.findall(r'<loc>\s*(https?://www\.radios\.com\.br/aovivo/[^\s<]+)\s*</loc>', content)
print("Total URLs:", len(urls))

sample = urls[1500:1530]
valid_found = []
for u in sample:
    try:
        req = urllib.request.Request(u, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=5) as resp:
            html = resp.read().decode("utf-8", errors="ignore")
            info = extract_station_info(html, u)
            if info and info.get("primary_stream_url"):
                valid_found.append(info)
                print(f"FOUND: {info.get('name')} | {info.get('city')}/{info.get('state')} -> {info.get('primary_stream_url')}")
    except Exception as e:
        pass
    time.sleep(0.2)

print(f"\nTested {len(sample)}, found {len(valid_found)} with direct stream!")
