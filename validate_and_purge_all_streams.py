import asyncio
import json
import os
import re
import sys
import time
from typing import Any, Dict, List, Set, Tuple
import aiohttp

ALL_SOURCES_JSON_PATH = r"d:/global-radiopod/all_radio_sources.json"
CURATED_KT_PATH = r"d:/global-radiopod/app/src/main/java/com/example/data/repository/CuratedStations.kt"
PURGE_REPORT_PATH = r"d:/global-radiopod/stream_purge_report.json"

MAX_CONCURRENT_TASKS = 100
CONNECT_TIMEOUT_SEC = 4.0
READ_TIMEOUT_SEC = 3.5

REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Icy-MetaData": "1",
    "Range": "bytes=0-2048",
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
    if not name:
        return ""
    n = name.lower()
    n = re.sub(r'^(rádio|radio|fm|am)\s+', '', n)
    n = re.sub(r'\s+(fm|am)$', '', n)
    return re.sub(r'[^a-z0-9]', '', n)

def normalize_url(url: str) -> str:
    if not url:
        return ""
    u = url.strip().lower()
    u = re.sub(r'^https?://', '', u)
    u = u.rstrip('/')
    return u

async def test_stream(session: aiohttp.ClientSession, url: str, is_retry: bool = False) -> Tuple[bool, str, int, str]:
    if not url or not url.startswith("http"):
        return False, "", 0, "Invalid URL"

    if "radiosnet" in url.lower() or "radios.com.br" in url.lower():
        return False, "", 0, "Blocked domain"

    conn_timeout = CONNECT_TIMEOUT_SEC + (2.0 if is_retry else 0.0)
    read_timeout = READ_TIMEOUT_SEC + (2.0 if is_retry else 0.0)

    try:
        timeout = aiohttp.ClientTimeout(
            sock_connect=conn_timeout,
            sock_read=read_timeout,
            total=conn_timeout + read_timeout
        )
        async with session.get(url, headers=REQUEST_HEADERS, timeout=timeout, allow_redirects=True) as response:
            c_type = response.headers.get("Content-Type", "").lower()
            icy_br = response.headers.get("icy-br", "")
            bitrate = int(icy_br) if icy_br.isdigit() else 128

            if response.status in (200, 206):
                # Check for HTML responses (usually error pages disguised as 200)
                if "text/html" in c_type:
                    return False, c_type, 0, "Returned HTML error page instead of stream"

                chunk = await response.content.read(2048)
                bytes_read = len(chunk)

                if bytes_read > 0 and any(t in c_type for t in ["audio", "mpegurl", "m3u8", "ogg", "aac", "octet-stream"]):
                    return True, c_type, bitrate, ""
                elif bytes_read >= 512:
                    return True, c_type, bitrate, ""
                else:
                    return False, c_type, bitrate, f"Empty or tiny stream payload ({bytes_read} bytes)"
            else:
                return False, c_type, 0, f"HTTP {response.status}"
    except asyncio.TimeoutError:
        return False, "", 0, "Timeout"
    except Exception as e:
        return False, "", 0, str(type(e).__name__)

async def fetch_dialtuner_streams(session: aiohttp.ClientSession) -> List[Dict[str, Any]]:
    print("\nConsulting DialTuner API for audio streams and backup links...")
    endpoints = [
        "https://dialtuner.com.br/wp-json/longwave/v1/discover?page=1&per_page=100",
        "https://dialtuner.com.br/wp-json/longwave/v1/discover?page=2&per_page=100",
        "https://dialtuner.com.br/wp-json/longwave/v1/ranking",
        "https://dialtuner.com.br/wp-json/longwave/v1/trending"
    ]
    dial_stations = []
    seen_ids = set()
    headers = {"User-Agent": "GlobalRadioPod/2.0"}

    for ep in endpoints:
        try:
            timeout = aiohttp.ClientTimeout(total=10)
            async with session.get(ep, headers=headers, timeout=timeout) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    items = data.get("stations") if isinstance(data, dict) else data
                    if isinstance(items, list):
                        for it in items:
                            sid = it.get("id") or it.get("slug")
                            if sid and sid not in seen_ids:
                                seen_ids.add(sid)
                                dial_stations.append(it)
        except Exception as e:
            print(f"Error fetching DialTuner endpoint {ep}: {e}")

    print(f"Retrieved {len(dial_stations)} stations from DialTuner")
    return dial_stations

def escape_kt_string(val: str) -> str:
    if not val:
        return ""
    return val.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('\n', ' ').replace('\r', '')

def update_curated_stations_kt(stations: List[Dict[str, Any]], chunk_size: int = 150):
    with open(CURATED_KT_PATH, "r", encoding="utf-8") as f:
        existing_code = f.read()

    marker = "private fun getStationsChunk_"
    marker_idx = existing_code.find(marker)
    if marker_idx == -1:
        marker = "val CURATED_GLOBAL_STATIONS"
        marker_idx = existing_code.find(marker)

    if marker_idx == -1:
        print(f"Error: Marker not found in {CURATED_KT_PATH}")
        return

    header = existing_code[:marker_idx].rstrip()

    num_chunks = (len(stations) + chunk_size - 1) // chunk_size
    chunk_func_names = []

    lines = [header, ""]

    for i in range(num_chunks):
        func_name = f"getStationsChunk_{i+1}"
        chunk_func_names.append(func_name)
        chunk_stations = stations[i * chunk_size : (i + 1) * chunk_size]

        lines.append(f"    private fun {func_name}(): List<RadioStation> = listOf(")
        for st in chunk_stations:
            tags_str = ", ".join(st.get("tags", []))
            url = st.get("best_primary_url") or st.get("primary_stream_url") or ""
            alt_urls = [u for u in st.get("working_urls", []) if u and u != url]
            name = escape_kt_string(st.get("name", "Rádio"))
            id_str = escape_kt_string(st.get("id", "radio"))
            favicon = escape_kt_string(st.get("favicon_url", ""))
            country = escape_kt_string(st.get("country", "Brasil"))
            country_code = escape_kt_string(st.get("country_code", "BR"))
            state = escape_kt_string(st.get("state", ""))
            city = escape_kt_string(st.get("city", ""))
            tags_escaped = escape_kt_string(tags_str)
            bitrate = int(st.get("bitrate_kbps") or 128)
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

        lines.append("    )\n")

    concat_chunks = " + ".join([f"{fn}()" for fn in chunk_func_names])
    lines.append("    val CURATED_GLOBAL_STATIONS: List<RadioStation> by lazy {")
    lines.append(f"        {concat_chunks}")
    lines.append("    }")
    lines.append("}")
    lines.append("")

    with open(CURATED_KT_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"Updated {CURATED_KT_PATH} with {len(stations)} curated stations across {num_chunks} chunks!")

async def main():
    print("================================================================")
    print("STREAM PURGE & VALIDATION ENGINE (100% OPERABLE CATALOG)")
    print("================================================================")

    if not os.path.exists(ALL_SOURCES_JSON_PATH):
        print(f"File not found: {ALL_SOURCES_JSON_PATH}")
        return

    with open(ALL_SOURCES_JSON_PATH, "r", encoding="utf-8") as f:
        local_data = json.load(f)

    existing_stations = local_data.get("stations", [])
    print(f"Initial stations in catalog: {len(existing_stations)}")

    connector = aiohttp.TCPConnector(limit=MAX_CONCURRENT_TASKS, ssl=False, ttl_dns_cache=300)
    async with aiohttp.ClientSession(connector=connector) as session:
        # 1. Fetch DialTuner streams to enrich existing stations with backup streams and add candidates
        dial_stations = await fetch_dialtuner_streams(session)

        # Index existing stations by normalized name
        name_to_station = {}
        for st in existing_stations:
            norm = normalize_station_name(st.get("name", ""))
            if norm and len(norm) >= 4:
                name_to_station[norm] = st

        dial_enriched_count = 0
        dial_new_candidates = []

        for d in dial_stations:
            d_name = d.get("name", "").strip()
            norm = normalize_station_name(d_name)
            d_stream = (d.get("stream") or "").strip()
            d_backup = (d.get("backup") or "").strip()

            if norm in name_to_station:
                st = name_to_station[norm]
                # Add streams as alternatives (without touching logo/favicon)
                curr_alts = st.get("alternative_stream_urls", [])
                curr_all = st.get("all_stream_urls", [])
                for u in [d_stream, d_backup]:
                    if u and u not in curr_all and u != st.get("primary_stream_url"):
                        curr_alts.append(u)
                        curr_all.append(u)
                        dial_enriched_count += 1
                st["alternative_stream_urls"] = list(dict.fromkeys(curr_alts))
                st["all_stream_urls"] = list(dict.fromkeys(curr_all))
            elif d_stream:
                dial_new_candidates.append(d)

        print(f"Existing stations enriched with DialTuner alternative/backup streams: {dial_enriched_count}")
        print(f"New candidate stations from DialTuner: {len(dial_new_candidates)}")

        # Add new candidate stations from DialTuner with default retro icon
        for d in dial_new_candidates:
            d_name = d.get("name", "").strip()
            d_stream = d.get("stream", "").strip()
            d_backup = d.get("backup", "").strip()
            slug = d.get("slug") or normalize_station_name(d_name)
            existing_stations.append({
                "id": f"dial_{slug[:28]}",
                "name": d_name,
                "country": "Brasil",
                "country_code": "BR",
                "state": d.get("region", ""),
                "city": d.get("city", ""),
                "codec": "AAC" if "aac" in d_stream.lower() else "MP3",
                "bitrate_kbps": 128,
                "votes": int(d.get("plays") or 5000),
                "tags": [g.lower() for g in d.get("genres", [])] + ["brasil"],
                "primary_stream_url": d_stream,
                "alternative_stream_urls": [d_backup] if d_backup else [],
                "all_stream_urls": [d_stream] + ([d_backup] if d_backup else []),
                "favicon_url": "", # Maintain current standard (no external image)
                "homepage": (d.get("socials") or {}).get("website", "")
            })

        # 2. Testing ALL stations and purging dead URLs
        print(f"\nBeginning deep parallel stream validation of all {len(existing_stations)} stations...")
        semaphore = asyncio.Semaphore(MAX_CONCURRENT_TASKS)

        async def validate_station_streams(st: Dict[str, Any]) -> Dict[str, Any]:
            all_urls = list(dict.fromkeys([u for u in st.get("all_stream_urls", []) + [st.get("primary_stream_url")] if u]))
            if not all_urls and st.get("primary_stream_url"):
                all_urls = [st["primary_stream_url"]]

            working_urls = []
            tested_urls = []

            for u in all_urls:
                async with semaphore:
                    is_ok, c_type, icy_br, err = await test_stream(session, u)
                    tested_urls.append((u, is_ok, err))
                    if is_ok:
                        working_urls.append(u)

            # Retry once if no URLs worked at all
            if not working_urls and all_urls:
                for u in all_urls:
                    async with semaphore:
                        is_ok, c_type, icy_br, err = await test_stream(session, u, is_retry=True)
                        if is_ok:
                            working_urls.append(u)
                            break

            return {
                "station": st,
                "all_tested": tested_urls,
                "working_urls": working_urls,
                "has_working_stream": len(working_urls) > 0
            }

        start_time = time.time()
        tasks = [validate_station_streams(s) for s in existing_stations]
        validation_results = await asyncio.gather(*tasks)
        elapsed = time.time() - start_time
        print(f"Stream testing finished in {elapsed:.2f}s!")

        # 3. Purge and rebuild final catalog
        final_operable_stations = []
        purged_stations = []
        promoted_count = 0
        dead_alts_removed_count = 0

        for r in validation_results:
            st = r["station"]
            working_urls = r["working_urls"]

            if not r["has_working_stream"]:
                purged_stations.append({
                    "id": st.get("id"),
                    "name": st.get("name"),
                    "state": st.get("state"),
                    "original_primary": st.get("primary_stream_url")
                })
                continue

            orig_primary = st.get("primary_stream_url")
            new_primary = working_urls[0]

            if orig_primary not in working_urls:
                promoted_count += 1

            # Remove dead alternative URLs
            new_alts = [u for u in working_urls if u != new_primary]
            orig_alts = st.get("alternative_stream_urls", [])
            dead_alts_removed_count += max(0, len(orig_alts) - len(new_alts))

            st["primary_stream_url"] = new_primary
            st["alternative_stream_urls"] = new_alts
            st["all_stream_urls"] = working_urls

            final_operable_stations.append(st)

        print("\n--- PURGE & SANITIZATION SUMMARY ---")
        print(f"Total stations evaluated: {len(existing_stations)}")
        print(f"Stations 100% OPERABLE retained: {len(final_operable_stations)}")
        print(f"Stations PURGED (all streams dead): {len(purged_stations)}")
        print(f"Stations saved by promoting working alternative: {promoted_count}")
        print(f"Dead alternative stream URLs removed: {dead_alts_removed_count}")

        # 4. Sort and update all_radio_sources.json
        final_operable_stations.sort(key=lambda s: (
            s.get("city") == "Limeira",
            s.get("country_code") == "BR",
            s.get("votes", 0)
        ), reverse=True)

        br_count = sum(1 for s in final_operable_stations if s.get("country_code") == "BR")
        intl_count = len(final_operable_stations) - br_count

        local_data["metadata"]["total_stations"] = len(final_operable_stations)
        local_data["metadata"]["brazil_stations_count"] = br_count
        local_data["metadata"]["international_stations_count"] = intl_count
        local_data["metadata"]["last_validated_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
        local_data["metadata"]["operable_stations_count"] = len(final_operable_stations)
        local_data["metadata"]["description"] = (
            f"Base de rádios do Global RadioPod higienizada e purgada. "
            f"Total de {len(final_operable_stations)} emissoras com streams 100% validados e funcionais."
        )
        local_data["stations"] = final_operable_stations

        cleaned_data = clean_surrogates(local_data)
        with open(ALL_SOURCES_JSON_PATH, "w", encoding="utf-8", errors="replace") as f:
            json.dump(cleaned_data, f, ensure_ascii=False, indent=2)
        print(f"Successfully saved clean catalog to {ALL_SOURCES_JSON_PATH}")

        # 5. Regenerate CuratedStations.kt
        curated_operable = []
        for s in final_operable_stations:
            curated_operable.append({
                "id": s.get("id"),
                "name": s.get("name"),
                "country": s.get("country", "Brasil"),
                "country_code": s.get("country_code", "BR"),
                "state": s.get("state", ""),
                "city": s.get("city", ""),
                "codec": s.get("codec", "MP3"),
                "bitrate_kbps": s.get("bitrate_kbps", 128),
                "votes": s.get("votes", 1000),
                "tags": s.get("tags", []),
                "favicon_url": s.get("favicon_url", ""),
                "homepage": s.get("homepage", ""),
                "best_primary_url": s.get("primary_stream_url"),
                "working_urls": s.get("all_stream_urls", [s.get("primary_stream_url")])
            })

        update_curated_stations_kt(curated_operable, chunk_size=150)

        # 6. Save purge report
        purge_report = {
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "initial_stations_evaluated": len(existing_stations),
            "final_operable_stations": len(final_operable_stations),
            "stations_purged": len(purged_stations),
            "promoted_alternatives_count": promoted_count,
            "dead_alternative_urls_removed": dead_alts_removed_count,
            "final_brazil_stations": br_count,
            "final_international_stations": intl_count,
            "purged_sample": purged_stations[:30]
        }

        with open(PURGE_REPORT_PATH, "w", encoding="utf-8", errors="replace") as f:
            json.dump(clean_surrogates(purge_report), f, ensure_ascii=False, indent=2)
        print(f"Purge report written to {PURGE_REPORT_PATH}")

if __name__ == "__main__":
    asyncio.run(main())
