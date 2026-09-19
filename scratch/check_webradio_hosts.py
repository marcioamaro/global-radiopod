import urllib.request
import json
from urllib.parse import urlparse
from collections import Counter

url = "https://www.webradio.com.br/wp-json/wp/v2/station?per_page=50"
req = urllib.request.Request(url, headers={"User-Agent": "GlobalRadioPod/2.0"})
try:
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print(f"Total stations fetched: {len(data)}")
        hosts = []
        for s in data:
            title = s.get("title", {}).get("rendered", "")
            meta = s.get("meta", {})
            streams = meta.get("stream", [])
            for st in streams:
                u = st.get("url", "")
                p = urlparse(u)
                if p.netloc:
                    hosts.append(p.netloc.lower())
                    print(f"  {title:30} -> {p.netloc}")
                    
        print("\nDomain distribution among 50 stations:")
        for h, c in Counter(hosts).most_common(10):
            print(f"  {h:35}: {c}")
except Exception as e:
    print("Error:", e)
