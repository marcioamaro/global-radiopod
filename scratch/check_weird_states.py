import json

data = json.load(open("all_radio_sources.json", "r", encoding="utf-8"))
stations = data.get("stations", data)
weird = [s for s in stations if (s.get("country") in ("Brasil", "Brazil") or s.get("country_code") == "BR") and s.get("state") in ("NY", "LA", "IL", "KY", "AZ", "MD", "WI")]

print("Weird count:", len(weird))
for s in weird[:10]:
    print(f"Name: {s.get('name')} | Country: {s.get('country')} | Code: {s.get('country_code')} | State: {s.get('state')} | City: {s.get('city')} | URL: {s.get('primary_stream_url')}")
