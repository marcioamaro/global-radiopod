import urllib.request
import json

mirrors = [
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json",
    "https://at1.api.radio-browser.info/json"
]

url = f"{mirrors[0]}/stations/search?limit=10&order=votes&reverse=true&hidebroken=true"
headers = {'User-Agent': 'GlobalRadioPod/2.0 (ExpansionPipeline)'}

try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.load(resp)
        print(f"Top {len(data)} stations fetched successfully:")
        for s in data[:5]:
            print(f"  {s.get('name')} ({s.get('countrycode')}) - Votes: {s.get('votes')} - Codec: {s.get('codec')}")
except Exception as e:
    print("Error:", e)
