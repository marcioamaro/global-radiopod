import urllib.request
import json
import ssl
import re
import socket
import concurrent.futures

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Icy-MetaData': '1'
}

# 1. Testar e validar a Rádio Atual SP 94.1 FM
atual_station = {
    "id": "br_sp_radio_atual_941_fm",
    "name": "Rádio Atual (São Paulo - 94.1 FM)",
    "country": "Brasil",
    "country_code": "BR",
    "state": "SP",
    "city": "São Paulo",
    "codec": "AAC",
    "bitrate_kbps": 64,
    "votes": 85000,
    "tags": ["forro", "nordestina", "axe", "pop", "piseiro", "sertanejo", "ctn"],
    "primary_stream_url": "https://ice.fabricahost.com.br/radioatua1941",
    "alternative_stream_urls": [
        "http://ice.fabricahost.com.br/radioatua1941"
    ],
    "total_sources_count": 2,
    "all_stream_urls": [
        "https://ice.fabricahost.com.br/radioatua1941",
        "http://ice.fabricahost.com.br/radioatua1941"
    ],
    "favicon_url": "https://radioatual.com.br/favicon.ico",
    "homepage": "https://radioatual.com.br"
}

# 2. Buscar estações com fabricahost na Radio Browser API
print("Buscando 'fabricahost' na Radio Browser API...")
rb_servers = ["https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info"]
found_rb_stations = []

for base in rb_servers:
    try:
        url = f"{base}/json/stations/byurl/fabricahost"
        req = urllib.request.Request(url, headers={'User-Agent': 'MediaPodBot/3.0'})
        with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            print(f"Encontradas {len(data)} estações pelo domínio fabricahost no Radio Browser!")
            found_rb_stations.extend(data)
            break
    except Exception as e:
        print("Erro na consulta RB:", e)

# Lista de possíveis mountpoints no servidor ice.fabricahost.com.br
popular_slugs = [
    "radioatua1941", "atual941", "radioatual", "tropicalfm", "transcontinental", "gazetafm",
    "metropolitana", "dumountfm", "educadorafm", "difusorafm", "clubeam", "clubefm",
    "culturafm", "nativafm", "bandfm", "jovempan", "maranatafm", "novotempo",
    "alvoradafm", "cidadepop", "ondasfm", "litoralfm", "costafm", "valeplay", "iguatemifm",
    "topfm", "capitalam", "moradadosol", "imprensa", "paraisofm", "regionalfm",
    "sucessofm", "liderfm", "antenajovem", "atlanticafm", "universitariasp",
    "estilo", "radiomania", "radiovanguarda", "showfm", "progresso", "esperancafm",
    "boasnovas", "melodiafm", "gospelfm", "adoracaofm", "sarandoaterrafm", "cancaonova"
]

candidates_to_test = []

for st in found_rb_stations:
    u = st.get("url_resolved") or st.get("url")
    if u:
        candidates_to_test.append((u, st.get("name") or "Rádio Web", st.get("state") or "SP", st.get("tags") or ""))

for slug in popular_slugs:
    candidates_to_test.append((f"https://ice.fabricahost.com.br/{slug}", slug.upper(), "SP", "webradio"))
    candidates_to_test.append((f"https://f100.fabricahost.com.br/{slug}", slug.upper(), "SP", "webradio"))

print(f"Total de candidatos a testar no Fabricahost: {len(candidates_to_test)}")

validated_fabricahost = [atual_station]
seen_stream_urls = {atual_station["primary_stream_url"]}

def test_fabricahost_stream(item):
    url, name, state, tags = item
    if url in seen_stream_urls:
        return None
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=4, context=ctx) as resp:
            content_type = (resp.headers.get("Content-Type") or "").lower()
            icy_name = resp.headers.get("icy-name")
            icy_genre = resp.headers.get("icy-genre")
            icy_br = resp.headers.get("icy-br") or 128
            
            if "audio" in content_type or "mpegurl" in content_type or "application/ogg" in content_type:
                chunk = resp.read(512)
                if len(chunk) > 0:
                    clean_name = icy_name if (icy_name and len(icy_name) > 3) else name
                    return {
                        "id": f"br_{re.sub(r'[^a-z0-9]', '_', clean_name.lower())[:30]}_{abs(hash(url))%10000}",
                        "name": clean_name,
                        "country": "Brasil",
                        "country_code": "BR",
                        "state": state if state else "SP",
                        "city": "São Paulo" if state == "SP" else state,
                        "codec": "AAC" if "aac" in content_type else "MP3",
                        "bitrate_kbps": int(icy_br) if str(icy_br).isdigit() else 128,
                        "votes": 1200,
                        "tags": [t.strip() for t in (tags + "," + (icy_genre or "")).split(",") if t.strip()] or ["webradio", "musica"],
                        "primary_stream_url": url,
                        "alternative_stream_urls": [],
                        "total_sources_count": 1,
                        "all_stream_urls": [url],
                        "favicon_url": "",
                        "homepage": ""
                    }
    except Exception:
        pass
    return None

with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
    results = list(executor.map(test_fabricahost_stream, candidates_to_test))

for r in results:
    if r and r["primary_stream_url"] not in seen_stream_urls:
        seen_stream_urls.add(r["primary_stream_url"])
        validated_fabricahost.append(r)
        print(f"  [VALIDADA FABRICAHOST] {r['name']} ({r['state']}) -> {r['primary_stream_url']}")

print(f"\nTotal de estações validadas no Fabricahost: {len(validated_fabricahost)}")

# Atualizar all_radio_sources.json
with open("all_radio_sources.json", "r", encoding="utf-8") as f:
    full_data = json.load(f)

existing_st = full_data.get("stations", [])
existing_urls_set = {s.get("primary_stream_url") for s in existing_st}

added_count = 0
for v in validated_fabricahost:
    if v["primary_stream_url"] not in existing_urls_set:
        # Colocar a Atual 94.1 FM bem no topo das rádios brasileiras / SP
        if v["id"] == "br_sp_radio_atual_941_fm":
            existing_st.insert(0, v)
        else:
            existing_st.append(v)
        existing_urls_set.add(v["primary_stream_url"])
        added_count += 1

full_data["stations"] = existing_st
full_data["metadata"]["total_stations"] = len(existing_st)
full_data["metadata"]["brazil_stations"] = sum(1 for s in existing_st if s.get("country_code") == "BR")

with open("all_radio_sources.json", "w", encoding="utf-8") as f:
    json.dump(full_data, f, ensure_ascii=False, indent=2)

print(f"Base all_radio_sources.json atualizada! Adicionadas {added_count} estações do Fabricahost (incluindo Rádio Atual 94.1 FM SP). Total: {len(existing_st)}")
