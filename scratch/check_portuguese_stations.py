import urllib.request
import json

url = "https://de1.api.radio-browser.info/json/stations/bylanguageexact/portuguese?limit=10000&hidebroken=true"
req = urllib.request.Request(url, headers={"User-Agent": "GlobalRadioPod/2.0"})
try:
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print("Total Portuguese stations in Radio Browser:", len(data))
        br = [s for s in data if s.get("countrycode") == "BR" or s.get("country") in ("Brazil", "Brasil")]
        print("Of which BR countrycode:", len(br))
        non_br = [s for s in data if s.get("countrycode") != "BR"]
        print("Non-BR Portuguese stations:", len(non_br))
        sample_non_br = [(s.get("name"), s.get("country"), s.get("countrycode"), s.get("state")) for s in non_br[:10]]
        for s in sample_non_br:
            print("  ", s)
except Exception as e:
    print("Error:", e)
