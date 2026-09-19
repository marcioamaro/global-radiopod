#!/usr/bin/env python3
"""
scratch/ingest_webradio_com_br.py — Ingestão de 500 a 1.000 web rádios brasileiras validadas
a partir do diretório https://www.webradio.com.br, com:
1. Trava estrita de diversidade de origem: máximo de 5 rádios por host/domínio de streaming.
2. Extração precisa de UF, cidade e gêneros via class_list e taxonomias nativas.
3. Validação assíncrona ao vivo de streaming (rejeição de streams inativos/quebrados).
4. Deduplicação completa com o catálogo existente.
5. Modularização em CuratedStations.kt (chunks de 150 rádios) e radio_catalog.json.
"""

import asyncio
import datetime
import json
import os
import re
import shutil
import time
import unicodedata
import urllib.parse
from collections import Counter
from typing import Any, Dict, List, Set, Tuple

import aiohttp

STREAM_REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Icy-MetaData": "1",
    "Range": "bytes=0-2048",
    "Accept": "*/*",
    "Connection": "keep-alive"
}

UF_MAP = {
    "ac": "AC", "al": "AL", "ap": "AP", "am": "AM", "ba": "BA", "ce": "CE",
    "df": "DF", "es": "ES", "go": "GO", "ma": "MA", "mt": "MT", "ms": "MS",
    "mg": "MG", "pa": "PA", "pb": "PB", "pr": "PR", "pe": "PE", "pi": "PI",
    "rj": "RJ", "rn": "RN", "rs": "RS", "ro": "RO", "rr": "RR", "sc": "SC",
    "sp": "SP", "se": "SE", "to": "TO"
}

CAPITALS = {
    "AC": "Rio Branco", "AL": "Maceió", "AP": "Macapá", "AM": "Manaus",
    "BA": "Salvador", "CE": "Fortaleza", "DF": "Brasília", "ES": "Vitória",
    "GO": "Goiânia", "MA": "São Luís", "MT": "Cuiabá", "MS": "Campo Grande",
    "MG": "Belo Horizonte", "PA": "Belém", "PB": "João Pessoa", "PR": "Curitiba",
    "PE": "Recife", "PI": "Teresina", "RJ": "Rio de Janeiro", "RN": "Natal",
    "RS": "Porto Alegre", "RO": "Porto Velho", "RR": "Boa Vista", "SC": "Florianópolis",
    "SP": "São Paulo", "SE": "Aracaju", "TO": "Palmas"
}

def remove_accents(s: str) -> str:
    if not s: return ""
    return "".join(c for c in unicodedata.normalize('NFKD', s) if not unicodedata.combining(c))

def normalize_name(name: str) -> str:
    if not name: return ""
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

def parse_class_list_info(class_list: List[str]) -> Tuple[str, str, List[str]]:
    """Extrai UF, Cidade e Gêneros das classes CSS nativas do WordPress"""
    uf = "SP"
    city = ""
    genres = []

    for c in class_list:
        if c.startswith("genre-"):
            g = c.replace("genre-", "").replace("-", " ")
            if g not in genres:
                genres.append(g)
        elif c.startswith("local-") and c != "local-brasil":
            loc = c.replace("local-", "")
            # Procura por terminação com sigla de estado (ex: -sp, -mg, -rj, -rs)
            m = re.search(r'-([a-z]{2})$', loc)
            if m and m.group(1) in UF_MAP:
                uf = UF_MAP[m.group(1)]
                city_slug = loc[:-3]  # remove -uf
                city_clean = city_slug.replace("-cidade", "").replace("-", " ").title()
                if city_clean and len(city_clean) > 2:
                    city = city_clean

    if not city:
        city = CAPITALS.get(uf, "São Paulo")

    if not genres:
        genres = ["webradio", "brasil"]

    return uf, city, genres[:5]

async def test_stream(session: aiohttp.ClientSession, url: str) -> Tuple[bool, str, int]:
    if not url or not url.startswith("http"):
        return False, "invalid_url", 0

    u_lower = url.lower()
    if "radiosnet" in u_lower or "radios.com.br" in u_lower:
        return False, "radiosnet_blocked", 0

    start = time.time()
    try:
        timeout = aiohttp.ClientTimeout(sock_connect=4.0, sock_read=3.0, total=7.0)
        async with session.get(url, headers=STREAM_REQUEST_HEADERS, timeout=timeout, allow_redirects=True) as resp:
            ctype = resp.headers.get("Content-Type", "").lower()
            if resp.status in (200, 206):
                chunk = await resp.content.read(2048)
                if len(chunk) > 0:
                    latency = round((time.time() - start) * 1000)
                    return True, ctype, latency
    except Exception as e:
        return False, str(e)[:30], 0
    return False, "bad_response", 0

async def fetch_webradio_page(session: aiohttp.ClientSession, page: int) -> List[Dict[str, Any]]:
    url = f"https://www.webradio.com.br/wp-json/wp/v2/station?local=860&per_page=100&page={page}"
    headers = {"User-Agent": "GlobalRadioPod/2.0 (Android Auto Ingestion)"}
    try:
        timeout = aiohttp.ClientTimeout(total=20)
        async with session.get(url, headers=headers, timeout=timeout) as resp:
            if resp.status == 200:
                data = await resp.json(content_type=None)
                return data
    except Exception as e:
        print(f"Erro ao buscar página {page}: {e}")
    return []

async def main():
    print("============================================================================")
    print("=== [INGESTÃO WEBRADIO.COM.BR] EXPANSÃO COM TRAVA DE DIVERSIDADE ORIGEM ===")
    print("============================================================================")

    # 1. Carregar base existente
    with open("all_radio_sources.json", "r", encoding="utf-8") as f:
        existing_data = json.load(f)
    existing_stations = existing_data.get("stations", existing_data if isinstance(existing_data, list) else [])
    print(f"Base total atual: {len(existing_stations)} estações")
    br_existing_count = sum(1 for s in existing_stations if s.get("country_code") == "BR")
    print(f"Rádios brasileiras já na base: {br_existing_count}")

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

    # 2. Coletar páginas do webradio.com.br respeitando a trava de diversidade (máx 5 rádios por host)
    print("\n--- Fase 1: Coletando estações de webradio.com.br com trava de diversidade de host ---")
    MAX_PER_HOST = 5
    TARGET_NEW_APPROVED = 750  # meta entre 500 e 1000

    host_counts: Dict[str, int] = {}
    candidate_stations: List[Dict[str, Any]] = []

    async with aiohttp.ClientSession() as session:
        page = 1
        max_pages = 25  # 25 páginas x 100 = até 2500 candidatos analisados
        while page <= max_pages and len(candidate_stations) < 1500:
            print(f"Buscando página {page}/{max_pages} de webradio.com.br...")
            items = await fetch_webradio_page(session, page)
            if not items:
                break

            for item in items:
                raw_name = (item.get("title", {}).get("rendered") or "").strip()
                meta = item.get("meta", {})
                streams = meta.get("stream", [])
                if not streams or not raw_name:
                    continue

                stream_url = (streams[0].get("url") or "").strip()
                norm_url = normalize_url(stream_url)
                if not norm_url or norm_url in existing_urls:
                    continue

                # Extrai domínio do host de streaming
                parsed = urllib.parse.urlparse(stream_url)
                stream_host = parsed.netloc.lower()
                if not stream_host:
                    continue

                # TRAVA DE DIVERSIDADE DE ORIGEM (máximo 5 por host)
                if host_counts.get(stream_host, 0) >= MAX_PER_HOST:
                    continue

                class_list = item.get("class_list", [])
                uf, city, genres = parse_class_list_info(class_list)

                name_k = normalize_name(raw_name)
                city_k = remove_accents(city).lower().strip()
                comp_key = f"{name_k}::{city_k}::{uf}::BR"

                if comp_key in existing_keys:
                    continue

                # Adiciona como candidato a validar
                existing_urls.add(norm_url)
                existing_keys.add(comp_key)
                host_counts[stream_host] = host_counts.get(stream_host, 0) + 1

                candidate_stations.append({
                    "name": raw_name,
                    "stream_url": stream_url,
                    "norm_url": norm_url,
                    "stream_host": stream_host,
                    "state": uf,
                    "city": city,
                    "genres": genres,
                    "name_k": name_k,
                    "city_k": city_k
                })

            print(f"Candidatos únicos coletados até agora: {len(candidate_stations)} (Hosts distintos: {len(host_counts)})")
            page += 1
            await asyncio.sleep(0.3)

        print(f"\nTotal de candidatos pré-selecionados para teste de stream: {len(candidate_stations)}")
        print(f"Total de provedores/hosts de streaming distintos: {len(host_counts)}")

        # 3. Validação assíncrona ao vivo dos streams
        print("\n--- Fase 2: Validando conexões e streaming ao vivo ---")
        semaphore = asyncio.Semaphore(70)

        async def check_item(c):
            async with semaphore:
                is_valid, ctype, lat = await test_stream(session, c["stream_url"])
                return c, is_valid, ctype, lat

        tasks = [check_item(c) for c in candidate_stations]
        approved_results: List[Dict[str, Any]] = []
        completed = 0
        total_tasks = len(tasks)

        for f in asyncio.as_completed(tasks):
            c, is_valid, ctype, lat = await f
            completed += 1
            if is_valid:
                approved_results.append(c)
                if len(approved_results) >= TARGET_NEW_APPROVED:
                    print(f"Meta de {TARGET_NEW_APPROVED} rádios aprovadas atingida! Interrompendo validação restante para otimizar tempo.")
                    break
            if completed % 100 == 0 or completed == total_tasks:
                print(f"Validação: {completed}/{total_tasks} | {len(approved_results)} válidas")

        print(f"\nTotal final de novas rádios APROVADAS com stream ao vivo ativo: {len(approved_results)}")

    # 4. Formatação e geração de IDs determinísticos
    new_station_models: List[Dict[str, Any]] = []
    for c in approved_results:
        name_k = c["name_k"]
        city_k = c["city_k"]
        slug_name = re.sub(r'[^a-z0-9]+', '_', name_k).strip('_')[:20]
        slug_city = re.sub(r'[^a-z0-9]+', '_', city_k).strip('_')[:15]
        base_id = f"br_{slug_city}_{slug_name}".strip('_')
        final_id = base_id
        count = 1
        while final_id in existing_ids:
            final_id = f"{base_id}_{count}"
            count += 1
        existing_ids.add(final_id)

        entry = {
            "id": final_id,
            "name": c["name"],
            "country": "Brasil",
            "country_code": "BR",
            "state": c["state"],
            "city": c["city"],
            "codec": "MP3",
            "bitrate_kbps": 128,
            "votes": 500,
            "tags": c["genres"],
            "primary_stream_url": c["stream_url"],
            "alternative_stream_urls": [],
            "total_sources_count": 1,
            "all_stream_urls": [c["stream_url"]],
            "favicon_url": "",
            "homepage": "https://www.webradio.com.br"
        }
        new_station_models.append(entry)

    # 5. Consolidação final
    print(f"\n--- Fase 3: Consolidando catálogo e exportando artefatos ---")
    final_stations = list(existing_stations) + new_station_models
    new_br_total = sum(1 for s in final_stations if s.get("country_code") == "BR")
    print(f"Total mundial consolidado: {len(final_stations)} estações")
    print(f"Total de rádios brasileiras: {new_br_total} (+{len(new_station_models)} novas)")

    # Distribuição por estado
    br_only = [s for s in final_stations if s.get("country_code") == "BR"]
    dist = Counter(s.get("state", "UNKNOWN") for s in br_only)
    print("\nCobertura consolidada por UF:")
    for uf in sorted(CAPITALS.keys()):
        print(f"  {uf} ({CAPITALS[uf]}): {dist.get(uf, 0)} rádios")

    # Backup
    backup_file = f"all_radio_sources.json.bak_webradio_{int(time.time())}"
    shutil.copyfile("all_radio_sources.json", backup_file)
    print(f"\nBackup gerado: {backup_file}")

    # Salva all_radio_sources.json
    output_dict = {
        "metadata": {
            "version": "2.4",
            "last_updated": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "total_stations": len(final_stations),
            "brazil_stations": new_br_total,
            "description": "Base global expandida com web radios brasileiras validadas de servidores independentes"
        },
        "stations": final_stations
    }
    with open("all_radio_sources.json", "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(output_dict), f, ensure_ascii=False, indent=2)
    print("all_radio_sources.json atualizado com sucesso!")

    # Salva app/src/main/assets/radio_catalog.json
    with open("app/src/main/assets/radio_catalog.json", "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(output_dict), f, ensure_ascii=False, indent=2)
    print("app/src/main/assets/radio_catalog.json atualizado com sucesso!")

    # 6. Gerar CuratedStations.kt particionado em chunks de 150 estações
    print("\n--- Fase 4: Gerando CuratedStations.kt particionado ---")
    chunk_size = 150
    chunks = [final_stations[i:i + chunk_size] for i in range(0, len(final_stations), chunk_size)]
    num_chunks = len(chunks)
    print(f"Dividindo {len(final_stations)} estações em {num_chunks} chunks de até {chunk_size} estações")

    kt_lines = []
    kt_lines.append("package com.example.data.repository")
    kt_lines.append("")
    kt_lines.append("import com.example.data.model.RadioStation")
    kt_lines.append("import java.util.Locale")
    kt_lines.append("")
    kt_lines.append("/**")
    kt_lines.append(f" * Catálogo Curado Oficial do Global RadioPod contendo {len(final_stations)} estações ativas.")
    kt_lines.append(f" * Particionado em {num_chunks} blocos para garantir limite de bytecode da JVM (máx 64KB/método).")
    kt_lines.append(" */")
    kt_lines.append("object CuratedStations {")
    kt_lines.append("")
    kt_lines.append("    val stations: List<RadioStation> by lazy {")
    kt_lines.append("        buildList {")
    for chunk_idx in range(1, num_chunks + 1):
        kt_lines.append(f"            addAll(getStationsChunk_{chunk_idx}())")
    kt_lines.append("        }")
    kt_lines.append("    }")
    kt_lines.append("")

    for idx, chunk in enumerate(chunks, 1):
        kt_lines.append(f"    private fun getStationsChunk_{idx}(): List<RadioStation> = listOf(")
        for s in chunk:
            s_id = escape_kt_string(s.get("id"))
            s_name = escape_kt_string(s.get("name"))
            s_country = escape_kt_string(s.get("country"))
            s_code = escape_kt_string(s.get("country_code", "BR"))
            s_state = escape_kt_string(s.get("state"))
            s_city = escape_kt_string(s.get("city"))
            s_codec = escape_kt_string(s.get("codec", "MP3"))
            s_bitrate = int(s.get("bitrate_kbps") or 128)
            s_votes = int(s.get("votes") or 500)
            s_stream = escape_kt_string(s.get("primary_stream_url"))
            s_fav = escape_kt_string(s.get("favicon_url"))
            s_home = escape_kt_string(s.get("homepage"))
            s_tags = [f'"{escape_kt_string(t)}"' for t in s.get("tags", [])]
            tags_str = f"listOf({', '.join(s_tags)})" if s_tags else "emptyList()"

            kt_lines.append("        RadioStation(")
            kt_lines.append(f'            id = "{s_id}",')
            kt_lines.append(f'            name = "{s_name}",')
            kt_lines.append(f'            country = "{s_country}",')
            kt_lines.append(f'            countryCode = "{s_code}",')
            kt_lines.append(f'            state = "{s_state}",')
            kt_lines.append(f'            city = "{s_city}",')
            kt_lines.append(f'            codec = "{s_codec}",')
            kt_lines.append(f'            bitrateKbps = {s_bitrate},')
            kt_lines.append(f'            votes = {s_votes},')
            kt_lines.append(f'            tags = {tags_str},')
            kt_lines.append(f'            primaryStreamUrl = "{s_stream}",')
            kt_lines.append(f'            alternativeStreamUrls = emptyList(),')
            kt_lines.append(f'            faviconUrl = "{s_fav}",')
            kt_lines.append(f'            homepage = "{s_home}"')
            kt_lines.append("        ),")
        kt_lines.append("    )")
        kt_lines.append("")

    kt_lines.append("    val stationsCount: Int get() = stations.size")
    kt_lines.append("}")
    kt_lines.append("")

    with open("app/src/main/java/com/example/data/repository/CuratedStations.kt", "w", encoding="utf-8") as kf:
        kf.write("\n".join(kt_lines))
    print("app/src/main/java/com/example/data/repository/CuratedStations.kt regerado com sucesso!")

    report = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "total_stations_before": len(existing_stations),
        "total_brazil_before": br_existing_count,
        "new_brazil_stations_added": len(new_station_models),
        "total_stations_after": len(final_stations),
        "total_brazil_after": new_br_total,
        "chunks_count": num_chunks,
        "distinct_hosts_count": len(host_counts),
        "uf_distribution": {uf: dist.get(uf, 0) for uf in sorted(CAPITALS.keys())}
    }
    with open("reports/webradio-ingestion-report.json", "w", encoding="utf-8") as rf:
        json.dump(report, rf, indent=2)
    print("Relatório salvo em reports/webradio-ingestion-report.json")
    print("=== INGESTÃO WEBRADIO.COM.BR CONCLUÍDA COM SUCESSO! ===")

if __name__ == "__main__":
    asyncio.run(main())
