import re

with open("scratch/test_single_page.py", "r") as f:
    pass

import urllib.request
import sys
sys.path.append(".")
from extract_radiosnet_sitemap import HEADERS

u = "https://www.radios.com.br/aovivo/radio-rondonia-fm-933/10292"
req = urllib.request.Request(u, headers=HEADERS)
with urllib.request.urlopen(req, timeout=5) as resp:
    html = resp.read().decode("utf-8", errors="ignore")
    idx = html.find("/ajax/player/ssec")
    if idx != -1:
        print(html[idx-100:idx+300])
