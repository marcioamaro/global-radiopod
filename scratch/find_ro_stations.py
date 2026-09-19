import urllib.request
import json

MIRROR = "https://de1.api.radio-browser.info/json"
queries = ["porto velho", "parecis", "rondonia", "rondônia", "caiari", "ji-parana"]

for q in queries:
    url = f"{MIRROR}/stations/search?name={urllib.parse.quote(q)}&limit=50"
    req = urllib.request.Request(url, headers={"User-Agent": "GlobalRadioPod/2.0"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"Query '{q}': {len(data)} results")
            for s in data:
                u = s.get("url_resolved") or s.get("url")
                print(f"  {s.get('name')} | State: {s.get('state')} | City: {s.get('city')} | URL: {u}")
    except Exception as e:
        print(f"Error {q}:", e)
