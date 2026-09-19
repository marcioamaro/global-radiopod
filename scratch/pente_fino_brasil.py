#!/usr/bin/env python3
"""
scratch/pente_fino_brasil.py — Pente fino completo em todas as cidades e estados do Brasil
para localizar e integrar o máximo possível de rádios e web rádios com streaming estritamente validado.
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
from typing import Any, Dict, List, Set, Tuple

import aiohttp

RADIO_BROWSER_MIRRORS = [
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json",
    "https://at1.api.radio-browser.info/json"
]

BRAZIL_STATES = [
    ("AC", "Acre"), ("AL", "Alagoas"), ("AP", "Amapa"), ("AM", "Amazonas"),
    ("BA", "Bahia"), ("CE", "Ceara"), ("DF", "Distrito Federal"), ("ES", "Espirito Santo"),
    ("GO", "Goias"), ("MA", "Maranhao"), ("MT", "Mato Grosso"), ("MS", "Mato Grosso do Sul"),
    ("MG", "Minas Gerais"), ("PA", "Para"), ("PB", "Paraiba"), ("PR", "Parana"),
    ("PE", "Pernambuco"), ("PI", "Piaui"), ("RJ", "Rio de Janeiro"), ("RN", "Rio Grande do Norte"),
    ("RS", "Rio Grande do Sul"), ("RO", "Rondonia"), ("RR", "Roraima"), ("SC", "Santa Catarina"),
    ("SP", "Sao Paulo"), ("SE", "Sergipe"), ("TO", "Tocantins")
]

PROMINENT_CITIES = [
    "Araras", "Campinas", "Ribeirao Preto", "Santos", "Sorocaba", "Sao Jose dos Campos",
    "Sao Jose do Rio Preto", "Bauru", "Piracicaba", "Jundiai", "Franca", "Marilia",
    "Presidente Prudente", "Sao Carlos", "Limeira", "Rio Claro", "Americana", "Indaiatuba",
    "Taubate", "Barretos", "Catanduva", "Botucatu", "Araraquara", "Guarulhos", "Osasco",
    "Santo Andre", "Sao Bernardo do Campo",
    "Belo Horizonte", "Uberlandia", "Juiz de Fora", "Montes Claros", "Uberaba",
    "Governador Valadares", "Ipatinga", "Divinopolis", "Pocos de Caldas", "Patos de Minas", "Varginha",
    "Rio de Janeiro", "Niteroi", "Petropolis", "Volta Redonda", "Campos dos Goytacazes",
    "Cabo Frio", "Nova Friburgo", "Macae", "Angra dos Reis", "Teresopolis",
    "Salvador", "Feira de Santana", "Vitoria da Conquista", "Ilheus", "Itabuna", "Juazeiro",
    "Barreiras", "Porto Seguro", "Jequie", "Alagoinhas",
    "Curitiba", "Londrina", "Maringa", "Ponta Grossa", "Cascavel", "Foz do Iguacu",
    "Toledo", "Guarapuava", "Paranagua", "Pato Branco",
    "Porto Alegre", "Caxias do Sul", "Pelotas", "Canoas", "Santa Maria", "Gravatai",
    "Passo Fundo", "Novo Hamburgo", "Sao Leopoldo", "Rio Grande", "Bento Goncalves",
    "Florianopolis", "Joinville", "Blumenau", "Chapeco", "Criciuma", "Itajai",
    "Jaragua do Sul", "Lages", "Balneario Camboriu", "Brusque",
    "Fortaleza", "Caucaia", "Juazeiro do Norte", "Maracanau", "Sobral", "Crato", "Itapipoca", "Iguatu",
    "Recife", "Jaboatao", "Olinda", "Caruaru", "Petrolina", "Paulista", "Cabo de Santo Agostinho",
    "Garanhuns", "Vitoria de Santo Antao",
    "Goiania", "Aparecida de Goiania", "Anapolis", "Rio Verde", "Luziania", "Aguas Lindas", "Itumbiara", "Jatai",
    "Belem", "Ananindeua", "Santarem", "Maraba", "Parauapebas", "Castanhal",
    "Manaus", "Parintins", "Itacoatiara", "Manacapuru",
    "Vitoria", "Vila Velha", "Serra", "Cariacica", "Cachoeiro de Itapemirim", "Linhares", "Colatina",
    "Sao Luis", "Imperatriz", "Sao Jose de Ribamar", "Timon", "Caxias",
    "Natal", "Mossoro", "Parnamirim", "Caico",
    "Cuiaba", "Varzea Grande", "Rondonopolis", "Sinop", "Tangara da Serra", "Sorriso",
    "Campo Grande", "Dourados", "Tres Lagoas", "Corumba", "Ponta Pora",
    "Joao Pessoa", "Campina Grande", "Santa Rita", "Patos", "Sousa",
    "Teresina", "Parnaiba", "Picos", "Piripiri", "Floriano",
    "Maceio", "Arapiraca", "Palmeira dos Indios",
    "Aracaju", "Nossa Senhora do Socorro", "Lagarto", "Itabaiana",
    "Porto Velho", "Ji-Parana", "Ariquemes", "Vilhena", "Cacoal",
    "Macapa", "Santana", "Laranjal do Jari",
    "Boa Vista", "Rorainopolis",
    "Palmas", "Araguaina", "Gurupi", "Porto Nacional",
    "Rio Branco", "Cruzeiro do Sul", "Sena Madureira"
]

GENRE_TAGS = [
    "webradio", "web radio", "sertanejo", "gospel", "mpb", "forro", "pagode",
    "samba", "pop", "rock", "noticias", "comunitaria", "evangelica", "catolica",
    "brasil", "brazil", "axe", "jovem", "funk", "futebol", "esporte", "bossa nova"
]

STREAM_REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Icy-MetaData": "1",
    "Range": "bytes=0-2048",
    "Accept": "*/*",
    "Connection": "keep-alive"
}

def remove_accents(s: str) -> str:
    if not s:
        return ""
    return "".join(c for c in unicodedata.normalize('NFKD', s) if not unicodedata.combining(c))

def normalize_name(name: str) -> str:
    if not name:
        return ""
    cleaned = remove_accents(name).lower()
    cleaned = re.sub(r'[^a-z0-9\s]', ' ', cleaned)
    cleaned = re.sub(r'^(radio|fm|am|webradio|web radio)\s+', '', cleaned)
    cleaned = re.sub(r'\s+(fm|am)$', '', cleaned)
    return re.sub(r'\s+', ' ', cleaned).strip()

def normalize_url(url: str) -> str:
    if not url:
        return ""
    u = url.split('#')[0].strip()
    try:
        p = urllib.parse.urlparse(u)
        nl = p.netloc.lower()
        if nl.endswith(":80") and p.scheme == "http":
            nl = nl[:-3]
        elif nl.endswith(":443") and p.scheme == "https":
            nl = nl[:-4]
        return urllib.parse.urlunparse((p.scheme.lower(), nl, p.path, p.params, p.query, ''))
    except Exception:
        return u

def escape_kt_string(s: Any) -> str:
    if s is None:
        return ""
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

def infer_state_code(state_raw: str, city_raw: str) -> str:
    combined = f"{state_raw} {city_raw}".upper()
    for uf, name in BRAZIL_STATES:
        if re.search(r'\b' + uf + r'\b', combined):
            return uf
        if remove_accents(name).lower() in remove_accents(combined).lower():
            return uf
    return state_raw.strip()[:2].upper() if len(state_raw.strip()) == 2 else "BR"

async def fetch_radio_browser(session: aiohttp.ClientSession, endpoint: str) -> List[Dict[str, Any]]:
    headers = {"User-Agent": "GlobalRadioPod/2.0 (Android Auto)"}
    for mirror in RADIO_BROWSER_MIRRORS:
        url = f"{mirror}/{endpoint.lstrip('/')}"
        try:
            timeout = aiohttp.ClientTimeout(total=15)
            async with session.get(url, headers=headers, timeout=timeout) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    return data
        except Exception:
            continue
    return []

async def test_stream(session: aiohttp.ClientSession, url: str) -> Tuple[bool, str, int]:
    """Testa se a URL do stream realmente funciona com streaming ativo"""
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

async def main():
    print("===================================================================")
    print("=== [PENTE FINO BRASIL] EXPANSÃO COMPLETA DE RÁDIOS BRASILEIRAS ===")
    print("===================================================================")

    # 1. Carregar base existente
    with open("all_radio_sources.json", "r", encoding="utf-8") as f:
        existing_data = json.load(f)
    existing_stations = existing_data.get("stations", existing_data if isinstance(existing_data, list) else [])
    print(f"Base total existente: {len(existing_stations)} estações")
    br_existing = [s for s in existing_stations if s.get("country") in ("Brasil", "Brazil") or s.get("country_code") == "BR"]
    print(f"Rádios brasileiras já na base: {len(br_existing)}")

    existing_urls: Set[str] = set()
    existing_keys: Set[str] = set()
    existing_ids: Set[str] = set()

    for s in existing_stations:
        sid = s.get("id")
        if sid:
            existing_ids.add(sid)
        for u in s.get("all_stream_urls", []) + [s.get("primary_stream_url")]:
            if u:
                existing_urls.add(normalize_url(u))
        name_k = normalize_name(s.get("name", ""))
        city_k = remove_accents(s.get("city", "")).lower().strip()
        state_k = (s.get("state") or "").upper().strip()
        country_k = (s.get("country_code") or "BR").upper().strip()
        if name_k:
            existing_keys.add(f"{name_k}::{city_k}::{state_k}::{country_k}")

    # 2. Coletar candidatos da API do Radio Browser com Pente Fino
    candidate_items: List[Dict[str, Any]] = []
    seen_cand_uuids: Set[str] = set()

    async with aiohttp.ClientSession() as session:
        print("\n--- Fase 1: Coletando catálogo de estações por país e estados ---")
        endpoints = [
            "stations/search?countrycode=BR&limit=10000",
            "stations/search?country=Brazil&limit=10000"
        ]

        # Pente fino por cada um dos 27 estados
        for uf, name in BRAZIL_STATES:
            endpoints.append(f"stations/search?countrycode=BR&state={uf}&limit=1000")
            endpoints.append(f"stations/search?countrycode=BR&state={urllib.parse.quote(name)}&limit=1000")

        # Pente fino por cidades
        for city in PROMINENT_CITIES:
            q_city = urllib.parse.quote(city)
            endpoints.append(f"stations/search?countrycode=BR&name={q_city}&limit=200")
            endpoints.append(f"stations/search?countrycode=BR&state={q_city}&limit=200")

        # Pente fino por gêneros e web rádios
        for tag in GENRE_TAGS:
            q_tag = urllib.parse.quote(tag)
            endpoints.append(f"stations/search?countrycode=BR&tag={q_tag}&limit=500")

        print(f"Total de consultas estruturadas a executar: {len(endpoints)}")
        query_count = 0
        for ep in endpoints:
            query_count += 1
            if query_count % 25 == 0 or query_count == len(endpoints):
                print(f"Progresso de consultas: {query_count}/{len(endpoints)} ({len(candidate_items)} candidatos acumulados)")
            items = await fetch_radio_browser(session, ep)
            for it in items:
                uuid = it.get("stationuuid")
                if not uuid or uuid in seen_cand_uuids:
                    continue
                seen_cand_uuids.add(uuid)
                candidate_items.append(it)

        # Também incorporar extracted_sitemap1_radios.json se existir
        if os.path.exists("extracted_sitemap1_radios.json"):
            try:
                with open("extracted_sitemap1_radios.json", "r", encoding="utf-8") as sf:
                    s_data = json.load(sf)
                    st_list = s_data.get("stations", [])
                    print(f"\nIncorporando candidatos do sitemap local: {len(st_list)} estações")
                    for s in st_list:
                        u = s.get("primary_stream_url")
                        if u and u.startswith("http"):
                            candidate_items.append({
                                "name": s.get("name"),
                                "url_resolved": u,
                                "city": s.get("city"),
                                "state": s.get("state"),
                                "country": "Brasil",
                                "countrycode": "BR",
                                "codec": s.get("codec", "MP3"),
                                "bitrate": s.get("bitrate_kbps", 128),
                                "tags": ",".join(s.get("tags", ["webradio", "brasil"])),
                                "votes": 500
                            })
            except Exception as e:
                print(f"Aviso ao ler extracted_sitemap1_radios.json: {e}")

        print(f"\nTotal bruto de candidatos coletados: {len(candidate_items)}")

        # 3. Pré-filtragem de deduplicação antes de gastar rede validando
        unvalidated_candidates: List[Dict[str, Any]] = []
        for it in candidate_items:
            stream_url = (it.get("url_resolved") or it.get("url") or "").strip()
            norm_url = normalize_url(stream_url)
            if not norm_url or norm_url in existing_urls:
                continue

            raw_name = (it.get("name") or "").strip()
            if not raw_name or len(raw_name) < 2:
                continue

            name_k = normalize_name(raw_name)
            city_raw = (it.get("city") or "").strip()
            state_raw = (it.get("state") or "").strip()
            uf = infer_state_code(state_raw, city_raw)
            city_k = remove_accents(city_raw).lower().strip()
            comp_key = f"{name_k}::{city_k}::{uf}::BR"

            if comp_key in existing_keys:
                continue

            existing_urls.add(norm_url)
            existing_keys.add(comp_key)

            unvalidated_candidates.append({
                "raw_item": it,
                "name": raw_name,
                "stream_url": stream_url,
                "norm_url": norm_url,
                "city": city_raw or name_k.title(),
                "state": uf,
                "name_k": name_k
            })

        print(f"Total de candidatos novos e únicos a validar: {len(unvalidated_candidates)}")

        # 4. Validação concorrente dos streams (estritamente streams funcionais)
        print("\n--- Fase 2: Validando streams com testes de conexão ao vivo ---")
        semaphore = asyncio.Semaphore(60)

        async def validate_candidate(cand):
            async with semaphore:
                is_valid, ctype, lat = await test_stream(session, cand["stream_url"])
                return cand, is_valid, ctype, lat

        tasks = [validate_candidate(c) for c in unvalidated_candidates]
        validated_results = []
        completed = 0
        total_tasks = len(tasks)

        for f in asyncio.as_completed(tasks):
            cand, is_valid, ctype, lat = await f
            completed += 1
            if completed % 100 == 0 or completed == total_tasks:
                valid_so_far = sum(1 for _, v, _, _ in validated_results) + (1 if is_valid else 0)
                print(f"Validação: {completed}/{total_tasks} concluídos | {valid_so_far} válidos")
            if is_valid:
                validated_results.append((cand, is_valid, ctype, lat))

        print(f"\nTotal de candidatos APROVADOS na validação técnica de streaming: {len(validated_results)}")

        # 5. Formatação das novas estações aprovadas
        new_approved_stations: List[Dict[str, Any]] = []
        for cand, _, ctype, lat in validated_results:
            it = cand["raw_item"]
            name_k = cand["name_k"]
            city_k = remove_accents(cand["city"]).lower().strip()
            slug_name = re.sub(r'[^a-z0-9]+', '_', name_k).strip('_')[:20]
            slug_city = re.sub(r'[^a-z0-9]+', '_', city_k).strip('_')[:15]
            base_id = f"br_{slug_city}_{slug_name}".strip('_')
            final_id = base_id
            c = 1
            while final_id in existing_ids:
                final_id = f"{base_id}_{c}"
                c += 1
            existing_ids.add(final_id)

            tags_raw = it.get("tags")
            if isinstance(tags_raw, str):
                tags_list = [t.strip().lower() for t in tags_raw.split(",") if t.strip()][:5]
            elif isinstance(tags_raw, list):
                tags_list = [str(t).strip().lower() for t in tags_raw if str(t).strip()][:5]
            else:
                tags_list = ["webradio", "brasil"]

            entry = {
                "id": final_id,
                "name": cand["name"],
                "country": "Brasil",
                "country_code": "BR",
                "state": cand["state"][:30],
                "city": cand["city"][:40],
                "codec": (it.get("codec") or "MP3").upper()[:8],
                "bitrate_kbps": int(it.get("bitrate") or 128),
                "votes": max(int(it.get("votes") or 500), 500),
                "tags": tags_list if tags_list else ["webradio", "brasil"],
                "primary_stream_url": cand["stream_url"],
                "alternative_stream_urls": [],
                "total_sources_count": 1,
                "all_stream_urls": [cand["stream_url"]],
                "favicon_url": (it.get("favicon") or "").strip(),
                "homepage": (it.get("homepage") or "").strip()
            }
            new_approved_stations.append(entry)

    # 6. Consolidação e salvamento
    print(f"\n--- Fase 3: Integrando {len(new_approved_stations)} novas rádios validadas na base ---")
    final_stations = list(existing_stations) + new_approved_stations
    print(f"Novo total consolidado: {len(final_stations)} estações no Global RadioPod!")
    new_br_total = sum(1 for s in final_stations if s.get("country") in ("Brasil", "Brazil") or s.get("country_code") == "BR")
    print(f"Novo total de rádios brasileiras: {new_br_total} (Adicionadas: +{len(new_approved_stations)})")

    # Backup
    backup_file = f"all_radio_sources.json.bak_pente_fino_{int(time.time())}"
    shutil.copyfile("all_radio_sources.json", backup_file)
    print(f"Backup gerado: {backup_file}")

    # Salvar all_radio_sources.json
    output_dict = {
        "metadata": {
            "version": "2.2",
            "last_updated": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "total_stations": len(final_stations),
            "brazil_stations": new_br_total,
            "description": "Base global expandida com pente fino de rádios brasileiras validadas"
        },
        "stations": final_stations
    }
    with open("all_radio_sources.json", "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(output_dict), f, ensure_ascii=False, indent=2)
    print("all_radio_sources.json atualizado com sucesso!")

    # Salvar assets/radio_catalog.json
    with open("app/src/main/assets/radio_catalog.json", "w", encoding="utf-8") as f:
        json.dump(clean_surrogates(output_dict), f, ensure_ascii=False, indent=2)
    print("app/src/main/assets/radio_catalog.json atualizado com sucesso!")

    # 7. Gerar CuratedStations.kt particionado em chunks de 150 estações
    print("\n--- Fase 4: Gerando CuratedStations.kt modularizado ---")
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

    # Relatório final
    report = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "total_stations_before": len(existing_stations),
        "total_brazil_before": len(br_existing),
        "new_brazil_stations_added": len(new_approved_stations),
        "total_stations_after": len(final_stations),
        "total_brazil_after": new_br_total,
        "chunks_count": num_chunks
    }
    with open("reports/pente-fino-brasil-report.json", "w", encoding="utf-8") as rf:
        json.dump(report, rf, indent=2)
    print("Relatório salvo em reports/pente-fino-brasil-report.json")
    print("=== PENTE FINO CONCLUÍDO COM SUCESSO! ===")

if __name__ == "__main__":
    asyncio.run(main())
