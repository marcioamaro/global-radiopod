import asyncio
import json
import os
import re
import sys
import time
import urllib.parse
from typing import Any, Dict, List, Set, Tuple
import aiohttp

ALL_SOURCES_JSON_PATH = r"d:/global-radiopod/all_radio_sources.json"
CURATED_KT_PATH = r"d:/global-radiopod/app/src/main/java/com/example/data/repository/CuratedStations.kt"
COMPARISON_REPORT_PATH = r"d:/global-radiopod/radio_browser_comparison_report.json"

RADIO_BROWSER_MIRRORS = [
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json",
    "https://at1.api.radio-browser.info/json",
    "https://all.api.radio-browser.info/json"
]

MAX_CONCURRENT_CHECKS = 100
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
    if not name:
        return ""
    n = name.lower()
    n = re.sub(r'^(rádio|radio|fm|am)\s+', '', n)
    n = re.sub(r'\s+(fm|am)$', '', n)
    n = re.sub(r'[^a-z0-9]', '', n)
    return n

def normalize_url(url: str) -> str:
    if not url:
        return ""
    u = url.strip().lower()
    u = re.sub(r'^https?://', '', u)
    u = u.rstrip('/')
    return u

async def fetch_json_with_fallbacks(session: aiohttp.ClientSession, endpoint_path: str) -> List[Dict[str, Any]]:
    headers = {"User-Agent": "GlobalRadioPod/2.0 (Android Auto)"}
    for mirror in RADIO_BROWSER_MIRRORS:
        full_url = f"{mirror}/{endpoint_path.lstrip('/')}"
        try:
            timeout = aiohttp.ClientTimeout(total=20)
            async with session.get(full_url, headers=headers, timeout=timeout) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    print(f"Successfully fetched {len(data)} items from {mirror}")
                    return data
        except Exception as e:
            print(f"Mirror {mirror} failed: {e}. Trying next mirror...")
    return []

async def test_stream(session: aiohttp.ClientSession, url: str) -> Tuple[bool, str, int, str]:
    """Tests if audio stream actually works and returns (is_working, content_type, bitrate, error)"""
    if not url or not url.startswith("http"):
        return False, "", 0, "Invalid URL format"

    # Reject radiosnet/radios web pages if any
    if "radiosnet" in url.lower() or "radios.com.br" in url.lower():
        return False, "", 0, "Blocked radiosnet domain"

    start_t = time.time()
    try:
        timeout = aiohttp.ClientTimeout(
            sock_connect=CONNECT_TIMEOUT_SEC,
            sock_read=READ_TIMEOUT_SEC,
            total=CONNECT_TIMEOUT_SEC + READ_TIMEOUT_SEC
        )
        async with session.get(url, headers=REQUEST_HEADERS, timeout=timeout, allow_redirects=True) as response:
            c_type = response.headers.get("Content-Type", "").lower()
            icy_br = response.headers.get("icy-br", "")
            bitrate = int(icy_br) if icy_br.isdigit() else 128

            if response.status in (200, 206):
                chunk = await response.content.read(2048)
                bytes_read = len(chunk)
                if bytes_read > 0 and any(t in c_type for t in ["audio", "mpegurl", "m3u8", "ogg", "aac", "octet-stream"]):
                    return True, c_type, bitrate, ""
                elif bytes_read >= 512:
                    return True, c_type, bitrate, ""
                else:
                    return False, c_type, bitrate, f"Small payload: {bytes_read} bytes"
            else:
                return False, c_type, bitrate, f"HTTP {response.status}"
    except asyncio.TimeoutError:
        return False, "", 0, "Timeout"
    except Exception as e:
        return False, "", 0, str(type(e).__name__)

def state_code_from_name(state_name: str) -> str:
    if not state_name:
        return ""
    state_clean = state_name.strip()
    # Map common BR state names to UF
    uf_map = {
        "são paulo": "SP", "sao paulo": "SP", "sp": "SP",
        "rio de janeiro": "RJ", "rj": "RJ",
        "minas gerais": "MG", "mg": "MG",
        "rio grande do sul": "RS", "rs": "RS",
        "paraná": "PR", "parana": "PR", "pr": "PR",
        "santa catarina": "SC", "sc": "SC",
        "bahia": "BA", "ba": "BA",
        "ceará": "CE", "ceara": "CE", "ce": "CE",
        "pernambuco": "PE", "pe": "PE",
        "goiás": "GO", "goias": "GO", "go": "GO",
        "distrito federal": "DF", "brasilia": "DF", "df": "DF",
        "espírito santo": "ES", "espirito santo": "ES", "es": "ES",
        "rio grande do norte": "RN", "rn": "RN",
        "paraíba": "PB", "paraiba": "PB", "pb": "PB",
        "alagoas": "AL", "al": "AL",
        "maranhão": "MA", "maranhao": "MA", "ma": "MA",
        "piauí": "PI", "piaui": "PI", "pi": "PI",
        "sergipe": "SE", "se": "SE",
        "mato grosso": "MT", "mt": "MT",
        "mato grosso do sul": "MS", "ms": "MS",
        "pará": "PA", "para": "PA", "pa": "PA",
        "amazonas": "AM", "am": "AM",
        "rondônia": "RO", "rondonia": "RO", "ro": "RO",
        "acre": "AC", "ac": "AC",
        "amapá": "AP", "amapa": "AP", "ap": "AP",
        "roraima": "RR", "rr": "RR",
        "tocantins": "TO", "to": "TO"
    }
    low = state_clean.lower()
    return uf_map.get(low, state_clean[:2].upper() if len(state_clean) == 2 else "")

async def main():
    print("==================================================")
    print("RADIO BROWSER API - COMPARISON & DATABASE FEEDING")
    print("==================================================")

    # 1. Load current local base
    if not os.path.exists(ALL_SOURCES_JSON_PATH):
        print(f"Error: {ALL_SOURCES_JSON_PATH} not found!")
        return

    with open(ALL_SOURCES_JSON_PATH, "r", encoding="utf-8") as f:
        local_data = json.load(f)

    existing_stations = local_data.get("stations", [])
    print(f"Current local stations in catalog: {len(existing_stations)}")

    # Index existing URLs and names
    local_urls = set()
    local_url_to_station = {}
    for st in existing_stations:
        for u in st.get("all_stream_urls", []) + [st.get("primary_stream_url")]:
            if u:
                norm_u = normalize_url(u)
                local_urls.add(norm_u)
                local_url_to_station[norm_u] = st

    local_names_by_norm = {}
    for st in existing_stations:
        n = normalize_station_name(st.get("name", ""))
        if n and len(n) > 3:
            local_names_by_norm[n] = st

    connector = aiohttp.TCPConnector(limit=MAX_CONCURRENT_CHECKS, ssl=False, ttl_dns_cache=300)
    async with aiohttp.ClientSession(connector=connector) as session:
        # 2. Fetch Radio Browser: Brazil stations
        print("\nFetching Brazil stations from Radio Browser API...")
        br_endpoint = "stations/search?countrycode=BR&hidebroken=true&order=votes&reverse=true&limit=10000"
        rb_br_stations = await fetch_json_with_fallbacks(session, br_endpoint)
        print(f"Total Brazil stations fetched from Radio Browser: {len(rb_br_stations)}")

        # 3. Fetch Radio Browser: Top Worldwide stations (curated global)
        print("\nFetching Top Global stations from Radio Browser API...")
        global_endpoint = "stations/topvote/250?hidebroken=true"
        rb_global_stations = await fetch_json_with_fallbacks(session, global_endpoint)
        print(f"Total Global stations fetched from Radio Browser: {len(rb_global_stations)}")

        # Combine all candidates from Radio Browser
        combined_rb = []
        seen_rb_uuids = set()
        for s in rb_br_stations + rb_global_stations:
            uuid = s.get("stationuuid")
            if uuid and uuid not in seen_rb_uuids:
                seen_rb_uuids.add(uuid)
                combined_rb.append(s)

        print(f"\nTotal unique Radio Browser stations to compare: {len(combined_rb)}")

        # 4. Compare bases
        exact_url_matches = []
        name_matches = []
        enriched_count = 0
        new_candidates = []

        for r in combined_rb:
            stream = (r.get("url_resolved") or r.get("url") or "").strip()
            if not stream:
                continue

            norm_stream = normalize_url(stream)
            name = (r.get("name") or "").strip()
            norm_name = normalize_station_name(name)

            # Check if matching URL exists
            if norm_stream in local_urls:
                matched_st = local_url_to_station[norm_stream]
                exact_url_matches.append((r, matched_st))
                # Enrich existing station if metadata is better
                changed = False
                if not matched_st.get("favicon_url") and r.get("favicon"):
                    matched_st["favicon_url"] = r.get("favicon")
                    changed = True
                if not matched_st.get("homepage") and r.get("homepage"):
                    matched_st["homepage"] = r.get("homepage")
                    changed = True
                if r.get("votes", 0) > matched_st.get("votes", 0):
                    matched_st["votes"] = r.get("votes")
                    changed = True
                if changed:
                    enriched_count += 1
            # Check if matching normalized name exists
            elif norm_name in local_names_by_norm and len(norm_name) >= 5:
                matched_st = local_names_by_norm[norm_name]
                name_matches.append((r, matched_st))
                # Add alternative stream URL if working!
                alt_urls = matched_st.get("alternative_stream_urls", [])
                all_urls = matched_st.get("all_stream_urls", [])
                if stream not in all_urls and stream != matched_st.get("primary_stream_url"):
                    alt_urls.append(stream)
                    all_urls.append(stream)
                    matched_st["alternative_stream_urls"] = list(dict.fromkeys(alt_urls))
                    matched_st["all_stream_urls"] = list(dict.fromkeys(all_urls))
                    enriched_count += 1
            else:
                # Completely new candidate station!
                new_candidates.append(r)

        print(f"\n--- COMPARISON RESULTS ---")
        print(f"Direct stream URL matches: {len(exact_url_matches)}")
        print(f"Name/Station matches (enriching alternatives): {len(name_matches)}")
        print(f"Existing stations enriched with logos/votes/alternatives: {enriched_count}")
        print(f"New potential station candidates to test: {len(new_candidates)}")

        # 5. Validate new candidates with high votes or good bitrates
        # Sort candidates by votes descending so best stations are prioritized
        new_candidates.sort(key=lambda x: x.get("votes", 0), reverse=True)

        # Filter out candidates with 0 votes if they have no tags or empty names, prioritize reputable stations
        selected_candidates = []
        for c in new_candidates:
            c_name = c.get("name", "").strip()
            if not c_name or len(c_name) < 2:
                continue
            # Keep stations that have votes >= 1, or verified bitrate, or clear name
            if c.get("votes", 0) >= 1 or c.get("bitrate", 0) > 0 or c.get("countrycode") == "BR":
                selected_candidates.append(c)

        print(f"Testing {len(selected_candidates)} candidate streams in parallel (max concurrency: {MAX_CONCURRENT_CHECKS})...")

        semaphore = asyncio.Semaphore(MAX_CONCURRENT_CHECKS)

        async def check_candidate(cand: Dict[str, Any]) -> Dict[str, Any] | None:
            stream_url = (cand.get("url_resolved") or cand.get("url") or "").strip()
            async with semaphore:
                is_working, c_type, icy_br, err = await test_stream(session, stream_url)
                if not is_working and cand.get("url") and cand.get("url") != stream_url:
                    # Test original url as fallback
                    stream_url = cand.get("url").strip()
                    is_working, c_type, icy_br, err = await test_stream(session, stream_url)

                if is_working:
                    cand["_tested_url"] = stream_url
                    cand["_content_type"] = c_type
                    cand["_bitrate"] = icy_br or cand.get("bitrate", 128)
                    return cand
                return None

        tasks = [check_candidate(c) for c in selected_candidates]
        validated_results = await asyncio.gather(*tasks)

        working_new_stations = [r for r in validated_results if r is not None]
        print(f"Stream validation completed! Operable new stations found: {len(working_new_stations)}")

        # 6. Format and merge new stations into local data structure
        added_new_count = 0
        for cand in working_new_stations:
            clean_name = cand.get("name", "").strip()
            # Clean up duplicate radio tags in name
            clean_name = re.sub(r'\s+', ' ', clean_name)
            
            uuid_str = cand.get("stationuuid", "")
            id_slug = re.sub(r'[^a-z0-9_]', '', clean_name.lower().replace(" ", "_").replace("-", "_"))
            if not id_slug or len(id_slug) < 3:
                id_slug = f"rb_{uuid_str[:8]}"
            else:
                id_slug = f"rb_{id_slug[:24]}_{uuid_str[:6]}"

            # Determine state and city
            raw_state = cand.get("state", "").strip()
            uf = state_code_from_name(raw_state)
            city = raw_state if uf and raw_state != uf else ""
            if cand.get("countrycode") != "BR":
                country = cand.get("country") or "Global"
                country_code = cand.get("countrycode") or "XX"
            else:
                country = "Brasil"
                country_code = "BR"

            # Parse tags
            raw_tags = cand.get("tags", "")
            if isinstance(raw_tags, str):
                tags_list = [t.strip().lower() for t in re.split(r'[,;]', raw_tags) if t.strip()]
            else:
                tags_list = []
            if country_code == "BR" and "brasil" not in tags_list:
                tags_list.append("brasil")
            if uf and uf.lower() not in tags_list:
                tags_list.append(uf.lower())

            stream_url = cand.get("_tested_url") or cand.get("url_resolved") or cand.get("url")
            codec = cand.get("codec") or ("AAC" if "aac" in stream_url.lower() else "MP3")
            bitrate = cand.get("_bitrate") or cand.get("bitrate") or 128
            votes = cand.get("votes") or 500

            new_station_entry = {
                "id": id_slug,
                "name": clean_name,
                "country": country,
                "country_code": country_code,
                "state": uf or raw_state,
                "city": city,
                "codec": codec.upper(),
                "bitrate_kbps": int(bitrate),
                "votes": int(votes),
                "tags": tags_list,
                "primary_stream_url": stream_url,
                "alternative_stream_urls": [cand.get("url")] if cand.get("url") and cand.get("url") != stream_url else [],
                "all_stream_urls": [stream_url] + ([cand.get("url")] if cand.get("url") and cand.get("url") != stream_url else []),
                "favicon_url": cand.get("favicon", "").strip(),
                "homepage": cand.get("homepage", "").strip(),
                "stationuuid": uuid_str
            }

            existing_stations.append(new_station_entry)
            added_new_count += 1

        print(f"Successfully added {added_new_count} brand-new validated stations!")
        print(f"New total stations in catalog: {len(existing_stations)}")

        # 7. Sort stations:
        # Limeira / SP first, then by votes descending
        existing_stations.sort(key=lambda s: (
            s.get("city") == "Limeira",
            s.get("country_code") == "BR",
            s.get("votes", 0)
        ), reverse=True)

        # 8. Update all_radio_sources.json
        br_count = sum(1 for s in existing_stations if s.get("country_code") == "BR")
        intl_count = len(existing_stations) - br_count

        local_data["metadata"]["total_stations"] = len(existing_stations)
        local_data["metadata"]["brazil_stations_count"] = br_count
        local_data["metadata"]["international_stations_count"] = intl_count
        local_data["metadata"]["last_validated_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
        local_data["metadata"]["description"] = (
            f"Base de rádios do Global RadioPod sincronizada e alimentada com a Radio Browser API. "
            f"Total: {len(existing_stations)} emissoras com streams validados."
        )
        local_data["stations"] = existing_stations

        cleaned_data = clean_surrogates(local_data)
        with open(ALL_SOURCES_JSON_PATH, "w", encoding="utf-8", errors="replace") as f:
            json.dump(cleaned_data, f, ensure_ascii=False, indent=2)
        print(f"Updated {ALL_SOURCES_JSON_PATH} successfully!")

        # 9. Update CuratedStations.kt
        # Convert stations for Kotlin file
        curated_operable = []
        for s in existing_stations:
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

        update_curated_stations_kt(curated_operable)

        # 10. Generate comparison report
        report = {
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "radio_browser_stations_fetched": len(combined_rb),
            "radio_browser_br_stations": len(rb_br_stations),
            "radio_browser_global_top_stations": len(rb_global_stations),
            "initial_local_stations": len(existing_stations) - added_new_count,
            "exact_stream_url_matches": len(exact_url_matches),
            "name_or_brand_matches": len(name_matches),
            "existing_stations_enriched": enriched_count,
            "new_working_stations_added": added_new_count,
            "final_catalog_total": len(existing_stations),
            "final_brazil_stations": br_count,
            "final_international_stations": intl_count
        }

        with open(COMPARISON_REPORT_PATH, "w", encoding="utf-8") as f:
            json.dump(clean_surrogates(report), f, ensure_ascii=False, indent=2)
        print(f"Comparison report saved to {COMPARISON_REPORT_PATH}")

def escape_kt_string(val: str) -> str:
    if not val:
        return ""
    return val.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('\n', ' ').replace('\r', '')

def update_curated_stations_kt(stations: List[Dict[str, Any]], chunk_size: int = 150):
    with open(CURATED_KT_PATH, "r", encoding="utf-8") as f:
        existing_code = f.read()

    marker = "val CURATED_GLOBAL_STATIONS"
    marker_idx = existing_code.find(marker)
    if marker_idx == -1:
        marker2 = "private fun getStationsChunk_"
        marker_idx = existing_code.find(marker2)

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

if __name__ == "__main__":
    asyncio.run(main())
