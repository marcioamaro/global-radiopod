import urllib.request
import re

url = "https://iptv-org.github.io/iptv/countries/br.m3u"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
try:
    with urllib.request.urlopen(req, timeout=15) as resp:
        content = resp.read().decode("utf-8", errors="ignore")
        lines = content.splitlines()
        print("Total lines in br.m3u:", len(lines))
        radios = []
        for i, line in enumerate(lines):
            if line.startswith("#EXTINF") and ("radio" in line.lower() or "group-title=\"radio\"" in line.lower() or "audio" in line.lower()):
                stream = lines[i+1] if i+1 < len(lines) else ""
                radios.append((line, stream))
        print("Radios in br.m3u:", len(radios))
        for r in radios[:10]:
            print(r)
except Exception as e:
    print("Error:", e)
