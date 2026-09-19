import urllib.request
import json

url = "https://www.webradio.com.br/wp-json/wp/v2/station?per_page=10"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print(f"Fetched {len(data)} stations from WP REST API!")
        if data:
            first = data[0]
            print("Title:", first.get("title", {}).get("rendered"))
            print("Link:", first.get("link"))
            print("Keys:", list(first.keys()))
            # Check meta or custom fields
            for k in ["meta", "radion_stream", "stream", "stream_url", "acf"]:
                if k in first:
                    print(f"Field '{k}':", first[k])
except Exception as e:
    print("Error:", e)
