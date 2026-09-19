import urllib.request
import json

url = "https://www.webradio.com.br/wp-json/wp/v2/station?per_page=5"
req = urllib.request.Request(url, headers={"User-Agent": "GlobalRadioPod/2.0"})
with urllib.request.urlopen(req, timeout=10) as resp:
    data = json.loads(resp.read().decode("utf-8"))
    for s in data:
        print("Name:", s.get("title", {}).get("rendered"))
        print("Local IDs:", s.get("local"))
        print("Genre IDs:", s.get("genre"))
        print("Station tags:", s.get("station_tag"))
        print("Meta stream:", s.get("meta", {}).get("stream"))
