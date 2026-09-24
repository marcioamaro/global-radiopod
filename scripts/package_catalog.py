"""Builds the local update package after offline catalog checks."""
import runpy
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
runpy.run_path(str(root / "scripts/check_project.py"))
output = root / "reports/mediapod-catalog.zip"
with ZipFile(output, "w", ZIP_DEFLATED) as archive:
    for name in ("radio_catalog.json", "media_rankings.json"):
        archive.write(root / "app/src/main/assets" / name, name)
print(output)
