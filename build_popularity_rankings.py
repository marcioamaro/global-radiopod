"""Build reproducible, playable Top 20 snapshots. Source evidence stays in reports.

python build_popularity_rankings.py
"""
import base64
import concurrent.futures
import json
import re
from pathlib import Path
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from refresh_media_rankings import ASSETS, REPORT, TODAY, apple, dump, norm, show_from_lookup, verify_feed

CATALOG = json.loads((ASSETS / 'radio_catalog.json').read_text(encoding='utf-8'))['stations']
BY_ID = {s['id']: s for s in CATALOG}
BASE = 'https://mytuner-radio.com'


def page(url, key):
    path = REPORT / (key + '.html')
    if not path.exists():
        response = requests.get(url, timeout=30)
        response.raise_for_status()
        path.write_text(response.text, encoding='utf-8')
    return BeautifulSoup(path.read_text(encoding='utf-8'), 'html.parser')


def public_player(url, key):
    """Read the same public playlist used by the site's unauthenticated player."""
    soup = page(url, key)
    raw = str(soup)
    playlist = json.loads(re.search(r'formatPlaylist\((\[.*?\])\)', raw).group(1))
    timestamp = soup.select_one('#last-update')['data-timestamp']
    key_bytes = (timestamp * 32)[:32].encode('ascii')
    urls = []
    for item in playlist:
        decryptor = Cipher(algorithms.AES(key_bytes), modes.CFB(bytes.fromhex(item['iv']))).decryptor()
        decoded = decryptor.update(base64.b64decode(item['cipher'])) + decryptor.finalize()
        decoded = decoded[:-decoded[-1]].decode('utf-8')
        if decoded.startswith(('https://', 'http://')):
            urls.append(decoded)
    assert urls, url
    return urls


def snapshot(entries, note, period='Popularidade'):
    assert len(entries) == 20
    return dict(source='', sourceUrl='', period=period, note=note, entries=entries)


def main():
    rankings = json.loads((ASSETS / 'media_rankings.json').read_text(encoding='utf-8'))
    evidence = {'checkedAt': TODAY.isoformat()}
    # Explicit identity matches prevent homonymous local affiliates from replacing a station.
    mappings = {
        '413397': 'br_sp_065', '406264': 'antena1_sp', '408793': 'br_sp_297',
        '412548': 'br_sp_349', '417407': 'br_sp_112', '410316': 'dial_radio-105-fm',
        '406561': 'dial_radio-bandeirantes-107-3-fm', '412906': 'br_sao_paulo_metropolitana_98_5',
        '416389': 'br_sp_184', '395019': 'dial_radio-nativa-95-3-fm', '395698': 'br_sp_177',
        '445374': 'br_sp_276', '409309': 'dial_radio-saudade-99-7-fm', '418702': 'br_sp_137',
        '455526': 'rb_rádio_tmc_1001_fm_4912da', '395846': 'dial_radio-kiss-92-5-fm'
    }
    url = BASE + '/pt/radio/pais/brazil-estacoes'
    links = [a for a in page(url, 'mytuner-br').select('a.no-select')
             if re.search(r'/radio/[^/]+-\d+/$', a.get('href', ''))][:20]
    entries, audit = [], []
    for rank, link in enumerate(links, 1):
        sid = re.search(r'-(\d+)/$', link['href']).group(1)
        title = link.get_text(' ', strip=True)
        source_url = urljoin(BASE, link['href'])
        if sid in mappings:
            station = BY_ID[mappings[sid]]
            if sid == '412906':
                station = dict(station, primary_stream_url=public_player(source_url, 'mytuner-radio-' + sid)[0],
                               alternative_stream_urls=[])
        else:
            streams = public_player(source_url, 'mytuner-radio-' + sid)
            station = dict(id='popular_br_' + sid, name=title, primary_stream_url=streams[0],
                           alternative_stream_urls=streams[1:], country='Brasil', country_code='BR',
                           state='', city='', tags='', codec='', bitrate_kbps=0,
                           favicon_url=link.select_one('img').get('data-src', ''))
        entries.append(dict(rank=rank, title=title, station=station))
        audit.append(dict(rank=rank, title=title, sourceUrl=source_url, stationId=station['id']))
    rankings['radio_brazil'] = snapshot(entries, '20 rádios em ordem de popularidade no Brasil.')
    evidence['radio_brazil'] = dict(sourceUrl=url, entries=audit)

    # Global click counts are actual directory observations, not fabricated catalog votes.
    world = json.loads((REPORT / 'global-radio-popularity.json').read_text(encoding='utf-8'))
    entries, audit, seen = [], [], set()
    for source in sorted(world, key=lambda s: -s['clickcount']):
        identity = (norm(source['name']), source['countrycode'])
        if identity in seen or not source['lastcheckok']:
            continue
        seen.add(identity)
        match = next((s for s in CATALOG if s['primary_stream_url'] in (source['url'], source['url_resolved'])), None)
        if source['name'] == 'RMC FR':
            match = BY_ID['rb_03c97140-ba60-4f41-8b98-b560ca0927cc']
        station = match or dict(id='rb_' + source['stationuuid'], name=source['name'].strip(),
            primary_stream_url=source['url'], alternative_stream_urls=[], favicon_url=source['favicon'],
            homepage=source['homepage'], country=source['country'], country_code=source['countrycode'],
            state=source['state'], city='', tags=source['tags'], codec=source['codec'], bitrate_kbps=source['bitrate'])
        rank = len(entries) + 1
        entries.append(dict(rank=rank, title=source['name'].strip(), station=station))
        audit.append(dict(rank=rank, title=source['name'], clicks=source['clickcount'], stationId=station['id']))
        if rank == 20:
            break
    rankings['radio_world'] = snapshot(entries, '20 rádios por popularidade de acessos online no mundo.')
    evidence['radio_world'] = dict(sourceUrl='https://de1.api.radio-browser.info/json/stations/topclick/100', entries=audit)

    # Brazilian chart order is preserved; only podcasts with public RSS audio are included.
    catalog = json.loads((ASSETS / 'podcasts_catalog.json').read_text(encoding='utf-8'))
    source_url = 'https://itunes.apple.com/br/rss/toppodcasts/limit=100/json'
    chart_path = REPORT / 'popularity-podcast-br.json'
    if not chart_path.exists():
        r = requests.get(source_url, timeout=30)
        r.raise_for_status()
        dump(chart_path, r.json())
    chart = json.loads(chart_path.read_text(encoding='utf-8'))['feed']['entry']
    entries, audit = [], []
    for position, source in enumerate(chart, 1):
        sid = source['id']['attributes']['im:id']
        title = source['im:name']['label']
        show = next((s for s in catalog if s['id'] == 'itunes_' + sid or norm(s['title']) == norm(title)), None)
        if not show:
            results = apple({'id': sid, 'entity': 'podcast', 'country': 'BR'})
            if results and results[0].get('feedUrl'):
                show = verify_feed(show_from_lookup(results[0]))
                if show:
                    catalog.append(show)
        if not show or not show.get('feedUrl') or show.get('country') != 'BR':
            continue
        rank = len(entries) + 1
        entries.append(dict(rank=rank, title=show['title'], show=show))
        audit.append(dict(rank=rank, sourceRank=position, title=title, showId=show['id']))
        if rank == 20:
            break
    rankings['podcast_brazil'] = snapshot(entries, '20 podcasts brasileiros mais populares com episódios disponíveis.')
    evidence['podcast_brazil'] = dict(sourceUrl=source_url, entries=audit)
    evidence['podcast_world'] = dict(sourceUrl='https://newsroom.spotify.com/2026-04-23/spotify-20-most-streamed-music-podcasts-audiobooks/',
                                   period=rankings['podcast_world']['period'])
    for entry in rankings['podcast_world']['entries']:
        entry.pop('sourceUrl', None)
    rankings['podcast_world'].update(source='', sourceUrl='', note='20 podcasts em ordem de popularidade histórica mundial.')
    dump(ASSETS / 'media_rankings.json', rankings)
    dump(ASSETS / 'podcasts_catalog.json', catalog)
    dump(REPORT / 'popularity-evidence.json', evidence)
    print({key: len(value['entries']) for key, value in rankings.items()}, flush=True)


if __name__ == '__main__':
    main()
