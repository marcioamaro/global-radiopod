"""Export the validated catalog asset. Kotlin is now a stable facade, not generated data."""
import json
from pathlib import Path
root = Path(__file__).resolve().parent
source = root / 'all_radio_sources.json'
data = json.loads(source.read_text(encoding='utf-8'))
stations = data['stations']
assert stations and len({s['id'] for s in stations}) == len(stations)
assert all(s.get('primary_stream_url', '').startswith(('http://', 'https://')) for s in stations)
(root / 'app/src/main/assets/radio_catalog.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Catalog asset exported:', len(stations), 'stations; Kotlin generation is no longer necessary.')
