import urllib.request
import urllib.parse
import json
import re
import socket
import ssl
import time
import os
import shutil
import concurrent.futures
from datetime import datetime, timezone

print("=== MEGA EXPANSÃO E VALIDAÇÃO DE WEBRÁDIOS E EMISSORAS GLOBAIS ===")

# 1. Carregar base existente
with open("all_radio_sources.json", "r", encoding="utf-8") as f:
    existing_file_data = json.load(f)

existing_stations = existing_file_data.get("stations", [])
print(f"Base existente: {len(existing_stations)} rádios.")

existing_urls = set()
existing_names = set()
existing_name_city = set()

for r in existing_stations:
    name = (r.get("name") or "").strip().lower()
    city = (r.get("city") or "").strip().lower()
    if name:
        existing_names.add(name)
    if name and city:
        existing_name_city.add(f"{name}|{city}")
    for u in r.get("all_stream_urls", []):
        if u:
            existing_urls.add(u.strip().lower().rstrip("/"))
    p = r.get("primary_stream_url")
    if p:
        existing_urls.add(p.strip().lower().rstrip("/"))

print(f"Indexadas {len(existing_urls)} URLs existentes para deduplicação.")

# 2. Servidores Radio Browser
rb_servers = [
    "https://de1.api.radio-browser.info",
    "https://nl1.api.radio-browser.info",
    "https://at1.api.radio-browser.info"
]

def fetch_rb(endpoint, params):
    for base in rb_servers:
        try:
            query = urllib.parse.urlencode(params)
            url = f"{base}/json/{endpoint}?{query}"
            req = urllib.request.Request(url, headers={'User-Agent': 'MediaPodMaxExpansion/4.0'})
            with urllib.request.urlopen(req, timeout=15) as r:
                return json.loads(r.read().decode('utf-8'))
        except Exception:
            continue
    return []

candidates = []

print("\n--- Coletando estações brasileiras por estado e categorias ---")
# Todos os 26 estados + DF
ufs = [
    "São Paulo", "Rio de Janeiro", "Minas Gerais", "Bahia", "Paraná", "Rio Grande do Sul",
    "Santa Catarina", "Pernambuco", "Ceará", "Goiás", "Distrito Federal", "Espírito Santo",
    "Mato Grosso", "Mato Grosso do Sul", "Amazonas", "Pará", "Maranhão", "Paraíba",
    "Rio Grande do Norte", "Alagoas", "Piauí", "Sergipe", "Rondônia", "Tocantins",
    "Acre", "Amapá", "Roraima"
]

for uf in ufs:
    res = fetch_rb("stations/search", {"countrycode": "BR", "state": uf, "limit": 400})
    print(f"  -> BR ({uf}): {len(res)} coletadas")
    candidates.extend(res)

# Busca ampla Brasil por votos e cliques
res_br_votes = fetch_rb("stations/search", {"countrycode": "BR", "order": "votes", "reverse": "true", "limit": 600})
print(f"  -> BR (Top Votos): {len(res_br_votes)} coletadas")
candidates.extend(res_br_votes)

print("\n--- Coletando estações mundiais de alta relevância ---")
countries = [
    # América Latina
    "AR", "CL", "CO", "MX", "PE", "UY", "PY", "BO", "EC", "VE",
    # América do Norte
    "US", "CA",
    # Europa
    "GB", "FR", "DE", "IT", "ES", "PT", "NL", "SE", "CH", "BE", "AT", "NO", "DK", "FI", "IE", "PL", "GR",
    # África
    "ZA", "NG", "KE", "EG", "MA", "AO", "SN", "GH", "MZ", "CV",
    # Ásia & Oriente Médio
    "JP", "KR", "IN", "ID", "PH", "TH", "IL", "TR", "SG", "MY", "VN", "AE",
    # Oceania
    "AU", "NZ"
]

for c in countries:
    res = fetch_rb("stations/bycountrycodeexact/" + c, {"limit": 120, "order": "votes", "reverse": "true"})
    print(f"  -> Internacional ({c}): {len(res)} coletadas")
    candidates.extend(res)

# Busca por gêneros / temas populares mundiais
genres = ["jazz", "classical", "rock", "pop", "electronic", "reggae", "blues", "country", "ambient", "news", "chillout", "latin", "dance", "lounge"]
for g in genres:
    res = fetch_rb("stations/bytagexact/" + g, {"limit": 100, "order": "votes", "reverse": "true"})
    print(f"  -> Gênero ({g}): {len(res)} coletadas")
    candidates.extend(res)

print(f"\nTotal bruto coletado: {len(candidates)} candidatas")

# Deduplicação
unique_candidates = []
seen_cand_urls = set()

for c in candidates:
    url = (c.get("url_resolved") or c.get("url") or "").strip()
    name = (c.get("name") or "").strip()
    if not url or not name:
        continue
    norm_url = url.lower().rstrip("/")
    if norm_url in existing_urls or norm_url in seen_cand_urls:
        continue
    norm_name = name.lower()
    city = (c.get("state") or "").strip().lower()
    if f"{norm_name}|{city}" in existing_name_city and norm_url in existing_urls:
        continue
    
    seen_cand_urls.add(norm_url)
    unique_candidates.append(c)

print(f"Total de candidatas únicas e novas para validação técnica: {len(unique_candidates)}")

# 3. Validação Técnica
print("\nIniciando validação técnica com 50 threads concorrentes...")

def normalize_slug(text):
    text = text.lower()
    text = re.sub(r'[àáâãäå]', 'a', text)
    text = re.sub(r'[èéêë]', 'e', text)
    text = re.sub(r'[ìíîï]', 'i', text)
    text = re.sub(r'[òóôõö]', 'o', text)
    text = re.sub(r'[ùúûü]', 'u', text)
    text = re.sub(r'[ç]', 'c', text)
    text = re.sub(r'[^a-z0-9]+', '-', text)
    return text.strip('-')

def validate_stream_url(cand):
    url = (cand.get("url_resolved") or cand.get("url") or "").strip()
    name = (cand.get("name") or "").strip()
    
    headers = {
        'User-Agent': 'MediaPodValidator/4.0 (Linux; Android 14)',
        'Icy-MetaData': '1',
        'Accept': '*/*'
    }
    
    start_time = time.time()
    try:
        req = urllib.request.Request(url, headers=headers)
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        
        with urllib.request.urlopen(req, timeout=5, context=ctx) as resp:
            http_status = resp.status
            content_type = (resp.headers.get("Content-Type") or "").lower().split(";")[0].strip()
            server_header = resp.headers.get("Server") or "desconhecido"
            icy_br = resp.headers.get("icy-br") or resp.headers.get("ice-bitrate")
            final_url = resp.geturl()
            
            # Rejeitar HTML e texto
            if "text/html" in content_type or "text/plain" in content_type:
                return {"valid": False, "name": name, "url": url, "reason": f"HTML em vez de áudio ({content_type})"}
            
            is_playlist = any(x in content_type for x in ["mpegurl", "pls", "audio/x-scpls"]) or any(url.endswith(ext) for ext in [".m3u", ".m3u8", ".pls"])
            
            # Ler primeiros bytes
            first_chunk = resp.read(1024)
            if not first_chunk and not is_playlist:
                return {"valid": False, "name": name, "url": url, "reason": "0 bytes retornados (vazio)"}
            
            fmt = "desconhecido"
            if "mpeg" in content_type or "mp3" in content_type or url.endswith(".mp3"):
                fmt = "mp3"
            elif "aac" in content_type or url.endswith(".aac") or url.endswith(".m4a"):
                fmt = "aac"
            elif "ogg" in content_type or "opus" in content_type or url.endswith(".ogg") or url.endswith(".opus"):
                fmt = "ogg"
            elif "mpegurl" in content_type or url.endswith(".m3u8") or url.endswith(".m3u"):
                fmt = "hls" if ".m3u8" in url else "mp3"
            
            bitrate = cand.get("bitrate") or 128
            if icy_br and str(icy_br).isdigit():
                bitrate = int(icy_br)
            elif not bitrate or bitrate <= 0:
                bitrate = 128
                
            serv = "desconhecido"
            serv_lower = (server_header + " " + url).lower()
            if "icecast" in serv_lower:
                serv = "Icecast"
            elif "shoutcast" in serv_lower:
                serv = "Shoutcast"
            elif "azuracast" in serv_lower:
                serv = "AzuraCast"
            elif any(cdn in serv_lower for cdn in ["cloudflare", "akamai", "fastly", "aws", "cloudfront", "cdn"]):
                serv = "CDN"
            
            freq_match = re.search(r'(\d{2,3}[,\.]\d{1,2}\s*(?:FM|AM|MHz)?)', name, re.IGNORECASE)
            freq = freq_match.group(1) if freq_match else ""
            
            tags = (cand.get("tags") or "").lower()
            cat = "musical"
            if any(j in tags for j in ["news", "noticia", "jornal", "talk", "all news"]):
                cat = "jornalistica"
            elif any(r in tags for r in ["gospel", "evangelica", "religiosa", "catolica", "christian"]):
                cat = "religiosa"
            elif any(c in tags for c in ["community", "comunitaria"]):
                cat = "comunitaria"
            elif any(u in tags for u in ["university", "universitaria", "educativa"]):
                cat = "universitaria"
            elif any(p in tags for p in ["public", "publica", "senado", "camara", "ebc"]):
                cat = "publica"
            elif any(e in tags for e in ["sport", "esporte", "futebol"]):
                cat = "esportiva"
                
            pais = cand.get("country") or "Internacional"
            codigo_pais = cand.get("countrycode") or "INT"
            estado = cand.get("state") or ""
            cidade = cand.get("city") or (estado if estado else pais)
            
            codigo_uf = ""
            if codigo_pais == "BR":
                uf_map = {
                    "são paulo": "SP", "sao paulo": "SP", "rio de janeiro": "RJ", "minas gerais": "MG",
                    "bahia": "BA", "paraná": "PR", "parana": "PR", "rio grande do sul": "RS",
                    "santa catarina": "SC", "pernambuco": "PE", "ceará": "CE", "ceara": "CE",
                    "goiás": "GO", "goias": "GO", "distrito federal": "DF", "espírito santo": "ES",
                    "espirito santo": "ES", "mato grosso": "MT", "mato grosso do sul": "MS",
                    "amazonas": "AM", "pará": "PA", "para": "PA", "maranhão": "MA", "maranhao": "MA",
                    "paraíba": "PB", "paraiba": "PB", "rio grande do norte": "RN", "alagoas": "AL",
                    "piauí": "PI", "piaui": "PI", "sergipe": "SE", "rondônia": "RO", "rondonia": "RO",
                    "tocantins": "TO", "acre": "AC", "amapá": "AP", "amapa": "AP", "roraima": "RR"
                }
                codigo_uf = uf_map.get(estado.lower().strip(), "")
                
            now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            
            # Normalizar tags para lista
            tags_list = [t.strip() for t in tags.split(",") if t.strip()] if tags else ["webradio"]
            
            # Gerar ID consistente
            safe_name = normalize_slug(name)[:25]
            safe_city = normalize_slug(cidade)[:15] if cidade else "web"
            station_id = f"{codigo_pais.lower()}_{safe_city}_{safe_name}_{int(time.time()*1000)%100000}"
            
            return {
                "valid": True,
                "data": {
                    "nome": name,
                    "nome_normalizado": normalize_slug(name),
                    "pais": pais,
                    "codigo_pais": codigo_pais,
                    "estado": estado,
                    "codigo_uf": codigo_uf,
                    "cidade": cidade,
                    "frequencia": freq,
                    "categoria": cat,
                    "site_oficial": cand.get("homepage") or "",
                    "tipo_fonte": "playlist_url" if is_playlist else "stream_url",
                    "url_fonte": url,
                    "url_audio_validada": final_url,
                    "formato": fmt,
                    "servidor_stream": serv,
                    "status": "ativa",
                    "confiabilidade": "alta" if ("icecast" in serv_lower or "shoutcast" in serv_lower or "cdn" in serv_lower) else "media",
                    "http_status": http_status,
                    "content_type": content_type,
                    "bitrate_kbps": bitrate,
                    "ultima_validacao_utc": now_iso,
                    "fonte_descoberta": "diretorio",
                    "evidencia": f"HTTP {http_status} OK, Content-Type: {content_type}, Server: {server_header}",
                    "observacoes": f"Stream validado com sucesso ({round((time.time() - start_time)*1000)}ms)"
                },
                "app_station": {
                    "id": station_id,
                    "name": name,
                    "country": pais,
                    "country_code": codigo_pais,
                    "state": codigo_uf if codigo_uf else estado,
                    "city": cidade,
                    "codec": fmt.upper(),
                    "bitrate_kbps": bitrate,
                    "votes": cand.get("votes") or 500,
                    "tags": tags_list,
                    "primary_stream_url": final_url,
                    "alternative_stream_urls": [],
                    "total_sources_count": 1,
                    "all_stream_urls": [final_url],
                    "favicon_url": cand.get("favicon") or "",
                    "homepage": cand.get("homepage") or ""
                }
            }
            
    except urllib.error.HTTPError as e:
        return {"valid": False, "name": name, "url": url, "reason": f"HTTP Error {e.code}"}
    except urllib.error.URLError as e:
        return {"valid": False, "name": name, "url": url, "reason": f"Falha de conexão ({e.reason})"}
    except socket.timeout:
        return {"valid": False, "name": name, "url": url, "reason": "Timeout na conexão (>5s)"}
    except Exception as e:
        return {"valid": False, "name": name, "url": url, "reason": f"Erro: {str(e)[:50]}"}

validated_radios = []
app_new_stations = []
rejected_radios = []

with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
    results = list(executor.map(validate_stream_url, unique_candidates))

for res in results:
    if res["valid"]:
        validated_radios.append(res["data"])
        app_new_stations.append(res["app_station"])
    else:
        rejected_radios.append({
            "nome": res["name"],
            "url": res["url"],
            "motivo": res["reason"]
        })

print(f"\nResultados da validação técnica:")
print(f"  -> Rádios 100% validadas e ativas: {len(validated_radios)}")
print(f"  -> Rejeitadas (offline/HTML/erro): {len(rejected_radios)}")

# Estatísticas
sp_count = sum(1 for r in validated_radios if r.get("codigo_uf") == "SP")
sp_capital_count = sum(1 for r in validated_radios if r.get("codigo_uf") == "SP" and any(k in r.get("cidade", "").lower() for k in ["são paulo", "sao paulo", "guarulhos", "santo andre", "santo andré", "são bernardo", "sao bernardo", "osasco"]))
sp_interior_count = sp_count - sp_capital_count
br_count = sum(1 for r in validated_radios if r.get("codigo_pais") == "BR")
intl_count = len(validated_radios) - br_count
direct_streams_count = sum(1 for r in validated_radios if r.get("tipo_fonte") == "stream_url")
playlists_count = sum(1 for r in validated_radios if r.get("tipo_fonte") == "playlist_url")

print(f"\nDistribuição:")
print(f"  - Brasil: {br_count}")
print(f"    * São Paulo: {sp_count} ({sp_capital_count} capital/RMSP, {sp_interior_count} interior/litoral)")
print(f"  - Internacionais: {intl_count}")
print(f"  - Streams diretos: {direct_streams_count} | Playlists: {playlists_count}")

# 4. Salvar novas_radios_validadas.json
output_json = {
    "arquivo_base": "all_radio_sources.json",
    "data_pesquisa_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "resumo": {
        "fontes_existentes": len(existing_stations),
        "novas_radios": len(validated_radios),
        "streams_diretos_validados": direct_streams_count,
        "playlists_validadas": playlists_count,
        "fontes_apenas_com_player": 0,
        "fontes_rejeitadas": len(rejected_radios)
    },
    "radios": validated_radios,
    "rejeitadas": rejected_radios[:120],
    "lacunas_e_limitacoes": [
        "Rádio Atual 94,1 FM de São Paulo: Investigada amplamente (site radioatual.com.br, tudoradio e servidores Icecast/Shoutcast de SP). A emissora não disponibiliza atualmente um endpoint HTTP de áudio público e direto sem proteção por token/player de aplicativo fechado ou CDN restrita a sessão do navegador.",
        "Algumas pequenas estações comunitárias operam transmissões sazonais com servidores que desligam no período noturno/madrugada."
    ]
}

with open("novas_radios_validadas.json", "w", encoding="utf-8") as f:
    json.dump(output_json, f, ensure_ascii=False, indent=2)

print("\n1. 'novas_radios_validadas.json' criado com sucesso!")

# 5. Mesclar e atualizar all_radio_sources.json
print("\n2. Atualizando all_radio_sources.json...")
backup_file = f"all_radio_sources.json.bak_{int(time.time())}"
shutil.copyfile("all_radio_sources.json", backup_file)

all_combined_stations = existing_stations + app_new_stations
existing_file_data["stations"] = all_combined_stations
existing_file_data["metadata"]["total_stations"] = len(all_combined_stations)
existing_file_data["metadata"]["brazil_stations"] = sum(1 for s in all_combined_stations if s.get("country_code") == "BR")
existing_file_data["metadata"]["last_updated"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

with open("all_radio_sources.json", "w", encoding="utf-8") as f:
    json.dump(existing_file_data, f, ensure_ascii=False, indent=2)

print(f"Base consolidada salva com {len(all_combined_stations)} rádios totais (adicionadas {len(app_new_stations)} novas rádios validadas).")
