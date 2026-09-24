"""Audit municipality/UF inconsistencies against IBGE; apply only source-confirmed locations."""
import json
import unicodedata
from collections import defaultdict
from pathlib import Path
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent
def norm(value):
    return ''.join(c for c in unicodedata.normalize('NFKD', value.casefold()) if c.isalnum())

def main():
    municipalities = json.loads((ROOT / 'reports/ibge-municipios.json').read_text(encoding='utf-8'))
    cities = defaultdict(list)
    states = {}
    for city in municipalities:
        region = city.get('regiao-imediata', {}).get('regiao-intermediaria', {}).get('UF')
        region = region or city.get('microrregiao', {}).get('mesorregiao', {}).get('UF')
        if region:
            states[norm(region['nome'])] = region['sigla']
            cities[norm(city['nome'])].append((city['nome'], region['sigla']))
    path = ROOT / 'app/src/main/assets/radio_catalog.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    ranked = json.loads((ROOT / 'app/src/main/assets/media_rankings.json').read_text(encoding='utf-8'))
    indexed = {s['id']: s for s in data['stations']}
    corrections = []
    evidence = json.loads((ROOT / 'reports/media-rankings/popularity-evidence.json').read_text(encoding='utf-8'))
    for row in evidence['radio_brazil']['entries']:
        sid = row['sourceUrl'].rstrip('/').split('-')[-1]
        html = ROOT / f'reports/media-rankings/mytuner-radio-{sid}.html'
        if not html.exists(): continue
        soup = BeautifulSoup(html.read_text(encoding='utf-8'), 'html.parser')
        for script in soup.select('script[type="application/ld+json"]'):
            items = json.loads(script.string or script.get_text())
            if not isinstance(items, list): items = [items]
            for item in items:
                if item.get('@type') != 'BreadcrumbList': continue
                crumbs = item.get('itemListElement', [])
                if len(crumbs) < 4 or norm(crumbs[0]['name']) != 'brasil': continue
                state = states.get(norm(crumbs[1]['name']))
                options = [c for c in cities[norm(crumbs[2]['name'])] if c[1] == state]
                if len(options) != 1: continue
                station = indexed[row['stationId']]
                city, state = options[0]
                if (station.get('city'), station.get('state')) != (city, state):
                    corrections.append(dict(id=station['id'], before=[station.get('city'), station.get('state')],
                                            after=[city, state], source=row['sourceUrl']))
                    station.update(city=city, state=state)
                    for entry in ranked['radio_brazil']['entries']:
                        if entry['station']['id'] == station['id']: entry['station'].update(city=city, state=state)
    unresolved = []
    for station in data['stations']:
        if station.get('country_code') != 'BR': continue
        matches = cities.get(norm(station.get('city', '')), [])
        if not matches or station.get('state') not in {s for _, s in matches}:
            unresolved.append(dict(id=station['id'], name=station['name'], city=station.get('city'), state=station.get('state'),
                reason='City missing/ambiguous or municipality/UF mismatch; publisher evidence required'))
    for file in [path, ROOT / 'all_radio_sources.json']:
        file.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (ROOT / 'app/src/main/assets/media_rankings.json').write_text(json.dumps(ranked, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    report = dict(reference='https://servicodados.ibge.gov.br/api/v1/localidades/municipios', corrections=corrections,
                  unverified=unresolved, note='Unverified entries retained without guessed locations.')
    (ROOT / 'reports/checklist-geography-audit.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Source-confirmed corrections:', len(corrections), 'Unverified locations:', len(unresolved))

if __name__ == '__main__': main()
