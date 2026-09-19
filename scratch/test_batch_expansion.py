import urllib.request
import json

mirrors = [
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json",
    "https://at1.api.radio-browser.info/json"
]

countries_to_test = [
    ("BR", 100),
    ("AR", 50),
    ("MX", 50),
    ("US", 100),
    ("GB", 100),
    ("DE", 50),
    ("FR", 50),
    ("ES", 50),
    ("PT", 50),
    ("IT", 50),
    ("JP", 50),
    ("AU", 50)
]

for cc, limit in countries_to_test:
    url = f"{mirrors[0]}/stations/bycountrycodeexact/{cc}?limit={limit}&order=votes&reverse=true&hidebroken=true"
    req = urllib.request.Request(url, headers={'User-Agent': 'GlobalRadioPod/2.0'})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.load(resp)
            # count working
            ok_count = sum(1 for s in data if s.get('lastcheckok') == 1)
            print(f"Country {cc}: fetched {len(data)}, verified working={ok_count}")
    except Exception as e:
        print(f"Country {cc} error: {e}")
