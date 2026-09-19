import json
from collections import Counter

data = json.load(open("all_radio_sources.json", "r", encoding="utf-8"))
stations = data.get("stations", data)
br_stations = [s for s in stations if s.get("country") in ("Brasil", "Brazil") or s.get("country_code") == "BR"]

print("Total BR stations:", len(br_stations))
states = Counter(s.get("state", "UNKNOWN") for s in br_stations)
print("\nTop 35 states / UFs:")
for st, count in states.most_common(35):
    print(f"  {st:25}: {count}")

cities = Counter(s.get("city", "UNKNOWN") for s in br_stations)
print("\nTop 25 cities:")
for city, count in cities.most_common(25):
    print(f"  {city:25}: {count}")
