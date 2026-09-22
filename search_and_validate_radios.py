import urllib.request
import urllib.parse
import json
import re
import socket
import ssl
import time
from datetime import datetime, timezone

# 1. Carregar base existente para deduplicação
print("Carregando base existente all_radio_sources.json...")
existing_urls = set()
existing_names = set()
existing_name_city = set()

try:
    with open("all_radio_sources.json", "r", encoding="utf-8") as f:
        existing_data = json.load(f)
        for r in existing_data:
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
    print(f"Base existente: {len(existing_data)} radios, {len(existing_urls)} URLs indexadas.")
except Exception as e:
    print("Erro ao carregar base existente:", e)
    existing_data = []

# 2. Investigar Atual 94.1 FM
print("\n--- Investigando Atual 94.1 FM SP ---")
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Accept': '*/*',
    'Icy-MetaData': '1'
}

atual_stream_candidates = []
atual_queries = [
    "https://tudoradio.com/radios/ver/2568-radio-atual-fm-94-1-sao-paulo",
    "https://www.radios.com.br/aovivo/radio-atual-94-1-fm/33537",
    "https://radioatual.com.br"
]

for url in atual_queries:
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=8) as response:
            html = response.read().decode('utf-8', errors='ignore')
            # Procurar padrões de stream
            found = re.findall(r'(https?://[^\s\"\'\<\>]+(?:\.m3u8|\.aac|\.mp3|/stream|:8\d{3}|:9\d{3}|:7\d{3}|/live)[^\s\"\'\<\>]*)', html)
            for f in found:
                if not any(x in f.lower() for x in ['.js', '.css', '.png', '.jpg', 'google', 'facebook', 'radios.com.br/player', 'tudoradio.com/player']):
                    print(f"[{url}] Encontrado potencial stream:", f)
                    atual_stream_candidates.append(f)
            # Procurar dados json ou player iframe
            players = re.findall(r'(https?://[^\s\"\'\<\>]+(?:shoutca\.st|srvsh\.com\.br|hdradios\.net|livecdn\.biz|streamhost\.com\.br|crosshost\.com\.br)[^\s\"\'\<\>]*)', html)
            for p in players:
                print(f"[{url}] Encontrado servidor:", p)
                atual_stream_candidates.append(p)
    except Exception as e:
        print(f"Falha ao consultar {url}: {e}")

# Testar Radio Browser API para Atual FM e outras
print("\nBuscando na API Radio Browser...")
rb_servers = [
    "https://de1.api.radio-browser.info",
    "https://nl1.api.radio-browser.info",
    "https://at1.api.radio-browser.info"
]

def query_radio_browser(endpoint, params):
    for base in rb_servers:
        try:
            query = urllib.parse.urlencode(params)
            url = f"{base}/json/{endpoint}?{query}"
            req = urllib.request.Request(url, headers={'User-Agent': 'MediaPodValidator/2.0'})
            with urllib.request.urlopen(req, timeout=10) as r:
                return json.loads(r.read().decode('utf-8'))
        except Exception as e:
            continue
    return []

atual_rb = query_radio_browser("stations/search", {"name": "Atual", "countrycode": "BR"})
print(f"Radio Browser busca 'Atual BR': {len(atual_rb)} resultados")
for st in atual_rb:
    print("  ->", st.get("name"), "|", st.get("state"), "|", st.get("url_resolved") or st.get("url"))

