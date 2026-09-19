import urllib.request
import json

url = "https://www.webradio.com.br/wp-json/wp/v2/local?parent=860&per_page=100"
req = urllib.request.Request(url, headers={"User-Agent": "GlobalRadioPod/2.0"})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print("Total Brazilian states in webradio.com.br:", len(data))
        for t in sorted(data, key=lambda x: x.get("count", 0), reverse=True):
            print(f"  ID: {t.get('id'):5} | State: {t.get('name'):25} | Slug: {t.get('slug'):25} | Count: {t.get('count')}")
except Exception as e:
    print("Error:", e)
