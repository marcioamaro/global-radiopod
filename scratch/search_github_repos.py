import urllib.request
import json

url = "https://api.github.com/search/repositories?q=brazil+radio+m3u+in:name,description"
req = urllib.request.Request(url, headers={"User-Agent": "GlobalRadioPod/2.0"})
try:
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print("Found repos:", data.get("total_count", 0))
        for item in data.get("items", [])[:5]:
            print(item.get("full_name"), item.get("html_url"))
except Exception as e:
    print("Error:", e)
