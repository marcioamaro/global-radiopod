import urllib.request
import json

url = "https://www.webradio.com.br/wp-json/wp/v2/local?search=brasil&per_page=50"
req = urllib.request.Request(url, headers={"User-Agent": "GlobalRadioPod/2.0"})
with urllib.request.urlopen(req, timeout=10) as resp:
    data = json.loads(resp.read().decode("utf-8"))
    for t in data:
        print(t.get("id"), t.get("name"), t.get("slug"), "parent:", t.get("parent"), "count:", t.get("count"))
