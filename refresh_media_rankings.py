"""Refresh auditable ranking snapshots and discover Brazilian RSS podcasts.

Run from the project root: python refresh_media_rankings.py
Requires requests and beautifulsoup4. Raw source responses are cached in reports.
Rank positions are never inferred from votes, episode counts or search results.
"""
import concurrent.futures as futures
import datetime as dt
import json
import re
import time
import threading
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlsplit

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent
REPORT = ROOT / 'reports/media-rankings'
REPORT.mkdir(parents=True, exist_ok=True)
ASSETS = ROOT / 'app/src/main/assets'
TODAY = dt.datetime.now(dt.timezone.utc).date()
MONTH = TODAY.replace(day=1) - dt.timedelta(days=1)


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def norm(value):
    return ''.join(c for c in unicodedata.normalize('NFKD', value.casefold()) if c.isalnum())


def get(url, **kwargs):
    r = requests.get(url, timeout=25, **kwargs)
    r.raise_for_status()
    return r


APPLE_LOCK = threading.Lock()
APPLE_LAST = 0.0


def apple(params):
    """Catalog metadata only; these results must never determine chart order."""
    global APPLE_LAST
    key = __import__('hashlib').sha256(json.dumps(params, sort_keys=True).encode()).hexdigest()[:20]
    cache = REPORT / f'lookup-{key}.json'
    if cache.exists():
        return json.loads(cache.read_text(encoding='utf-8'))
    with APPLE_LOCK:
        time.sleep(max(0, 3.1 - (time.monotonic() - APPLE_LAST)))
        endpoint = 'lookup' if 'id' in params else 'search'
        response = get('https://itunes.apple.com/' + endpoint, params=params)
        APPLE_LAST = time.monotonic()
        data = response.json().get('results', [])
        dump(cache, data)
        return data


def show_from_lookup(item):
    return dict(id='itunes_' + str(item['collectionId']), title=item['collectionName'],
                author=item.get('artistName', ''), description=item.get('primaryGenreName', ''),
                feedUrl=item['feedUrl'], artworkUrl=item.get('artworkUrl600', ''), country='GLOBAL',
                category=item.get('primaryGenreName', 'Geral'), episodeCount=item.get('trackCount', 0))


def verify_feed(show):
    """Only add feeds with real audio enclosures and publisher-declared language."""
    try:
        with requests.get(show['feedUrl'], timeout=18, stream=True) as response:
            response.raise_for_status()
            content = bytearray()
            for block in response.iter_content(65536):
                content.extend(block)
                if len(content) > 32_000_000:
                    raise ValueError('Feed exceeds validation size limit')
        root = ET.fromstring(content)
        channel = root.find('channel')
        if channel is None:
            return None
        language = channel.findtext('language', '').lower().replace('_', '-')
        episodes = [item for item in channel.findall('item') if any(
            urlsplit(e.get('url', '')).scheme in ('http', 'https') and
            (e.get('type', '').startswith('audio/') or re.search(r'\.(mp3|m4a|aac|ogg)(\?|$)', e.get('url', '')))
            for e in item.findall('enclosure'))]
        if not episodes:
            return None
        result = dict(show, episodeCount=len(episodes), rssLanguage=language, feedCheckedAt=TODAY.isoformat())
        # Storefront country is NOT a podcast's country of production.
        if language == 'pt-br':
            result['country'] = 'BR'
        elif language == 'pt-pt':
            result['country'] = 'PT'
        result['description'] = BeautifulSoup(channel.findtext('description', ''), 'html.parser').get_text(' ', strip=True)[:1200]
        return result
    except (requests.RequestException, ET.ParseError, ValueError):
        return None


def expand_podcasts():
    original = json.loads((ASSETS / 'podcasts_catalog.json').read_text(encoding='utf-8'))
    before_brazil = sum(x.get("country") == "BR" for x in original)
    ids = set()
    # Discovery only. Rankings still come exclusively from Spotify.
    for genre in ('', '1301', '1303', '1304', '1305', '1307', '1310', '1311', '1318', '1321', '1324', '1487', '1533', '1545'):
        cache = REPORT / f'discovery-br-{genre or "all"}.json'
        try:
            if cache.exists():
                data = json.loads(cache.read_text(encoding='utf-8'))
            else:
                url = 'https://itunes.apple.com/br/rss/toppodcasts/limit=100' + (f'/genre={genre}' if genre else '') + '/json'
                data = get(url).json()
                dump(cache, data)
                time.sleep(1)
            ids.update(e['id']['attributes']['im:id'] for e in data['feed'].get('entry', []))
        except (requests.RequestException, ValueError, KeyError) as error:
            print('Discovery skipped', genre, str(error), flush=True)
    candidates = []
    sorted_ids = sorted(ids)
    for i in range(0, len(sorted_ids), 40):
        candidates.extend(apple({'id': ','.join(sorted_ids[i:i+40]), 'entity': 'podcast', 'country': 'BR'}))
    unique = {x['feedUrl']: show_from_lookup(x) for x in candidates if x.get('feedUrl')}
    print('Feed candidates', len(unique), flush=True)
    verified, rejected = [], []
    with futures.ThreadPoolExecutor(max_workers=8) as pool:
        pending = {pool.submit(verify_feed, show): show for show in unique.values()}
        for i, future in enumerate(futures.as_completed(pending), 1):
            result = future.result()
            if result:
                verified.append(result)
            else:
                rejected.append(pending[future]['feedUrl'])
            if i % 100 == 0:
                print('Feeds checked', i, 'verified', len(verified), flush=True)
    existing = {x['feedUrl']: x for x in original}
    added = [s for s in verified if s['country'] == 'BR' and s['feedUrl'] not in existing]
    # Keep stable IDs for favorites/subscriptions of existing catalog entries.
    for show in verified:
        if show['feedUrl'] in existing:
            show['id'] = existing[show['feedUrl']]['id']
            if show['country'] == 'GLOBAL':
                show['country'] = existing[show['feedUrl']].get('country', 'GLOBAL')
            existing[show['feedUrl']].update(show)
    for show in added:
        existing[show['feedUrl']] = show
    dump(ASSETS / 'podcasts_catalog.json', list(existing.values()))
    dump(REPORT / 'verified-podcasts.json', verified)
    dump(REPORT / 'podcast-expansion.json', {'before': len(original), 'beforeBrazil': before_brazil,
         'addedBrazil': len(added), 'after': len(existing), 'afterBrazil': sum(x.get('country') == 'BR' for x in existing.values()),
         'rejectedFeeds': rejected, 'checkedAt': TODAY.isoformat()})
    print('Brazilian podcasts added', len(added), flush=True)


HISTORY_URL = 'https://newsroom.spotify.com/2026-04-23/spotify-20-most-streamed-music-podcasts-audiobooks/'


def build_historical_rankings():
    html = get(HISTORY_URL).text
    (REPORT / 'spotify-all-time.html').write_text(html, encoding='utf-8')
    page = BeautifulSoup(html, 'html.parser')
    heading = next(h for h in page.find_all(['h2', 'h3']) if h.get_text(strip=True) == 'Most streamed podcasts of all time')
    links = heading.find_next('ol').select('a[href*="open.spotify.com/show/"]')
    if len(links) != 20:
        raise ValueError(f'Expected 20 official historical positions, got {len(links)}')
    catalog = json.loads((ASSETS / 'podcasts_catalog.json').read_text(encoding='utf-8'))
    entries = []
    for rank, link in enumerate(links, 1):
        title = link.get_text(strip=True)
        match = next((x for x in catalog if norm(x['title']) == norm(title)), None)
        if match is None:
            found = apple({'term': title, 'entity': 'podcast', 'media': 'podcast', 'limit': 15})
            exact = next((x for x in found if x.get('feedUrl') and norm(x.get('collectionName', '')) == norm(title)), None)
            if exact:
                match = verify_feed(show_from_lookup(exact))
                if match:
                    catalog.append(match)
        entries.append({'rank': rank, 'title': title, 'sourceUrl': link['href'], 'show': match})
    dump(ASSETS / 'podcasts_catalog.json', catalog)
    unavailable = lambda source, url, reason: dict(source=source, sourceUrl=url, period='Histórico', note=reason, entries=[])
    snapshots = {
        'podcast_world': dict(source='Spotify', sourceUrl=HISTORY_URL, period='Todos os tempos · até 23/04/2026',
            note='20 posições publicadas pelo Spotify. Audiência acumulada na plataforma; inclui podcasts de todos os países.', entries=entries),
        'podcast_brazil': unavailable('Spotify', 'https://podcastcharts.byspotify.com/br/top-podcasts',
            'Top 20 histórico do Brasil indisponível. O ranking público por país é semanal, não de todos os tempos.'),
        'radio_brazil': unavailable('RadiosNet', 'https://www.radios.com.br/estatistica',
            'Top 20 histórico do Brasil indisponível. A fonte publica estatísticas mensais, sem classificação acumulada de todos os tempos confirmada.'),
        'radio_world': unavailable('RadiosNet', 'https://www.radios.com.br/estatistica',
            'Top 20 histórico mundial indisponível. Não há classificação mundial acumulada confirmada nesta fonte.')
    }
    dump(ASSETS / 'media_rankings.json', snapshots)
    print('Historical ranking: 20 verified positions; playable RSS:', sum(bool(x['show']) for x in entries), flush=True)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['expand', 'rankings'])
    args = parser.parse_args()
    if args.mode == 'expand':
        expand_podcasts()
    else:
        from build_popularity_rankings import main
        main()
