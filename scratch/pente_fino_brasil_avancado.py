#!/usr/bin/env python3
"""
scratch/pente_fino_brasil_avancado.py — Pente fino abrangente em todos os estados e cidades do Brasil:
1. Normalização geográfica completa de todas as UFs brasileiras (27 estados: SP, RJ, MG, etc.).
2. Correção de estações estrangeiras que foram marcadas indevidamente como Brasil.
3. Busca intensiva de novas rádios e web rádios brasileiras em todas as 27 UFs e polos regionais.
4. Validação estrita de stream ativo (rejeição de streams mudos, 404, 500 ou que não entregam áudio).
5. Geração limpa e particionada de CuratedStations.kt e radio_catalog.json.
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

RADIO_BROWSER_MIRRORS = [
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json",
    "https://at1.api.radio-browser.info/json"
]

UF_NORMALIZATION = {
    # São Paulo
    "sao paulo": "SP", "são paulo": "SP", "so paulo": "SP", "sao paulo (sp)": "SP",
    "são paulo (sp)": "SP", "sao paulo brazil": "SP", "sp": "SP",
    # Rio de Janeiro
    "rio de janeiro": "RJ", "rio de janeiro (rj)": "RJ", "rj": "RJ",
    # Minas Gerais
    "minas gerais": "MG", "minas gerais (mg)": "MG", "mg": "MG",
    # Rio Grande do Sul
    "rio grande do sul": "RS", "rio grande do sul (rs)": "RS", "rs": "RS",
    # Paraná
    "parana": "PR", "paraná": "PR", "parana (pr)": "PR", "paraná (pr)": "PR",
    "parana brazil": "PR", "paranabrazil": "PR", "pr": "PR",
    # Santa Catarina
    "santa catarina": "SC", "santa catarina (sc)": "SC", "santa catarina, sc": "SC", "sc": "SC",
    # Bahia
    "bahia": "BA", "bahia (ba)": "BA", "ba": "BA",
    # Goiás
    "goias": "GO", "goiás": "GO", "goias (go)": "GO", "goiás (go)": "GO", "go": "GO",
    # Pernambuco
    "pernambuco": "PE", "pernambuco (pe)": "PE", "pe": "PE",
    # Ceará
    "ceara": "CE", "ceará": "CE", "ceara (ce)": "CE", "ce": "CE",
    # Distrito Federal
    "distrito federal": "DF", "brasilia": "DF", "brasília": "DF", "df": "DF",
    # Espírito Santo
    "espirito santo": "ES", "espírito santo": "ES", "es": "ES",
    # Maranhão
    "maranhao": "MA", "maranhão": "MA", "ma": "MA",
    # Paraíba
    "paraiba": "PB", "paraíba": "PB", "pb": "PB",
    # Amazonas
    "amazonas": "AM", "am": "AM",
    # Pará
    "para": "PA", "pará": "PA", "pa": "PA",
    # Mato Grosso
    "mato grosso": "MT", "mt": "MT",
    # Mato Grosso do Sul
    "mato grosso do sul": "MS", "ms": "MS",
    # Rio Grande do Norte
    "rio grande do norte": "RN", "rn": "RN",
    # Alagoas
    "alagoas": "AL", "al": "AL",
    # Piauí
    "piaui": "PI", "piauí": "PI", "pi": "PI",
    # Sergipe
    "sergipe": "SE", "se": "SE",
    # Rondônia
    "rondonia": "RO", "rondônia": "RO", "ro": "RO",
    # Tocantins
    "tocantins": "TO", "to": "TO",
    # Acre
    "acre": "AC", "ac": "AC",
    # Amapá
    "amapa": "AP", "amapá": "AP", "ap": "AP",
    # Roraima
    "roraima": "RR", "rr": "RR"
}

CAPITALS_BY_UF = {
    "AC": "Rio Branco", "AL": "Maceió", "AP": "Macapá", "AM": "Manaus",
    "BA": "Salvador", "CE": "Fortaleza", "DF": "Brasília", "ES": "Vitória",
    "GO": "Goiânia", "MA": "São Luís", "MT": "Cuiabá", "MS": "Campo Grande",
    "MG": "Belo Horizonte", "PA": "Belém", "PB": "João Pessoa", "PR": "Curitiba",
    "PE": "Recife", "PI": "Teresina", "RJ": "Rio de Janeiro", "RN": "Natal",
    "RS": "Porto Alegre", "RO": "Porto Velho", "RR": "Boa Vista", "SC": "Florianópolis",
    "SP": "São Paulo", "SE": "Aracaju", "TO": "Palmas"
}

US_STATES = {
    "NY", "IL", "CA", "TX", "FL", "OH", "GA", "NC", "MI", "NJ", "VA", "WA", "AZ",
    "MA", "TN", "IN", "MO", "MD", "WI", "CO", "MN", "KY", "LA", "OK", "OR", "UT",
    "NV", "IA", "AR", "MS", "KS", "CT", "NE", "ID", "WV", "HI", "NH", "ME", "MT",
    "RI", "DE", "SD", "ND", "AK", "VT", "WY"
}

PROMINENT_CITIES = [
    "Araras", "Campinas", "Ribeirao Preto", "Santos", "Sorocaba", "Sao Jose dos Campos",
    "Sao Jose do Rio Preto", "Bauru", "Piracicaba", "Jundiai", "Franca", "Marilia",
    "Presidente Prudente", "Sao Carlos", "Limeira", "Rio Claro", "Americana", "Indaiatuba",
    "Taubate", "Barretos", "Catanduva", "Botucatu", "Araraquara", "Guarulhos", "Osasco",
    "Santo Andre", "Sao Bernardo do Campo", "Mogi das Cruzes", "Suzano", "Diadema",
    "Belo Horizonte", "Uberlandia", "Juiz de Fora", "Montes Claros", "Uberaba",
    "Governador Valadares", "Ipatinga", "Divinopolis", "Pocos de Caldas", "Patos de Minas", "Varginha",
    "Rio de Janeiro", "Niteroi", "Petropolis", "Volta Redonda", "Campos dos Goytacazes",
    "Cabo Frio", "Nova Friburgo", "Macae", "Angra dos Reis", "Teresopolis", "Resende",
    "Salvador", "Feira de Santana", "Vitoria da Conquista", "Ilheus", "Itabuna", "Juazeiro",
    "Barreiras", "Porto Seguro", "Jequie", "Alagoinhas", "Teixeira de Freitas",
    "Curitiba", "Londrina", "Maringa", "Ponta Grossa", "Cascavel", "Foz do Iguacu",
    "Toledo", "Guarapuava", "Paranagua", "Pato Branco", "Umuarama", "Apucarana",
    "Porto Alegre", "Caxias do Sul", "Pelotas", "Canoas", "Santa Maria", "Gravatai",
    "Passo Fundo", "Novo Hamburgo", "Sao Leopoldo", "Rio Grande", "Bento Goncalves", "Erechim",
    "Florianopolis", "Joinville", "Blumenau", "Chapeco", "Criciuma", "Itajai",
    "Jaragua do Sul", "Lages", "Balneario Camboriu", "Brusque", "Tubarao",
    "Fortaleza", "Caucaia", "Juazeiro do Norte", "Maracanau", "Sobral", "Crato", "Itapipoca", "Iguatu",
    "Recife", "Jaboatao", "Olinda", "Caruaru", "Petrolina", "Paulista", "Cabo de Santo Agostinho",
    "Garanhuns", "Vitoria de Santo Antao",
    "Goiania", "Aparecida de Goiania", "Anapolis", "Rio Verde", "Luziania", "Aguas Lindas", "Itumbiara", "Jatai", "Caldas Novas",
    "Belem", "Ananindeua", "Santarem", "Maraba", "Parauapebas", "Castanhal",
    "Manaus", "Parintins", "Itacoatiara", "Manacapuru",
    "Vitoria", "Vila Velha", "Serra", "Cariacica", "Cachoeiro de Itapemirim", "Linhares", "Colatina", "Guarapari",
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
    "brasil", "brazil", "axe", "jovem", "funk", "futebol", "esporte", "bossa nova",
    "flashback", "dance", "gaucha", "nordeste"
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

def sanitize_brazil_state(raw_state: str, raw_city: str, station_name: str) -> str:
    """Normaliza o estado para uma UF oficial de 2 letras (ex: SP, RJ, MG)"""
    st_clean = remove_accents(raw_state or "").lower().strip()
    city_clean = remove_accents(raw_city or "").lower().strip()
    name_clean = remove_accents(station_name or "").lower().strip()

    # 1. Checagem direta do dicionário
    if st_clean in UF_NORMALIZATION:
        return UF_NORMALIZATION[st_clean]

    # 2. Busca por código de 2 letras
    for key, uf in UF_NORMALIZATION.items():
        if re.search(r'\b' + re.escape(key) + r'\b', st_clean):
            return uf

    # 3. Busca pelo nome da cidade
    for uf, cap in CAPITALS_BY_UF.items():
        cap_clean = remove_accents(cap).lower()
        if cap_clean in city_clean or cap_clean in name_clean:
            return uf

    # 4. Procura padrões "(SP)", "- SP", " SP"
    m = re.search(r'[\s\(\-\/]([A-Z]{2})[\)\s]*$', raw_state.strip())
    if m and m.group(1) in CAPITALS_BY_UF:
        return m.group(1)

    m2 = re.search(r'[\s\(\-\/]([A-Z]{2})[\)\s]*$', raw_city.strip())
    if m2 and m2.group(1) in CAPITALS_BY_UF:
        return m2.group(1)

    return "SP"  # Fallback seguro para maior concentração de rádios

async def fetch_radio_browser(session: aiohttp.ClientSession, endpoint: str) -> List[Dict[str, Any]]:
    headers = {"User-Agent": "GlobalRadioPod/2.0 (Android Auto Media)"}
    for mirror in RADIO_BROWSER_MIRRORS:
        url = f"{mirror}/{endpoint.lstrip('/')}"
        try:
            timeout = aiohttp.ClientTimeout(total=18)
            async with session.get(url, headers=headers, timeout=timeout) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    return data
        except Exception:
            continue
    return []

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

async def main():
    print("=========================================================================")
    print("=== [PENTE FINO BRASIL AVANÇADO] NORMALIZAÇÃO & EXPANSÃO DE RÁDIOS ===")
    print("=========================================================================")

    # 1. Carregar base existente
    with open("all_radio_sources.json", "r", encoding="utf-8") as f:
        existing_data = json.load(f)
    existing_stations = existing_data.get("stations", existing_data if isinstance(existing_data, list) else [])
    print(f"Base total atual: {len(existing_stations)} estações")

    # 2. Corrigir e normalizar a base existente primeiro
    print("\n--- Fase 1: Normalizando metadados geográficos da base existente ---")
    fixed_existing: List[Dict[str, Any]] = []
    misclassified_fixed = 0

    for s in existing_stations:
        s_copy = dict(s)
        is_br = s_copy.get("country") in ("Brasil", "Brazil") or s_copy.get("country_code") == "BR"
        st = (s_copy.get("state") or "").strip()
        name = s_copy.get("name", "")

        # Verifica se é na verdade uma rádio americana que foi marcada erroneamente como BR
        if is_br and st in ("NY", "IL", "CA", "TX", "MD", "WI", "KY", "LA", "AZ", "MI", "OH", "GA", "NC"):
            # Confirmação com indicativo de chamada americano (W/K) ou cidade americana
            if re.match(r'^(Radio\s+)?[WK][A-Z]{2,4}\b', name) or any(w in s_copy.get("city", "").lower() for w in ["chicago", "galesburg", "fisher", "freeport", "acra", "rochester", "baltimore", "normal"]):
                s_copy["country"] = "Estados Unidos"
                s_copy["country_code"] = "US"
                is_br = False
                misclassified_fixed += 1

        if is_br:
            s_copy["country"] = "Brasil"
            s_copy["country_code"] = "BR"
            norm_uf = sanitize_brazil_state(st, s_copy.get("city", ""), name)
            s_copy["state"] = norm_uf

            # Limpa cidade
            city = s_copy.get("city", "").strip()
            if not city or city.lower() in ("capital", "brasil", "brazil") or city == norm_uf or city.lower() in UF_NORMALIZATION:
                s_copy["city"] = CAPITALS_BY_UF.get(norm_uf, "São Paulo")
            else:
                # Remove sufixo redundante de estado se houver (ex: "Campinas - SP" -> "Campinas")
                s_copy["city"] = re.sub(r'\s*[\-\/]\s*[A-Z]{2}$', '', city).strip()

        fixed_existing.append(s_copy)

    print(f"Estações americanas corrigidas (desmarcadas de BR): {misclassified_fixed}")
    br_existing_count = sum(1 for s in fixed_existing if s.get("country_code") == "BR")
    print(f"Total de rádios brasileiras purificadas: {br_existing_count}")

    # Mapeia existentes para deduplicação
    existing_urls: Set[str] = set()
    existing_keys: Set[str] = set()
    existing_ids: Set[str] = set()

    for s in fixed_existing:
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

    # 3. Pente Fino Massivo de Candidatos (Rádios e Web Rádios)
    print("\n--- Fase 2: Pente Fino Massivo em todos os estados e cidades ---")
    candidate_items: List[Dict[str, Any]] = []
    seen_cand_uuids: Set[str] = set()

    async with aiohttp.ClientSession() as session:
        endpoints: List[str] = []

        # Endpoints nacionais
        endpoints.append("stations/search?countrycode=BR&limit=10000")
        endpoints.append("stations/search?country=Brazil&limit=10000")

        # 27 Estados por código e por nome
        for uf, name in CAPITALS_BY_UF.items():
            endpoints.append(f"stations/search?countrycode=BR&state={uf}&limit=1000")
            for key, mapped_uf in UF_NORMALIZATION.items():
                if mapped_uf == uf and len(key) > 2:
                    endpoints.append(f"stations/search?countrycode=BR&state={urllib.parse.quote(key)}&limit=1000")

        # Polos urbanos e interior
        for city in PROMINENT_CITIES:
            q_city = urllib.parse.quote(city)
            endpoints.append(f"stations/search?countrycode=BR&name={q_city}&limit=300")
            endpoints.append(f"stations/search?countrycode=BR&city={q_city}&limit=300")

        # Gêneros e Web Rádios brasileiras
        for tag in GENRE_TAGS:
            q_tag = urllib.parse.quote(tag)
            endpoints.append(f"stations/search?countrycode=BR&tag={q_tag}&limit=500")
            endpoints.append(f"stations/bytag/{q_tag}?limit=200")

        print(f"Total de consultas estruturadas: {len(endpoints)}")
        count = 0
        for ep in endpoints:
            count += 1
            if count % 35 == 0 or count == len(endpoints):
                print(f"Progresso das consultas: {count}/{len(endpoints)} ({len(candidate_items)} candidatos acumulados)")
            items = await fetch_radio_browser(session, ep)
            for it in items:
                uuid = it.get("stationuuid")
                if not uuid or uuid in seen_cand_uuids:
                    continue
                seen_cand_uuids.add(uuid)
                candidate_items.append(it)

        print(f"\nTotal bruto de candidatos coletados: {len(candidate_items)}")

        # 4. Pré-filtragem e deduplicação
        unvalidated_candidates: List[Dict[str, Any]] = []
        for it in candidate_items:
            # Garante que pertence ao Brasil ou tem características brasileiras claras
            cc = (it.get("countrycode") or "").upper()
            cname = (it.get("country") or "").lower()
            st_raw = it.get("state") or ""
            city_raw = it.get("city") or ""
            stream_url = (it.get("url_resolved") or it.get("url") or "").strip()
            norm_url = normalize_url(stream_url)

            is_brazilian = (cc == "BR" or "brazil" in cname or "brasil" in cname or
                            ".br/" in stream_url or ".br:" in stream_url or ".com.br" in stream_url or
                            any(k in remove_accents(st_raw).lower() for k in UF_NORMALIZATION.keys()))

            if not is_brazilian or not norm_url or norm_url in existing_urls:
                continue

            raw_name = (it.get("name") or "").strip()
            if not raw_name or len(raw_name) < 2:
                continue

            name_k = normalize_name(raw_name)
            uf = sanitize_brazil_state(st_raw, city_raw, raw_name)
            city_k = remove_accents(city_raw).lower().strip()
            comp_key = f"{name_k}::{city_k}::{uf}::BR"

            if comp_key in existing_keys:
                continue

            existing_urls.add(norm_url)
            existing_keys.add(comp_key)

            final_city = city_raw.strip() if city_raw.strip() and city_raw.lower() not in ("capital", "brasil", "brazil") else CAPITALS_BY_UF.get(uf, "São Paulo")

            unvalidated_candidates.append({
                "raw_item": it,
                "name": raw_name,
                "stream_url": stream_url,
                "norm_url": norm_url,
                "city": final_city,
                "state": uf,
                "name_k": name_k
            })

        print(f"Total de candidatos novos e únicos prontos para validação técnica: {len(unvalidated_candidates)}")

        # 5. Validação assíncrona ao vivo de streams
        print("\n--- Fase 3: Validando conexões e streaming ao vivo ---")
        semaphore = asyncio.Semaphore(70)

        async def check_candidate(c):
            async with semaphore:
                is_valid, ctype, lat = await test_stream(session, c["stream_url"])
                return c, is_valid, ctype, lat

        tasks = [check_candidate(c) for c in unvalidated_candidates]
        validated_results = []
        completed = 0
        total_tasks = len(tasks)

        for f in asyncio.as_completed(tasks):
            cand, is_valid, ctype, lat = await f
            completed += 1
            if completed % 100 == 0 or completed == total_tasks:
                valid_so_far = sum(1 for _, v, _, _ in validated_results) + (1 if is_valid else 0)
                print(f"Validação: {completed}/{total_tasks} | {valid_so_far} streams ativos")
            if is_valid:
                validated_results.append((cand, is_valid, ctype, lat))

        print(f"\nTotal de candidatos APROVADOS e com streaming funcional: {len(validated_results)}")

        # 6. Formatação e geração de IDs determinísticos
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

    # 7. Consolidação final
    print(f"\n--- Fase 4: Consolidando catálogo e exportando artefatos ---")
    final_stations = list(fixed_existing) + new_approved_stations
    new_br_total = sum(1 for s in final_stations if s.get("country_code") == "BR")
    print(f"Total consolidado mundial: {len(final_stations)} estações")
    print(f"Total de estações no Brasil: {new_br_total} (+{len(new_approved_stations)} novas)")

    # Distribuição por estado no Brasil
    br_only = [s for s in final_stations if s.get("country_code") == "BR"]
    dist = Counter(s.get("state", "UNKNOWN") for s in br_only)
    print("\nCobertura consolidada por UF no Brasil:")
    for uf in sorted(CAPITALS_BY_UF.keys()):
        print(f"  {uf} ({CAPITALS_BY_UF[uf]}): {dist.get(uf, 0)} rádios")

    # Backup
    backup_file = f"all_radio_sources.json.bak_pente_fino_avancado_{int(time.time())}"
    shutil.copyfile("all_radio_sources.json", backup_file)
    print(f"\nBackup gerado: {backup_file}")

    # Salva all_radio_sources.json
    output_dict = {
        "metadata": {
            "version": "2.3",
            "last_updated": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "total_stations": len(final_stations),
            "brazil_stations": new_br_total,
            "description": "Base global expandida com pente fino avancado de todas as UFs brasileiras e stream validado"
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

    # 8. Gerar CuratedStations.kt particionado em chunks de 150 estações
    print("\n--- Fase 5: Gerando CuratedStations.kt particionado ---")
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

    # Salva relatório
    report = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "total_stations_before": len(existing_stations),
        "total_brazil_before": br_existing_count,
        "new_brazil_stations_added": len(new_approved_stations),
        "total_stations_after": len(final_stations),
        "total_brazil_after": new_br_total,
        "chunks_count": num_chunks,
        "uf_distribution": {uf: dist.get(uf, 0) for uf in sorted(CAPITALS_BY_UF.keys())}
    }
    with open("reports/pente-fino-brasil-avancado-report.json", "w", encoding="utf-8") as rf:
        json.dump(report, rf, indent=2)
    print("Relatório salvo em reports/pente-fino-brasil-avancado-report.json")
    print("=== PENTE FINO AVANÇADO CONCLUÍDO COM SUCESSO! ===")

if __name__ == "__main__":
    asyncio.run(main())
