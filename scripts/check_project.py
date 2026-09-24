"""Offline consistency checks; does not contact providers or inspect secrets."""
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
stations = json.loads((ASSETS / "radio_catalog.json").read_text(encoding="utf-8"))["stations"]
by_id = {s["id"]: s for s in stations}
assert len(by_id) == len(stations), "Duplicate radio IDs"
assert all(s["name"] and s["primary_stream_url"].startswith(("http://", "https://")) for s in stations)
rankings = json.loads((ASSETS / "media_rankings.json").read_text(encoding="utf-8"))
count = 0
for key, ranking in rankings.items():
    if not isinstance(ranking, dict) or "entries" not in ranking:
        continue
    entries = ranking["entries"]
    assert [e["rank"] for e in entries] == list(range(1, 21)), key
    for entry in entries:
        if "station" in entry:
            station = entry["station"]
            assert by_id[station["id"]]["primary_stream_url"] == station["primary_stream_url"], key
        else:
            assert entry["show"]["feedUrl"].startswith(("http://", "https://")), key
    count += 1
assert count == 4
tracked = subprocess.check_output(["git", "ls-files", "*.jks", "*.keystore"], cwd=ROOT, text=True)
assert not tracked.strip(), "Signing keys must not be tracked"
build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
for declaration in re.findall(r"val release(?:Store|Key)Password\s*=.*?(?=\n\s*val )", build, re.S):
    assert not re.search(r'\?:\s*"', declaration), "Hardcoded release password"
print(f"OK: {len(stations)} radios, {count} Top 20 rankings, signing configuration")
resources = ROOT / "app/src/main/res"
default_keys = {node.attrib["name"] for node in ET.parse(resources / "values/feature_strings.xml").getroot()}
for language in ("pt", "es", "fr", "de", "it", "ja"):
    translated = ET.parse(resources / f"values-{language}/feature_strings.xml").getroot()
    assert {node.attrib["name"] for node in translated} == default_keys, language
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
assert manifest.find("application").attrib["{http://schemas.android.com/apk/res/android}allowBackup"] == "false"
rules = ET.parse(resources / "xml/data_extraction_rules.xml").getroot()
for mode in ("cloud-backup", "device-transfer"):
    assert len(rules.find(mode).findall("exclude")) == 9, mode
print(f"OK: {len(default_keys)} feature strings in 7 languages; automatic backup disabled")
