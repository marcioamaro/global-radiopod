import html
import json
import os
import re
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any, Dict, List, Optional

LOCAL_SITEMAP_PATH = r"d:/global-radiopod/sitemap_radio1.xml"
OUTPUT_EXTRACTED_FILE = r"d:/global-radiopod/extracted_sitemap1_radios.json"

MAX_STATIONS_TO_PROCESS = 1500
MAX_WORKERS = 10
REQUEST_TIMEOUT_SEC = 6.0

HEADERS = {
    "User-Agent": "RadiosNet/2.8.0 (Android; SDK 34)",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
    "Connection": "keep-alive"
}

def clean_text(text: str) -> str:
    if not text:
        return ""
    text = html.unescape(text)
    return re.sub(r'\s+', ' ', text).strip()

def parse_location(loc_raw: str):
    city = ""
    state = ""
    country = "Brasil"
    country_code = "BR"

    clean = clean_text(loc_raw)
    if " - " in clean:
        parts = clean.split(" - ")
        country = clean_text(parts[-1])
        sub = parts[0]
    else:
        sub = clean

    if "/" in sub:
        sub_parts = sub.split("/")
        city = clean_text(sub_parts[0])
        state = clean_text(sub_parts[1])
    else:
        city = sub

    if country.lower() in ("brasil", "brazil"):
        country = "Brasil"
        country_code = "BR"
        uf_match = re.search(r'\b([A-Z]{2})\b', state)
        if uf_match:
            state = uf_match.group(1)
    else:
        country_code = "XX"

    return city, state, country, country_code

def extract_station_info(page_html: str, page_url: str) -> Optional[Dict[str, Any]]:
    if "Desativada:</b>" in page_html or "A rádio está fora do ar ou foi encerrada" in page_html:
        return None

    id_match = re.search(r'/(\d+)(?:[?#]|$)', page_url)
    station_num_id = id_match.group(1) if id_match else str(int(time.time()))

    name_match = re.search(r'<h1>(.*?)</h1>', page_html, re.DOTALL)
    if not name_match:
        return None
    name = clean_text(name_match.group(1))
    if not name or "A transmissão" in name or len(name) < 2:
        return None

    loc_match = re.search(r'<h2>(.*?)</h2>', page_html, re.DOTALL)
    city, state, country, country_code = parse_location(loc_match.group(1) if loc_match else "")

    stream_candidates = []
    # Pattern 1: 'url':'...'
    url_matches = re.findall(r"['\"]url['\"]\s*:\s*['\"]([^'\"]+)['\"]", page_html)
    for u in url_matches:
        u = u.strip()
        if u.startswith("http"):
            stream_candidates.append(u)

    # Pattern 2: src: "..."
    src_matches = re.findall(r"src\s*:\s*['\"](https?://[^'\"]+)['\"]", page_html)
    for s in src_matches:
        s = s.strip()
        stream_candidates.append(s)

    # STRICT ANTI-RADIOSNET AND ANTI-PROXY FILTER
    valid_streams = []
    for s in stream_candidates:
        s_lower = s.lower()
        if "radiosnet" in s_lower:
            continue
        if "radios.com.br" in s_lower:
            continue
        if any(bad in s_lower for bad in ["datatables", "facebook", "google", "twitter", ".json", ".js", ".png", ".jpg", ".gif", ".flv"]):
            continue
        if s not in valid_streams:
            valid_streams.append(s)

    if not valid_streams:
        return None

    logo_match = re.search(r'<img[^>]+src=[\'"](https?://img\.radios\.com\.br/radio/[^\'"]+)[\'"]', page_html)
    favicon_url = logo_match.group(1) if logo_match else ""

    genre_matches = re.findall(r'<span class=[\'"]label label-[^\'"]*[\'"]>(.*?)</span>', page_html)
    tags = [clean_text(g).lower() for g in genre_matches if clean_text(g)]
    if not tags:
        tags = ["geral"]

    if city:
        tags.append(city.lower())
    if state:
        tags.append(state.lower())
    if country_code == "BR":
        tags.append("brasil")

    return {
        "id": f"radios_br_{station_num_id}",
        "radios_id": station_num_id,
        "name": name,
        "primary_stream_url": valid_streams[0],
        "alternative_stream_urls": valid_streams[1:],
        "all_stream_urls": valid_streams,
        "city": city,
        "state": state,
        "country": country,
        "country_code": country_code,
        "favicon_url": favicon_url,
        "homepage": page_url,
        "tags": list(dict.fromkeys(tags)),
        "bitrate_kbps": 128,
        "codec": "AAC" if any(".aac" in u.lower() or "aac" in u.lower() for u in valid_streams) else "MP3",
        "votes": 5000
    }

def fetch_single_station(url: str, retries: int = 2) -> Optional[Dict[str, Any]]:
    req = urllib.request.Request(url, headers=HEADERS)
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT_SEC) as resp:
                if resp.status == 200:
                    html_content = resp.read().decode("utf-8", errors="ignore")
                    return extract_station_info(html_content, url)
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < retries:
                time.sleep(2.0 * (attempt + 1))
                continue
            break
        except Exception:
            break
    return None

def main():
    print(f"Lendo sitemap local: {LOCAL_SITEMAP_PATH} ...")
    if not os.path.exists(LOCAL_SITEMAP_PATH):
        print(f"[ERRO] Arquivo sitemap nao encontrado em {LOCAL_SITEMAP_PATH}")
        return

    with open(LOCAL_SITEMAP_PATH, "r", encoding="utf-8") as f:
        xml_content = f.read()

    station_urls = re.findall(r'<loc>\s*(https?://www\.radios\.com\.br/aovivo/[^\s<]+)\s*</loc>', xml_content)
    print(f"Total de URLs no sitemap: {len(station_urls)}")

    target_urls = station_urls[:MAX_STATIONS_TO_PROCESS]
    total_target = len(target_urls)
    print(f"Escopo configurado: {total_target} emissoras para extração.")
    print(f"Iniciando extração com ThreadPool ({MAX_WORKERS} workers)...")

    results = []
    start_time = time.time()
    last_checkpoint_time = time.time()

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        future_to_url = {executor.submit(fetch_single_station, u): u for u in target_urls}
        completed = 0

        for future in as_completed(future_to_url):
            completed += 1
            station_info = future.result()
            if station_info:
                results.append(station_info)

            if completed % 200 == 0 or completed == total_target:
                curr_elapsed = time.time() - start_time
                rate = completed / curr_elapsed if curr_elapsed > 0 else 0
                print(f"[{completed}/{total_target}] ({rate:.1f} req/s) Extraídas {len(results)} rádios válidas (0% radiosnet)...")

            # Checkpoint save every 500 completions
            if completed % 500 == 0:
                with open(OUTPUT_EXTRACTED_FILE, "w", encoding="utf-8", errors="replace") as f:
                    json.dump({
                        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
                        "processed_count": completed,
                        "total_candidates": len(results),
                        "stations": results
                    }, f, ensure_ascii=False, indent=2)

    total_elapsed = time.time() - start_time
    print("\n" + "=" * 60)
    print(f"EXTRAÇÃO CONCLUÍDA EM {total_elapsed:.2f}s!")
    print(f"Total de URLs Processadas: {completed}")
    print(f"Total de Rádios com Streams Diretos (Sem Radiosnet): {len(results)}")
    print("=" * 60)

    # Final save
    with open(OUTPUT_EXTRACTED_FILE, "w", encoding="utf-8", errors="replace") as f:
        json.dump({
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "processed_count": completed,
            "total_candidates": len(results),
            "stations": results
        }, f, ensure_ascii=False, indent=2)

    print(f"Salvo com sucesso em: {OUTPUT_EXTRACTED_FILE}")

if __name__ == "__main__":
    main()
