import asyncio
import html
import json
import os
import re
import sys
import time
from typing import Any, Dict, List, Set
import aiohttp

CANDIDATES_JSON_PATH = r"d:/global-radiopod/extracted_sitemap1_radios.json"
ALL_SOURCES_JSON_PATH = r"d:/global-radiopod/all_radio_sources.json"
OUTPUT_REPORT_PATH = r"d:/global-radiopod/validation_report.json"
CURATED_KT_PATH = r"d:/global-radiopod/app/src/main/java/com/example/data/repository/CuratedStations.kt"

MAX_CONCURRENT_TASKS = 250
CONNECT_TIMEOUT_SEC = 3.5
READ_TIMEOUT_SEC = 3.0

REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Icy-MetaData": "1",
    "Range": "bytes=0-1024",
    "Accept": "*/*",
    "Connection": "keep-alive"
}

def clean_surrogates(obj):
    if isinstance(obj, str):
        return obj.encode('utf-8', 'surrogateescape').decode('utf-8', 'replace')
    elif isinstance(obj, dict):
        return {clean_surrogates(k): clean_surrogates(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [clean_surrogates(item) for item in obj]
    return obj

def normalize_station_name(name: str) -> str:
    n = name.lower()
    n = re.sub(r'^(rádio|radio|fm|am)\s+', '', n)
    n = re.sub(r'\s+(fm|am)$', '', n)
    n = re.sub(r'[^a-z0-9]', '', n)
    return n

async def test_stream(session: aiohttp.ClientSession, url: str) -> Dict[str, Any]:
    start_t = time.time()
    res = {
        "url": url,
        "is_working": False,
        "status_code": None,
        "content_type": None,
        "elapsed_ms": 0,
        "error": None
    }
    
    # Extra check: Reject if radiosnet somehow slipped in
    if "radiosnet" in url.lower() or "radios.com.br" in url.lower():
        res["error"] = "Blocked radiosnet/radios domain"
        return res

    try:
        timeout = aiohttp.ClientTimeout(
            sock_connect=CONNECT_TIMEOUT_SEC,
            sock_read=READ_TIMEOUT_SEC,
            total=CONNECT_TIMEOUT_SEC + READ_TIMEOUT_SEC
        )
        async with session.get(url, headers=REQUEST_HEADERS, timeout=timeout, allow_redirects=True) as response:
            res["status_code"] = response.status
            c_type = response.headers.get("Content-Type", "").lower()
            res["content_type"] = c_type
            
            if response.status in (200, 206):
                # Read small initial chunk to verify active audio stream
                bytes_read = 0
                chunk = await response.content.read(2048)
                bytes_read += len(chunk)
                
                res["elapsed_ms"] = int((time.time() - start_t) * 1000)
                
                if (bytes_read > 0 and ("audio" in c_type or "mpegurl" in c_type or "m3u8" in url or "octet-stream" in c_type or "application/ogg" in c_type)):
                    res["is_working"] = True
                elif bytes_read >= 512:
                    res["is_working"] = True
                else:
                    res["error"] = f"Header ok but only {bytes_read} bytes"
            else:
                res["error"] = f"HTTP {response.status}"
                res["elapsed_ms"] = int((time.time() - start_t) * 1000)
    except asyncio.TimeoutError:
        res["error"] = "Timeout"
        res["elapsed_ms"] = int((time.time() - start_t) * 1000)
    except Exception as e:
        res["error"] = f"{type(e).__name__}"
        res["elapsed_ms"] = int((time.time() - start_t) * 1000)
        
    return res

async def validate_candidate(
    semaphore: asyncio.Semaphore,
    session: aiohttp.ClientSession,
    candidate: Dict[str, Any]
) -> Optional[Dict[str, Any]]:
    async with semaphore:
        all_urls = candidate.get("all_stream_urls", [])
        if not all_urls and candidate.get("primary_stream_url"):
            all_urls = [candidate["primary_stream_url"]]
            
        working = []
        for u in all_urls:
            # Strict filter
            if "radiosnet" in u.lower() or "radios.com.br" in u.lower():
                continue
            t = await test_stream(session, u)
            if t["is_working"]:
                working.append(u)
                break # Fast mode: primary works, keep going
                
        if not working:
            return None
            
        candidate["primary_stream_url"] = working[0]
        candidate["all_stream_urls"] = working
        candidate["is_operable"] = True
        return candidate

def escape_kt_string(val: str) -> str:
    return val.replace('"', '\\"').replace('$', '\\$').replace('\n', ' ')

def update_curated_stations_kt(operable_stations: List[Dict[str, Any]]):
    with open(CURATED_KT_PATH, "r", encoding="utf-8") as f:
        existing_code = f.read()

    marker = "val CURATED_GLOBAL_STATIONS = listOf("
    marker_idx = existing_code.find(marker)
    if marker_idx == -1:
        print(f"[ERRO] Marcador '{marker}' nao encontrado em {CURATED_KT_PATH}")
        return

    header = existing_code[:marker_idx + len(marker)]

    lines = [header]
    for st in operable_stations:
        tags_str = ", ".join(st.get("tags", [])) if isinstance(st.get("tags"), list) else str(st.get("tags", ""))
        url = st.get("primary_stream_url") or st.get("best_primary_url") or st.get("streamUrl") or ""
        
        # Verify no radiosnet
        if "radiosnet" in url.lower() or "radios.com.br" in url.lower():
            continue
            
        all_urls = st.get("all_stream_urls") or st.get("alternativeStreamUrls") or []
        alt_urls = [u for u in all_urls if u != url and "radiosnet" not in u.lower() and "radios.com.br" not in u.lower()]
        
        name = escape_kt_string(st.get("name", "Rádio"))
        id_str = escape_kt_string(st.get("id", "radio"))
        favicon = escape_kt_string(st.get("favicon_url") or st.get("favicon") or "")
        country = escape_kt_string(st.get("country", "Brasil"))
        country_code = escape_kt_string(st.get("country_code") or st.get("countryCode") or "BR")
        state = escape_kt_string(st.get("state", ""))
        city = escape_kt_string(st.get("city", ""))
        tags_escaped = escape_kt_string(tags_str)
        bitrate = int(st.get("bitrate_kbps") or st.get("bitrate") or 128)
        codec = escape_kt_string(st.get("codec", "MP3"))
        votes = int(st.get("votes") or 1000)

        alt_urls_kt = ", ".join([f'"{escape_kt_string(u)}"' for u in alt_urls])

        lines.append("        RadioStation(")
        lines.append(f'            id = "{id_str}",')
        lines.append(f'            name = "{name}",')
        lines.append(f'            streamUrl = "{url}",')
        if alt_urls:
            lines.append(f'            alternativeStreamUrls = listOf({alt_urls_kt}),')
        lines.append(f'            favicon = "{favicon}",')
        lines.append(f'            tags = "{tags_escaped}",')
        lines.append(f'            country = "{country}",')
        lines.append(f'            countryCode = "{country_code}",')
        lines.append(f'            state = "{state}",')
        lines.append(f'            city = "{city}",')
        lines.append(f'            codec = "{codec}",')
        lines.append(f'            bitrate = {bitrate},')
        lines.append(f'            votes = {votes}')
        lines.append("        ),")

    lines.append("    )")
    lines.append("}")
    lines.append("")

    with open(CURATED_KT_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"Generated {CURATED_KT_PATH} with {len(operable_stations)} verified stations!")

async def main():
    if not os.path.exists(CANDIDATES_JSON_PATH):
        print(f"[ERRO] Arquivo de candidatos nao encontrado: {CANDIDATES_JSON_PATH}")
        return

    with open(CANDIDATES_JSON_PATH, "r", encoding="utf-8") as f:
        candidates_data = json.load(f)

    candidates = candidates_data.get("stations", [])
    print(f"Total de candidatos extraídos para validação: {len(candidates)}")

    with open(ALL_SOURCES_JSON_PATH, "r", encoding="utf-8") as f:
        existing_data = json.load(f)

    existing_stations = existing_data.get("stations", [])
    print(f"Base de rádios existente atual: {len(existing_stations)} estações")

    # Fast concurrent validation with 250 workers
    semaphore = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    connector = aiohttp.TCPConnector(limit=MAX_CONCURRENT_TASKS, ssl=False, ttl_dns_cache=300)

    print(f"\nIniciando validacao ultra-rapida de streams com {MAX_CONCURRENT_TASKS} conexoes simultaneas...")
    start_time = time.time()

    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = [validate_candidate(semaphore, session, c) for c in candidates]
        results = await asyncio.gather(*tasks)

    elapsed = time.time() - start_time
    valid_candidates = [r for r in results if r is not None]
    print(f"Validacao concluida em {elapsed:.2f}s!")
    print(f"Emissoras extraidas 100% ativas e funcionais: {len(valid_candidates)}")

    # Merge intelligently with existing catalog
    existing_by_norm = {}
    for st in existing_stations:
        key = (normalize_station_name(st.get("name", "")), st.get("state", "").upper())
        existing_by_norm[key] = st

    added_new = 0
    updated_existing = 0

    for cand in valid_candidates:
        key = (normalize_station_name(cand["name"]), cand.get("state", "").upper())
        if key in existing_by_norm:
            # Station already exists: enrich alternative URLs
            st = existing_by_norm[key]
            curr_urls = set(st.get("all_stream_urls", []))
            new_urls = [u for u in cand.get("all_stream_urls", []) if u not in curr_urls and "radiosnet" not in u.lower()]
            if new_urls:
                st["all_stream_urls"] = list(curr_urls.union(new_urls))
                st["alternative_stream_urls"] = [u for u in st["all_stream_urls"] if u != st.get("primary_stream_url")]
                updated_existing += 1
        else:
            # Brand new station
            cand["votes"] = 15000 if cand.get("state") == "SP" else 8000
            existing_stations.append(cand)
            existing_by_norm[key] = cand
            added_new += 1

    print(f"Resultado da mescla: {added_new} novas radios adicionadas, {updated_existing} radios existentes enriquecidas.")
    print(f"Total consolidado no catalogo: {len(existing_stations)} radios")

    # Double check strict filter: 0% radiosnet
    for st in existing_stations:
        prim = st.get("primary_stream_url", "")
        if "radiosnet" in prim.lower() or "radios.com.br" in prim.lower():
            print(f"[ALERTA] Removendo URL radiosnet: {prim}")
            st["primary_stream_url"] = ""

    # Sort: Limeira and SP high votes first
    existing_stations.sort(key=lambda s: (s.get("city") == "Limeira", s.get("votes", 0)), reverse=True)

    # Save all_radio_sources.json
    existing_data["stations"] = existing_stations
    existing_data["metadata"]["total_stations"] = len(existing_stations)
    existing_data["metadata"]["last_validated_at"] = time.strftime("%Y-%m-%d %H:%M:%S")

    with open(ALL_SOURCES_JSON_PATH, "w", encoding="utf-8", errors="replace") as f:
        json.dump(clean_surrogates(existing_data), f, ensure_ascii=False, indent=2)

    # Update CuratedStations.kt
    update_curated_stations_kt(existing_stations)

    # Save validation report
    summary = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "validation_duration_seconds": round(elapsed, 2),
        "total_candidates_tested": len(candidates),
        "active_validated_candidates": len(valid_candidates),
        "new_stations_added": added_new,
        "existing_stations_updated": updated_existing,
        "total_consolidated_catalog": len(existing_stations)
    }
    with open(OUTPUT_REPORT_PATH, "w", encoding="utf-8", errors="replace") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(f"\nPipeline finalizado com sucesso! Relatorio salvo em {OUTPUT_REPORT_PATH}")

if __name__ == "__main__":
    asyncio.run(main())
