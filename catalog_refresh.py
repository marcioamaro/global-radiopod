"""Discover globally, decode audio with PyAV, quarantine failures and export.

Run from the repository root: python catalog_refresh.py
Requires aiohttp and av. TLS verification is always enabled. No guessed URLs.
"""
import asyncio
import datetime as dt
import hashlib
import html
import io
import json
import re
import unicodedata
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit, urljoin

import aiohttp
import av

ROOT = Path(__file__).resolve().parent
REPORT = ROOT / 'reports/catalog-refresh-20260922'
ATUAL = 'https://ice.fabricahost.com.br/radioatual941'

def clean(value):
    if isinstance(value, str):
        return ' '.join(''.join(c for c in unicodedata.normalize('NFC', html.unescape(value))
                               if not unicodedata.category(c).startswith('C')).split())
    if isinstance(value, list):
        return [clean(x) for x in value]
    if isinstance(value, dict):
        return {k: clean(v) for k, v in value.items()}
    return value

def norm(value):
    return ''.join(c for c in unicodedata.normalize('NFKD', value.casefold()) if c.isalnum())

def urlkey(value):
    p = urlsplit(value.strip())
    return urlunsplit(('', p.netloc.lower().removesuffix(':80').removesuffix(':443'), p.path or '/', p.query, ''))

def identity(s):
    return (norm(s.get('name', '')), s.get('country_code', '').upper(), norm(s.get('state', '')), norm(s.get('city', '')))

def dump(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

def decode(data):
    with av.open(io.BytesIO(data)) as container:
        audio = next((s for s in container.streams if s.type == 'audio'), None)
        if audio is None:
            raise ValueError('No audio track')
        seconds = 0
        for frame in container.decode(audio):
            seconds += frame.samples / frame.sample_rate
            if seconds >= 1:
                return audio.codec_context.name.upper(), round(seconds, 3)
        raise ValueError('Less than one second of decoded audio')

async def probe(session, url, depth=0):
    if depth > 3 or urlsplit(url).scheme not in ('http', 'https'):
        raise ValueError('Unsupported URL or playlist recursion')
    async with session.get(url, headers={'Cache-Control': 'no-cache', 'Pragma': 'no-cache'}, timeout=aiohttp.ClientTimeout(total=22, sock_connect=8, sock_read=8)) as r:
        if r.status not in (200, 206):
            raise ValueError(f'HTTP {r.status}')
        ctype = r.headers.get('Content-Type', '').lower()
        if 'html' in ctype:
            raise ValueError('HTML instead of audio')
        data = bytearray()
        decoded = None
        async for chunk in r.content.iter_chunked(8192):
            data.extend(chunk)
            if len(data) >= 16384 and not data.lstrip().startswith((b'#EXTM3U', b'[playlist]')):
                try:
                    decoded = await asyncio.to_thread(decode, bytes(data))
                    break
                except Exception:
                    pass
            if len(data) >= 65536:
                break
        resolved = str(r.url)
    if data.lstrip().startswith((b'#EXTM3U', b'[playlist]')):
        text = data.decode('utf-8-sig', errors='replace')
        links = [line.strip() for line in text.splitlines() if line.strip() and not line.startswith('#')]
        if text.lower().startswith('[playlist]'):
            links = re.findall(r'^File\d+=(.+)$', text, re.M | re.I)
        links = [u for u in links if not u.startswith(('Title', 'Length', 'Number', 'Version'))]
        if not links:
            raise ValueError('Empty playlist')
        # Encrypted and fragmented HLS need init/key fetching; do not claim validation.
        if '#EXT-X-KEY' in text or '#EXT-X-MAP' in text:
            raise ValueError('HLS requires full player validation')
        result = await probe(session, urljoin(resolved, links[0]), depth + 1)
        if '#EXT-X-' in text:
            result['resolved_url'] = resolved
            result['codec'] = 'HLS'
        return result
    codec, seconds = decoded or await asyncio.to_thread(decode, bytes(data))
    return {'resolved_url': resolved, 'codec': codec, 'decoded_seconds': seconds,
            'sample_bytes': len(data), 'content_type': ctype, 'http_status': 200,
            'checked_at': dt.datetime.now(dt.timezone.utc).isoformat()}

async def main():
    REPORT.mkdir(parents=True, exist_ok=True)
    data = json.loads((ROOT / 'all_radio_sources.json').read_text(encoding='utf-8'))
    dump(REPORT / 'original-catalog.json', data)
    old = data['stations']
    candidates = []
    timeout = aiohttp.ClientTimeout(total=90)
    async with aiohttp.ClientSession(timeout=timeout, trust_env=True,
            headers={'User-Agent': 'GlobalRadioPod-Catalog/1.0', 'Accept': '*/*', 'Icy-MetaData': '0'},
            connector=aiohttp.TCPConnector(limit=48, limit_per_host=3)) as session:
        for host in ['nl1', 'de1', 'at1', 'all']:
            try:
                async with session.get(f'https://{host}.api.radio-browser.info/json/stations/search', params={'hidebroken': 'true', 'limit': 100000}) as r:
                    r.raise_for_status()
                    candidates = await r.json()
                if candidates:
                    break
            except Exception as e:
                print('Discovery', host, type(e).__name__, str(e)[:150], flush=True)
        dump(REPORT / 'discovered.json', candidates)
        print('Discovered:', len(candidates), 'Existing:', len(old), flush=True)
        seen_urls = set()
        seen_names = set()
        work = []
        duplicates = []
        for s in old:
            s = clean(s)
            if ATUAL in s.get('all_stream_urls', []) or s.get('primary_stream_url') == ATUAL:
                s.update(name='Rádio Atual São Paulo 94,1 FM', country='Brasil', country_code='BR', state='SP', city='São Paulo')
            work.append((s, False))
        if not any(s.get('primary_stream_url') == ATUAL for s, _ in work):
            work.insert(0, ({'id': 'br_sp_radio_atual_941_fm', 'name': 'Rádio Atual São Paulo 94,1 FM', 'country': 'Brasil', 'country_code': 'BR', 'state': 'SP', 'city': 'São Paulo', 'primary_stream_url': ATUAL}, False))
        for c in candidates:
            url = c.get('url_resolved') or c.get('url') or ''
            if not c.get('name') or not url.startswith(('http://', 'https://')):
                continue
            work.append((clean({'id': 'rb_' + c['stationuuid'], 'name': c['name'], 'country': 'Brasil' if c.get('countrycode') == 'BR' else c.get('country', ''), 'country_code': c.get('countrycode', ''), 'state': c.get('state', ''), 'city': c.get('city', ''), 'primary_stream_url': url, 'homepage': c.get('homepage', ''), 'favicon_url': c.get('favicon', ''), 'tags': c.get('tags', '').split(','), 'bitrate_kbps': c.get('bitrate', 0), 'votes': c.get('votes', 0), 'source_url': 'https://www.radio-browser.info/history/' + c['stationuuid']}), True))
        unique = []
        for s, new in work:
            u = s.get('primary_stream_url', '')
            if not u or urlkey(u) in seen_urls or identity(s) in seen_names:
                duplicates.append(s)
                continue
            seen_urls.add(urlkey(u)); seen_names.add(identity(s)); unique.append((s, new))
        print('Unique to validate:', len(unique), flush=True)
        sem = asyncio.Semaphore(40)
        completed = 0
        async def check(item):
            nonlocal completed
            s, new = item
            errors = []
            async with sem:
                urls = list(dict.fromkeys([s['primary_stream_url']] + s.get('alternative_stream_urls', []) + s.get('all_stream_urls', [])))
                for url in urls:
                    for attempt in range(2):
                        try:
                            result = await probe(session, url)
                            # Two independently opened streams must decode before admission.
                            result = await probe(session, url)
                            s.update(primary_stream_url=url, alternative_stream_urls=[], all_stream_urls=[url], total_sources_count=1, codec=result['codec'], validation=result)
                            completed += 1
                            if completed % 100 == 0:
                                print('Validated', completed, '/', len(unique), flush=True)
                            return s, new, None
                        except Exception as e:
                            errors.append({'url': url, 'error': type(e).__name__ + ': ' + str(e)[:200]})
                completed += 1
                if completed % 100 == 0:
                    print('Validated', completed, '/', len(unique), flush=True)
                return s, new, errors
        results = await asyncio.gather(*(check(item) for item in unique))
    accepted, rejected, added = [], [], []
    seen_urls, seen_ids = set(), set()
    for s, new, errors in results:
        if errors:
            rejected.append({'station': s, 'new_candidate': new, 'errors': errors})
        elif urlkey(s['primary_stream_url']) in seen_urls or s['id'] in seen_ids:
            duplicates.append(s)
        else:
            seen_urls.add(urlkey(s['primary_stream_url'])); seen_ids.add(s['id'])
            accepted.append(s)
            if new:
                added.append(s)
    dump(REPORT / 'rejected.json', rejected)
    dump(REPORT / 'duplicates.json', duplicates)
    dump(REPORT / 'added.json', added)
    summary = {'original': len(old), 'discovered': len(candidates), 'tested': len(unique), 'accepted': len(accepted), 'added': len(added), 'rejected': len(rejected), 'duplicates': len(duplicates), 'atual_valid': any(s['primary_stream_url'] == ATUAL for s in accepted), 'checked_at': dt.datetime.now(dt.timezone.utc).isoformat()}
    dump(REPORT / 'summary.json', summary)
    print(json.dumps(summary), flush=True)
    # A network-wide failure must never wipe the catalog.
    if len(accepted) < len(old) * .5:
        dump(REPORT / 'validated-catalog.json', {'stations': accepted})
        raise RuntimeError('Less than half of original count validated; catalog untouched. Inspect network/rejections.')
    # Both existing and new entries must pass validation before export.
    data['stations'] = accepted
    data['metadata'].update(total_stations=len(accepted), brazil_stations=sum(s.get('country_code') == 'BR' for s in accepted), last_updated=summary['checked_at'])
    dump(ROOT / 'all_radio_sources.json', data)
    dump(ROOT / 'app/src/main/assets/radio_catalog.json', data)

if __name__ == '__main__':
    asyncio.run(main())
