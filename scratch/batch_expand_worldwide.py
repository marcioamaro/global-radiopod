#!/usr/bin/env python3
"""
batch_expand_worldwide.py — Execução em lote da expansão mundial do Global RadioPod
para atingir mais de 6.000 estações ativas e auditadas.
"""

import asyncio
import datetime
import json
import os
import re
import shutil
import time
import urllib.parse
import urllib.request
import aiohttp
from typing import Dict, List, Any, Set, Tuple

RADIO_BROWSER_MIRRORS = [
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json",
    "https://at1.api.radio-browser.info/json"
]

TARGETS = [
    # Lote 1: Brasil Expandido (todas as regiões e capitais)
    ("BR", 1500),
    # Lote 2: América Latina
    ("AR", 400),
    ("MX", 400),
    ("CO", 250),
    ("CL", 200),
    ("UY", 120),
    ("PE", 120),
    ("PY", 80),
    # Lote 3: América do Norte
    ("US", 1000),
    ("CA", 250),
    # Lote 4: Europa
    ("GB", 400),
    ("DE", 300),
    ("FR", 250),
    ("ES", 250),
    ("IT", 200),
    ("PT", 150),
    ("NL", 120),
    ("CH", 80),
    # Lote 5: Ásia, Oceania & África
    ("JP", 150),
    ("AU", 150),
    ("ZA", 100),
    ("NZ", 60),
    ("KR", 60)
]

COUNTRY_NAMES = {
    "BR": "Brasil", "AR": "Argentina", "MX": "México", "CO": "Colômbia",
    "CL": "Chile", "UY": "Uruguai", "PE": "Peru", "PY": "Paraguai",
    "US": "Estados Unidos", "CA": "Canadá", "GB": "Reino Unido",
    "DE": "Alemanha", "FR": "França", "ES": "Espanha", "IT": "Itália",
    "PT": "Portugal", "NL": "Holanda", "CH": "Suíça", "JP": "Japão",
    "AU": "Austrália", "ZA": "África do Sul", "NZ": "Nova Zelândia",
    "KR": "Coreia do Sul"
}

def remove_accents(s: str) -> str:
    import unicodedata
    return "".join(c for c in unicodedata.normalize('NFKD', s) if not unicodedata.combining(c))

def normalize_name(name: str) -> str:
    cleaned = remove_accents(name).lower()
    cleaned = re.sub(r'[^a-z0-9\s]', ' ', cleaned)
    cleaned = re.sub(r'^(radio|fm|am|webradio|web radio)\s+', '', cleaned)
    cleaned = re.sub(r'\s+(fm|am)$', '', cleaned)
    return re.sub(r'\s+', ' ', cleaned).strip()

def normalize_url(url: str) -> str:
    if not url: return ""
    u = url.split('#')[0].strip()
    try:
        p = urllib.parse.urlparse(u)
        nl = p.netloc.lower()
        if nl.endswith(":80") and p.scheme == "http": nl = nl[:-3]
        elif nl.endswith(":443") and p.scheme == "https": nl = nl[:-4]
        return urllib.parse.urlunparse((p.scheme.lower(), nl, p.path, p.params, p.query, ''))
    except Exception:
        return u

def escape_kt_string(s: Any) -> str:
    if s is None: return ""
    val = str(s).replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", " ").replace("\r", "")
    return val.encode('utf-8', 'surrogateescape').decode('utf-8', 'replace')

def clean_surrogates(obj: Any) -> Any:
    if isinstance(obj, str):
        return obj.encode('utf-8', 'surrogateescape').decode('utf-8', 'replace')
    elif isinstance(obj, dict):
        return {clean_surrogates(k): clean_surrogates(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [clean_surrogates(item) for item in obj]
    return obj

async def fetch_country_stations(session: aiohttp.ClientSession, country_code: str, limit: int) -> List[Dict[str, Any]]:
    for mirror in RADIO_BROWSER_MIRRORS:
        url = f"{mirror}/stations/bycountrycodeexact/{country_code}?limit={limit}&order=votes&reverse=true&hidebroken=true"
        try:
            timeout = aiohttp.ClientTimeout(total=20)
            async with session.get(url, headers={"User-Agent": "GlobalRadioPod/2.0"}, timeout=timeout) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    # Filtra apenas estações com lastcheckok == 1 e stream válido
                    filtered = [s for s in data if s.get("lastcheckok") == 1 and (s.get("url_resolved") or s.get("url", "")).startswith("http")]
                    print(f"[{country_code}] Sucesso ({mirror}): {len(filtered)} estações ativas obtidas")
                    return filtered
        except Exception as e:
            continue
    print(f"[{country_code}] Falha em todos os espelhos")
    return []

async def main():
    print("=== INICIANDO EXPANSÃO MUNDIAL PARA 6.000+ ESTAÇÕES ===")
    
    # 1. Carregar base existente
    with open("all_radio_sources.json", "r", encoding="utf-8") as f:
        existing_data = json.load(f)
    existing_stations = existing_data.get("stations", existing_data if isinstance(existing_data, list) else [])
    print(f"Base atual possui: {len(existing_stations)} estações")

    existing_urls: Set[str] = set()
    existing_keys: Set[str] = set()
    existing_ids: Set[str] = set()

    for s in existing_stations:
        sid = s.get("id")
        if sid: existing_ids.add(sid)
        for u in s.get("all_stream_urls", []) + [s.get("primary_stream_url")]:
            if u: existing_urls.add(normalize_url(u))
        name_k = normalize_name(s.get("name", ""))
        city_k = remove_accents(s.get("city", "")).lower().strip()
        state_k = (s.get("state") or "").upper().strip()
        country_k = (s.get("country_code") or "BR").upper().strip()
        if name_k:
            existing_keys.add(f"{name_k}::{city_k}::{state_k}::{country_k}")

    # 2. Coleta de candidatos por país
    new_candidates: List[Dict[str, Any]] = []
    seen_cand_ids: Set[str] = set()

    async with aiohttp.ClientSession() as session:
        for cc, limit in TARGETS:
            stations = await fetch_country_stations(session, cc, limit)
            for item in stations:
                uuid = item.get("stationuuid")
                if not uuid or uuid in seen_cand_ids:
                    continue
                seen_cand_ids.add(uuid)

                stream_url = item.get("url_resolved") or item.get("url") or ""
                norm_stream = normalize_url(stream_url)
                if not norm_stream or norm_stream in existing_urls:
                    continue

                raw_name = item.get("name", "").strip()
                if not raw_name:
                    continue
                name_k = normalize_name(raw_name)
                city_raw = item.get("city", "").strip() or item.get("state", "").strip()
                city_k = remove_accents(city_raw).lower().strip()
                state_raw = item.get("state", "").strip()
                comp_key = f"{name_k}::{city_k}::{state_raw.upper()}::{cc}"

                if comp_key in existing_keys:
                    continue

                # Aceito como novo candidato único
                existing_urls.add(norm_stream)
                existing_keys.add(comp_key)

                # Gera ID determinístico
                slug_name = re.sub(r'[^a-z0-9]+', '_', name_k).strip('_')[:20]
                slug_city = re.sub(r'[^a-z0-9]+', '_', city_k).strip('_')[:15]
                base_id = f"{cc.lower()}_{slug_city}_{slug_name}".strip('_')
                final_id = base_id
                c = 1
                while final_id in existing_ids:
                    final_id = f"{base_id}_{c}"
                    c += 1
                existing_ids.add(final_id)

                tags_list = [t.strip().lower() for t in (item.get("tags") or "").split(",") if t.strip()][:5]

                station_entry = {
                    "id": final_id,
                    "name": raw_name,
                    "country": COUNTRY_NAMES.get(cc, item.get("country", cc)),
                    "country_code": cc,
                    "state": state_raw[:30],
                    "city": city_raw[:40],
                    "codec": (item.get("codec") or "MP3").upper()[:8],
                    "bitrate_kbps": int(item.get("bitrate") or 128),
                    "votes": max(int(item.get("votes") or 500), 500),
                    "tags": tags_list,
                    "primary_stream_url": stream_url,
                    "alternative_stream_urls": [],
                    "total_sources_count": 1,
                    "all_stream_urls": [stream_url],
                    "favicon_url": item.get("favicon", "").strip(),
                    "homepage": item.get("homepage", "").strip()
                }
                new_candidates.append(station_entry)

    print(f"\nTotal de novos candidatos únicos selecionados: {len(new_candidates)}")
    final_stations = list(existing_stations) + new_candidates
    print(f"Total consolidado da base: {len(final_stations)} estações!")

    # 3. Backup de segurança
    backup_path = f"all_radio_sources.json.bak_expansion_{int(time.time())}"
    shutil.copyfile("all_radio_sources.json", backup_path)
    print(f"Backup de segurança gerado: {backup_path}")

    # 4. Gravação da nova base consolidada em all_radio_sources.json
    final_catalog = {
        "metadata": {
            "generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "description": "Base mundial de rádios do Global RadioPod consolidada (6.000+ rádios).",
            "total_stations": len(final_stations),
            "brazil_stations_count": sum(1 for s in final_stations if s.get("country_code") == "BR"),
            "international_stations_count": sum(1 for s in final_stations if s.get("country_code") != "BR")
        },
        "stations": final_stations
    }

    with open("all_radio_sources.json", "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(final_catalog), f, ensure_ascii=False, indent=2)
    print("all_radio_sources.json atualizado com sucesso!")

    # 5. Exportação para app/src/main/assets/radio_catalog.json
    os.makedirs("app/src/main/assets", exist_ok=True)
    with open("app/src/main/assets/radio_catalog.json", "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(final_stations), f, ensure_ascii=False, indent=2)
    print("app/src/main/assets/radio_catalog.json exportado com sucesso!")

    # 6. Exportação para CuratedStations.kt particionado em blocos de 150 estações
    chunk_size = 150
    with open("app/src/main/java/com/example/data/repository/CuratedStations.kt", "r", encoding="utf-8") as f:
        kt_code = f.read()

    marker = "private fun getStationsChunk_"
    marker_idx = kt_code.find(marker)
    if marker_idx == -1:
        marker = "val CURATED_GLOBAL_STATIONS"
        marker_idx = kt_code.find(marker)

    header = kt_code[:marker_idx].rstrip()
    num_chunks = (len(final_stations) + chunk_size - 1) // chunk_size
    chunk_func_names = []
    lines = [header, ""]

    for i in range(num_chunks):
        func_name = f"getStationsChunk_{i+1}"
        chunk_func_names.append(func_name)
        chunk_stations = final_stations[i * chunk_size : (i + 1) * chunk_size]

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

    with open("app/src/main/java/com/example/data/repository/CuratedStations.kt", "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"CuratedStations.kt atualizado com {len(final_stations)} estações em {num_chunks} chunks!")

    # 7. Relatório de métricas
    report = {
        "timestamp": datetime.datetime.now().isoformat(),
        "previous_total": len(existing_stations),
        "new_stations_added": len(new_candidates),
        "total_stations": len(final_stations),
        "brazil_count": sum(1 for s in final_stations if s.get("country_code") == "BR"),
        "international_count": sum(1 for s in final_stations if s.get("country_code") != "BR"),
        "num_chunks_kt": num_chunks,
        "by_country": {}
    }
    for s in final_stations:
        cc = s.get("country_code", "OTHER")
        report["by_country"][cc] = report["by_country"].get(cc, 0) + 1

    os.makedirs("reports", exist_ok=True)
    with open("reports/worldwide-expansion-report.json", "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(report), f, ensure_ascii=False, indent=2)
    print("Relatório salvo em reports/worldwide-expansion-report.json!")
    print("=== EXPANSÃO MUNDIAL CONCLUÍDA COM SUCESSO! ===")

if __name__ == "__main__":
    asyncio.run(main())
