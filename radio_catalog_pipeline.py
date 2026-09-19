#!/usr/bin/env python3
"""
radio_catalog_pipeline.py — Pipeline unificada para expansão, normalização,
deduplicação, validação e exportação do catálogo de rádios do Global RadioPod.
"""

import argparse
import asyncio
import datetime
import json
import os
import re
import shutil
import sys
import time
import unicodedata
import urllib.parse
from typing import Any, Dict, List, Optional, Set, Tuple

import aiohttp

RADIO_BROWSER_MIRRORS = [
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json",
    "https://at1.api.radio-browser.info/json",
    "https://all.api.radio-browser.info/json"
]

DEFAULT_USER_AGENT = "GlobalRadioPod/2.0 (Android Auto Media; CatalogPipeline)"
STREAM_REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Icy-MetaData": "1",
    "Range": "bytes=0-2048",
    "Accept": "*/*",
    "Connection": "keep-alive"
}

# --- Utilitários de Normalização ---

def remove_accents(input_str: str) -> str:
    if not input_str:
        return ""
    nfkd = unicodedata.normalize('NFKD', input_str)
    return "".join([c for c in nfkd if not unicodedata.combining(c)])

def normalize_text_for_matching(text: str) -> str:
    if not text:
        return ""
    cleaned = remove_accents(text).lower()
    cleaned = re.sub(r'[^a-z0-9\s]', ' ', cleaned)
    cleaned = re.sub(r'\s+', ' ', cleaned).strip()
    return cleaned

def normalize_station_name(name: str) -> str:
    norm = normalize_text_for_matching(name)
    norm = re.sub(r'^(radio|fm|am|webradio|web radio)\s+', '', norm)
    norm = re.sub(r'\s+(fm|am)$', '', norm)
    return norm.strip()

def normalize_url(url: str) -> str:
    if not url:
        return ""
    u = url.strip()
    # Remove fragment
    u = u.split('#')[0]
    try:
        parsed = urllib.parse.urlparse(u)
        scheme = parsed.scheme.lower()
        netloc = parsed.netloc.lower()
        # Remove default ports
        if netloc.endswith(":80") and scheme == "http":
            netloc = netloc[:-3]
        elif netloc.endswith(":443") and scheme == "https":
            netloc = netloc[:-4]
        return urllib.parse.urlunparse((scheme, netloc, parsed.path, parsed.params, parsed.query, ''))
    except Exception:
        return u.strip()

def clean_surrogates(obj: Any) -> Any:
    if isinstance(obj, str):
        return obj.encode('utf-8', 'surrogateescape').decode('utf-8', 'replace')
    elif isinstance(obj, dict):
        return {clean_surrogates(k): clean_surrogates(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [clean_surrogates(item) for item in obj]
    return obj

def escape_kt_string(s: Any) -> str:
    if s is None:
        return ""
    val = str(s).replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", " ").replace("\r", "")
    return clean_surrogates(val)

# --- Subcomando: DISCOVER ---

async def fetch_radio_browser(session: aiohttp.ClientSession, endpoint: str) -> List[Dict[str, Any]]:
    headers = {"User-Agent": DEFAULT_USER_AGENT}
    for mirror in RADIO_BROWSER_MIRRORS:
        url = f"{mirror}/{endpoint.lstrip('/')}"
        try:
            timeout = aiohttp.ClientTimeout(total=25)
            async with session.get(url, headers=headers, timeout=timeout) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    print(f"[{mirror}] Sucesso: {len(data)} registros encontrados")
                    return data
        except Exception as e:
            print(f"[{mirror}] Falha: {e}. Tentando próximo espelho...")
    return []

async def run_discover(args: argparse.Namespace):
    print(f"=== [DISCOVER] Iniciando descoberta de rádios ===")
    print(f"País: {args.country} | Estado: {args.state} | Cidade: {args.city} | Query: {args.query}")
    
    discovered_records: List[Dict[str, Any]] = []
    seen_ids: Set[str] = set()

    async with aiohttp.ClientSession() as session:
        endpoints_to_query = []
        
        if args.city:
            clean_c = urllib.parse.quote(args.city.strip())
            endpoints_to_query.append(f"stations/search?name={clean_c}&limit=100")
            endpoints_to_query.append(f"stations/bytag/{clean_c}?limit=100")
        
        if args.query:
            clean_q = urllib.parse.quote(args.query.strip())
            endpoints_to_query.append(f"stations/search?name={clean_q}&limit=100")
            
        if args.country:
            clean_country = urllib.parse.quote(args.country.strip())
            if args.state:
                clean_state = urllib.parse.quote(args.state.strip())
                endpoints_to_query.append(f"stations/bycountrycodeexact/{clean_country}?state={clean_state}&limit=500")
            if not args.city and not args.state:
                endpoints_to_query.append(f"stations/bycountrycodeexact/{clean_country}?limit=500")

        # Consultas sistemáticas
        for ep in endpoints_to_query:
            print(f"Consultando endpoint: {ep}")
            items = await fetch_radio_browser(session, ep)
            for item in items:
                uuid = item.get("stationuuid")
                if not uuid or uuid in seen_ids:
                    continue
                seen_ids.add(uuid)
                
                # Filtragem seletiva por cidade/estado quando especificados
                cand_city = (item.get("state") or "") + " " + (item.get("tags") or "") + " " + (item.get("name") or "")
                if args.city and normalize_text_for_matching(args.city) not in normalize_text_for_matching(cand_city):
                    continue

                raw_stream = item.get("url_resolved") or item.get("url") or ""
                if not raw_stream or not raw_stream.startswith("http"):
                    continue

                tags_list = [t.strip().lower() for t in (item.get("tags") or "").split(",") if t.strip()]

                record = {
                    "source": "radio-browser",
                    "source_record_id": uuid,
                    "source_url": f"https://www.radio-browser.info/history/{uuid}",
                    "discovered_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    "name_raw": item.get("name", "").strip(),
                    "name_normalized": normalize_station_name(item.get("name", "")),
                    "stream_urls": [raw_stream],
                    "homepage": item.get("homepage", "").strip(),
                    "favicon": item.get("favicon", "").strip(),
                    "country_code": (item.get("countrycode") or args.country or "BR").upper(),
                    "country_name": item.get("country") or "Brasil",
                    "state_raw": item.get("state", "").strip() or (args.state or ""),
                    "state_code": (args.state or item.get("state") or "").upper()[:2],
                    "city_raw": args.city or item.get("city", "").strip(),
                    "city_normalized": normalize_text_for_matching(args.city or item.get("city", "")),
                    "language_codes": [item.get("language") or "por"],
                    "tags": tags_list,
                    "codec": (item.get("codec") or "MP3").upper(),
                    "bitrate": int(item.get("bitrate") or 128),
                    "geo_lat": float(item.get("geo_lat")) if item.get("geo_lat") else None,
                    "geo_lon": float(item.get("geo_long")) if item.get("geo_long") else None,
                    "official_source": True,
                    "license_notes": "Radio Browser ODbL / Open Community",
                    "validation": {
                        "status": "pending",
                        "http_status": None,
                        "content_type": None,
                        "resolved_url": None,
                        "checked_at": None,
                        "latency_ms": None,
                        "error": None
                    }
                }
                discovered_records.append(record)

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(discovered_records), f, ensure_ascii=False, indent=2)
    print(f"=== [DISCOVER] Concluído! {len(discovered_records)} candidatos salvos em {args.output} ===")

# --- Subcomando: NORMALIZE ---

def run_normalize(args: argparse.Namespace):
    print(f"=== [NORMALIZE] Normalizando candidatos de {args.input} ===")
    with open(args.input, "r", encoding="utf-8") as f:
        records = json.load(f)

    normalized: List[Dict[str, Any]] = []
    for r in records:
        r_copy = dict(r)
        r_copy["name_raw"] = re.sub(r'\s+', ' ', r_copy.get("name_raw", "")).strip()
        r_copy["name_normalized"] = normalize_station_name(r_copy["name_raw"])
        
        # Normalização geográfica
        if r_copy.get("country_code") == "BR":
            r_copy["country_name"] = "Brasil"
        if r_copy.get("city_raw"):
            r_copy["city_raw"] = re.sub(r'\s+', ' ', r_copy["city_raw"]).strip()
            r_copy["city_normalized"] = normalize_text_for_matching(r_copy["city_raw"])

        # Normalização de URLs
        norm_urls = []
        for u in r_copy.get("stream_urls", []):
            clean_u = normalize_url(u)
            if clean_u and clean_u not in norm_urls:
                norm_urls.append(clean_u)
        r_copy["stream_urls"] = norm_urls
        normalized.append(r_copy)

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(normalized), f, ensure_ascii=False, indent=2)
    print(f"=== [NORMALIZE] Concluído! {len(normalized)} registros normalizados salvos em {args.output} ===")

# --- Subcomando: DEDUPLICATE ---

def run_deduplicate(args: argparse.Namespace):
    print(f"=== [DEDUPLICATE] Comparando candidatos com a base existente ===")
    with open(args.input, "r", encoding="utf-8") as f:
        candidates = json.load(f)

    existing_stations: List[Dict[str, Any]] = []
    if os.path.exists(args.existing):
        with open(args.existing, "r", encoding="utf-8") as f:
            raw_ex = json.load(f)
            existing_stations = raw_ex.get("stations", raw_ex if isinstance(raw_ex, list) else [])

    # Indexa existentes por stream_url e chave composta (nome_norm + cidade_norm + estado)
    existing_urls = set()
    existing_keys = set()
    existing_by_id = {}

    for s in existing_stations:
        sid = s.get("id")
        if sid:
            existing_by_id[sid] = s
        for u in s.get("all_stream_urls", []) + [s.get("primary_stream_url")]:
            if u:
                existing_urls.add(normalize_url(u))
        
        name_k = normalize_station_name(s.get("name", ""))
        city_k = normalize_text_for_matching(s.get("city", ""))
        state_k = (s.get("state") or "").upper().strip()
        if name_k:
            composite_key = f"{name_k}::{city_k}::{state_k}"
            existing_keys.add(composite_key)

    unique_candidates = []
    duplicates_report = []

    for cand in candidates:
        name_k = cand.get("name_normalized", "")
        city_k = cand.get("city_normalized", "")
        state_k = (cand.get("state_code") or "").upper().strip()
        comp_key = f"{name_k}::{city_k}::{state_k}"
        
        cand_urls = [normalize_url(u) for u in cand.get("stream_urls", [])]
        is_url_dup = any(u in existing_urls for u in cand_urls)
        is_name_dup = comp_key in existing_keys

        if is_url_dup or is_name_dup:
            duplicates_report.append({
                "candidate": cand.get("name_raw"),
                "reason": "URL duplicada" if is_url_dup else "Nome + Cidade + Estado já existente",
                "urls": cand_urls,
                "composite_key": comp_key
            })
        else:
            unique_candidates.append(cand)
            # Adiciona ao set para evitar duplicatas internas do mesmo lote
            existing_keys.add(comp_key)
            for u in cand_urls:
                existing_urls.add(u)

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(unique_candidates), f, ensure_ascii=False, indent=2)

    if args.report:
        os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump(clean_surrogates(duplicates_report), f, ensure_ascii=False, indent=2)

    print(f"=== [DEDUPLICATE] Concluído! {len(unique_candidates)} candidatos únicos salvos. Duplicatas descartadas: {len(duplicates_report)} ===")

# --- Subcomando: VALIDATE ---

async def test_single_stream(session: aiohttp.ClientSession, url: str, timeout_sec: float) -> Tuple[bool, int, str, str, float]:
    """Testa URL de áudio. Retorna (is_ok, http_status, content_type, erro, latencia_ms)"""
    start_t = time.time()
    try:
        timeout = aiohttp.ClientTimeout(sock_connect=timeout_sec, sock_read=timeout_sec, total=timeout_sec * 2)
        async with session.get(url, headers=STREAM_REQUEST_HEADERS, timeout=timeout, allow_redirects=True) as resp:
            elapsed_ms = (time.time() - start_t) * 1000
            c_type = (resp.headers.get("Content-Type") or "").lower()
            if resp.status in (200, 206):
                chunk = await resp.content.read(1024)
                if len(chunk) > 0:
                    return True, resp.status, c_type, "", elapsed_ms
                return False, resp.status, c_type, "Zero bytes lidos", elapsed_ms
            return False, resp.status, c_type, f"HTTP {resp.status}", elapsed_ms
    except asyncio.TimeoutError:
        return False, 0, "", "Timeout de conexão", (time.time() - start_t) * 1000
    except Exception as e:
        # Fallback síncrono via urllib para tratar servidores Shoutcast/Icecast legacy que não seguem RFC estrito de HTTP
        try:
            import ssl
            import urllib.request
            loop = asyncio.get_running_loop()
            def urllib_check():
                ctx = ssl.create_default_context()
                ctx.check_hostname = False
                ctx.verify_mode = ssl.CERT_NONE
                req = urllib.request.Request(url, headers={"User-Agent": "VLC/3.0.18 LibVLC/3.0.18", "Icy-MetaData": "1"})
                with urllib.request.urlopen(req, timeout=timeout_sec, context=ctx) as r:
                    status = getattr(r, 'status', 200)
                    ct = (r.headers.get('content-type') or "").lower()
                    chunk = r.read(512)
                    return len(chunk) > 0, status, ct
            ok, f_status, f_ct = await loop.run_in_executor(None, urllib_check)
            elapsed_ms = (time.time() - start_t) * 1000
            if ok:
                return True, f_status, f_ct, "", elapsed_ms
        except Exception:
            pass
        return False, 0, "", str(e), (time.time() - start_t) * 1000

async def run_validate(args: argparse.Namespace):
    print(f"=== [VALIDATE] Validando streams com concorrência máxima de {args.concurrency} ===")
    with open(args.input, "r", encoding="utf-8") as f:
        candidates = json.load(f)

    semaphore = asyncio.Semaphore(args.concurrency)
    validated_records = []
    validation_report = []

    async with aiohttp.ClientSession() as session:
        async def validate_candidate(cand: Dict[str, Any]):
            urls = cand.get("stream_urls", [])
            working_urls = []
            cand_report = {"name": cand.get("name_raw"), "urls_tested": []}

            for u in urls:
                async with semaphore:
                    is_ok, status, c_type, err, lat = await test_single_stream(session, u, args.timeout)
                    cand_report["urls_tested"].append({
                        "url": u,
                        "status": "working" if is_ok else "failed",
                        "http_code": status,
                        "content_type": c_type,
                        "error": err,
                        "latency_ms": round(lat, 1)
                    })
                    if is_ok:
                        working_urls.append(u)

            if working_urls:
                cand_copy = dict(cand)
                cand_copy["stream_urls"] = working_urls
                cand_copy["validation"] = {
                    "status": "working",
                    "checked_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    "working_urls_count": len(working_urls)
                }
                validated_records.append(cand_copy)
            
            validation_report.append(cand_report)

        tasks = [validate_candidate(c) for c in candidates]
        await asyncio.gather(*tasks)

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(validated_records), f, ensure_ascii=False, indent=2)

    if args.report:
        os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump(clean_surrogates(validation_report), f, ensure_ascii=False, indent=2)

    print(f"=== [VALIDATE] Concluído! {len(validated_records)} rádios com streams verificados. Rejeitadas: {len(candidates) - len(validated_records)} ===")

# --- Subcomando: MERGE ---

def run_merge(args: argparse.Namespace):
    print(f"=== [MERGE] Realizando fusão determinística com {args.existing} ===")
    print(f"Modo Dry-Run: {args.dry_run}")

    with open(args.existing, "r", encoding="utf-8") as f:
        existing_data = json.load(f)

    existing_stations = existing_data.get("stations", existing_data if isinstance(existing_data, list) else [])
    with open(args.validated, "r", encoding="utf-8") as f:
        validated_candidates = json.load(f)

    existing_ids = {s["id"] for s in existing_stations if "id" in s}
    added_stations = []
    enriched_stations = []

    for cand in validated_candidates:
        name = cand.get("name_raw", "Rádio")
        slug = re.sub(r'[^a-z0-9]+', '_', cand.get("name_normalized") or "radio").strip('_')
        state = cand.get("state_code", "BR").lower()
        city = re.sub(r'[^a-z0-9]+', '_', cand.get("city_normalized", "")).strip('_')
        cand_id = f"br_{state}_{city}_{slug}".strip("_")
        
        # Garante id único
        final_id = cand_id
        counter = 1
        while final_id in existing_ids:
            final_id = f"{cand_id}_{counter}"
            counter += 1
        existing_ids.add(final_id)

        stream_urls = cand.get("stream_urls", [])
        primary_url = stream_urls[0] if stream_urls else ""
        alts = stream_urls[1:] if len(stream_urls) > 1 else []

        new_station_entry = {
            "id": final_id,
            "name": name,
            "country": cand.get("country_name", "Brasil"),
            "country_code": cand.get("country_code", "BR"),
            "state": cand.get("state_code", ""),
            "city": cand.get("city_raw", ""),
            "codec": cand.get("codec", "MP3"),
            "bitrate_kbps": cand.get("bitrate", 128),
            "votes": 5000,
            "tags": cand.get("tags", []),
            "primary_stream_url": primary_url,
            "alternative_stream_urls": alts,
            "total_sources_count": len(stream_urls),
            "all_stream_urls": stream_urls,
            "favicon_url": cand.get("favicon", ""),
            "homepage": cand.get("homepage", "")
        }
        added_stations.append(new_station_entry)

    final_stations = list(existing_stations) + added_stations

    merge_report = {
        "timestamp": datetime.datetime.now().isoformat(),
        "dry_run": args.dry_run,
        "previous_total": len(existing_stations),
        "added_count": len(added_stations),
        "new_total": len(final_stations),
        "added_stations": [{"id": s["id"], "name": s["name"], "city": s["city"]} for s in added_stations]
    }

    if args.report:
        os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump(clean_surrogates(merge_report), f, ensure_ascii=False, indent=2)

    if not args.dry_run:
        # Backup com timestamp antes de qualquer gravação
        backup_path = f"{args.existing}.bak_{int(time.time())}"
        shutil.copyfile(args.existing, backup_path)
        print(f"Backup de segurança gerado: {backup_path}")

        final_catalog = {
            "metadata": {
                "generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "description": "Base de rádios do Global RadioPod atualizada e validada.",
                "total_stations": len(final_stations),
                "brazil_stations_count": sum(1 for s in final_stations if s.get("country_code") == "BR"),
                "international_stations_count": sum(1 for s in final_stations if s.get("country_code") != "BR")
            },
            "stations": final_stations
        }
        with open(args.output or args.existing, "w", encoding="utf-8") as f:
            json.dump(clean_surrogates(final_catalog), f, ensure_ascii=False, indent=2)
        print(f"=== [MERGE] Base atualizada gravada com sucesso em {args.output or args.existing}! ===")
    else:
        print(f"=== [MERGE] Modo DRY-RUN ativo. Nenhuma alteração gravada. ({len(added_stations)} estações a serem adicionadas) ===")

# --- Subcomando: EXPORT-ANDROID ---

def run_export_android(args: argparse.Namespace):
    print(f"=== [EXPORT-ANDROID] Exportando catálogo para o formato Android ({args.format}) ===")
    with open(args.input, "r", encoding="utf-8") as f:
        data = json.load(f)

    stations = data.get("stations", data if isinstance(data, list) else [])

    if args.format == "curated-kt":
        chunk_size = 150
        with open(args.output, "r", encoding="utf-8") as f:
            existing_code = f.read()

        marker = "private fun getStationsChunk_"
        marker_idx = existing_code.find(marker)
        if marker_idx == -1:
            marker = "val CURATED_GLOBAL_STATIONS"
            marker_idx = existing_code.find(marker)

        if marker_idx == -1:
            print(f"Erro: Marcador de chunks não encontrado em {args.output}")
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
                tags_val = st.get("tags", [])
                tags_str = ", ".join(tags_val) if isinstance(tags_val, list) else str(tags_val)
                url = st.get("primary_stream_url") or st.get("streamUrl") or ""
                alts = st.get("alternative_stream_urls") or []
                alt_urls = [u for u in alts if u and u != url]

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
                votes = int(st.get("votes") or 5000)

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

        with open(args.output, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))
        print(f"=== [EXPORT-ANDROID] CuratedStations.kt atualizado com {len(stations)} estações em {num_chunks} chunks! ===")

    elif args.format == "room-json":
        os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(clean_surrogates(stations), f, ensure_ascii=False, indent=2)
        print(f"=== [EXPORT-ANDROID] JSON de catálogo salvo para Room em {args.output} ===")

# --- CLI Parser ---

def main():
    parser = argparse.ArgumentParser(description="Pipeline de expansão do catálogo de rádios do Global RadioPod")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # discover
    p_disc = subparsers.add_parser("discover", help="Descobre candidatos em diretórios e APIs")
    p_disc.add_argument("--country", default="BR", help="Código do país ISO 3166-1 alpha-2")
    p_disc.add_argument("--state", default=None, help="Código ou nome do estado/UF")
    p_disc.add_argument("--city", default=None, help="Nome do município")
    p_disc.add_argument("--query", default=None, help="Termo de busca complementar")
    p_disc.add_argument("--sources", default="radio-browser", help="Fontes separadas por vírgula")
    p_disc.add_argument("--output", required=True, help="Arquivo JSON de saída para candidatos brutos")

    # normalize
    p_norm = subparsers.add_parser("normalize", help="Normaliza nomes, URLs e metadados")
    p_norm.add_argument("--input", required=True, help="Arquivo JSON de candidatos brutos")
    p_norm.add_argument("--output", required=True, help="Arquivo JSON de saída normalizado")

    # deduplicate
    p_dedup = subparsers.add_parser("deduplicate", help="Deduplica candidatos contra a base existente")
    p_dedup.add_argument("--input", required=True, help="Arquivo JSON normalizado")
    p_dedup.add_argument("--existing", default="all_radio_sources.json", help="Arquivo da base existente")
    p_dedup.add_argument("--output", required=True, help="Arquivo JSON de candidatos únicos")
    p_dedup.add_argument("--report", default=None, help="Arquivo JSON de relatório de duplicatas")

    # validate
    p_val = subparsers.add_parser("validate", help="Valida streams ao vivo com controle de concorrência")
    p_val.add_argument("--input", required=True, help="Arquivo JSON de candidatos")
    p_val.add_argument("--concurrency", type=int, default=15, help="Máximo de conexões simultâneas")
    p_val.add_argument("--timeout", type=float, default=6.0, help="Timeout de conexão em segundos")
    p_val.add_argument("--retries", type=int, default=2, help="Número de tentativas por URL")
    p_val.add_argument("--output", required=True, help="Arquivo JSON de estações com streams válidos")
    p_val.add_argument("--report", default=None, help="Arquivo JSON de relatório de validação")

    # merge
    p_merge = subparsers.add_parser("merge", help="Fusão determinística na base de rádios")
    p_merge.add_argument("--existing", default="all_radio_sources.json", help="Base principal existente")
    p_merge.add_argument("--validated", required=True, help="Arquivo JSON de candidatos validados")
    p_merge.add_argument("--output", default=None, help="Arquivo de destino (se omitido, sobrescreve existing)")
    p_merge.add_argument("--report", default=None, help="Arquivo JSON de relatório de merge")
    p_merge.add_argument("--dry-run", action="store_true", default=False, help="Apenas simula a fusão sem alterar arquivos")
    p_merge.add_argument("--apply", dest="dry_run", action="store_false", help="Aplica a fusão definitivamente na base")

    # export-android
    p_exp = subparsers.add_parser("export-android", help="Exporta para formatos suportados pelo app Android")
    p_exp.add_argument("--input", default="all_radio_sources.json", help="Arquivo JSON da base final")
    p_exp.add_argument("--format", choices=["curated-kt", "room-json"], default="curated-kt", help="Formato de exportação")
    p_exp.add_argument("--output", required=True, help="Caminho do arquivo de destino (CuratedStations.kt ou JSON)")

    args = parser.parse_args()

    if args.command == "discover":
        asyncio.run(run_discover(args))
    elif args.command == "normalize":
        run_normalize(args)
    elif args.command == "deduplicate":
        run_deduplicate(args)
    elif args.command == "validate":
        asyncio.run(run_validate(args))
    elif args.command == "merge":
        run_merge(args)
    elif args.command == "export-android":
        run_export_android(args)

if __name__ == "__main__":
    main()
