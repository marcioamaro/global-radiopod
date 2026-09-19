import urllib.request
import re
import sys
sys.path.append(".")
from extract_radiosnet_sitemap import HEADERS

u = "https://www.radios.com.br/aovivo/radio-rondonia-fm-933/10292"
req = urllib.request.Request(u, headers=HEADERS)
with urllib.request.urlopen(req, timeout=5) as resp:
    html = resp.read().decode("utf-8", errors="ignore")
    # Find all matches of http in javascript
    m = re.findall(r'(https?://[a-zA-Z0-9\.\_\:\-\/]+)', html)
    streams = [x for x in set(m) if not any(ign in x for ign in ["radios.com.br", "google", "facebook", "twitter", "gstatic", "cloudflare", "schema.org", "datatables", "w3.org", "firebase"])]
    print("Found potential streams:")
    for s in streams:
        print("  ", s)
