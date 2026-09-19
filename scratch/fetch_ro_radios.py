import urllib.request
import re
import sys
sys.path.append(".")
from extract_radiosnet_sitemap import extract_station_info, HEADERS

urls = [
    "https://www.radios.com.br/aovivo/radio-rondonia-fm-933/10292",
    "https://www.radios.com.br/aovivo/radio-sgc-1019-fm/11516",
    "https://www.radios.com.br/aovivo/radio-caiari-1031-fm/10294",
    "https://www.radios.com.br/aovivo/radio-rio-madeira-1059-fm/15694"
]

found = []
for u in urls:
    try:
        req = urllib.request.Request(u, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=5) as resp:
            html = resp.read().decode("utf-8", errors="ignore")
            info = extract_station_info(html, u)
            if info:
                print("FOUND:", info.get("name"), "->", info.get("primary_stream_url"))
                stream = info.get("primary_stream_url")
                # Test stream
                sreq = urllib.request.Request(stream, headers={"User-Agent": "Mozilla/5.0"})
                with urllib.request.urlopen(sreq, timeout=5) as sresp:
                    chunk = sresp.read(1024)
                    print("  Stream OK! Status:", sresp.status, "Bytes read:", len(chunk))
                    info["state"] = "RO"
                    found.append(info)
    except Exception as e:
        print("Error on", u, e)

print("Total RO stations verified:", len(found))
