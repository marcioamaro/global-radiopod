import urllib.request
import urllib.parse
import json
import re
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# 1. Testar Rádio Atual SP 94.1
url_atual = "https://ice.fabricahost.com.br/radioatua1941"
print(f"Testando {url_atual}...")
try:
    req = urllib.request.Request(url_atual, headers=headers)
    with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
        print("Status:", resp.status)
        print("Headers:", dict(resp.headers))
        chunk = resp.read(1024)
        print(f"Lidos {len(chunk)} bytes de áudio com sucesso!")
except Exception as e:
    print("Erro ao testar Atual:", e)

# 2. Explorar status.xsl / status-json.xsl no ice.fabricahost.com.br e outros servidores da Fabricahost
servers_to_probe = [
    "https://ice.fabricahost.com.br",
    "http://ice.fabricahost.com.br",
    "http://ice.fabricahost.com.br:8000",
    "http://ice.fabricahost.com.br:80",
    "https://f100.fabricahost.com.br",
    "http://f100.fabricahost.com.br",
    "https://f200.fabricahost.com.br",
    "http://stm.fabricahost.com.br"
]

status_paths = [
    "/status-json.xsl",
    "/status.xsl",
    "/status2.xsl",
    "/server-status"
]

found_mounts = []

for s in servers_to_probe:
    for p in status_paths:
        target = s + p
        try:
            req = urllib.request.Request(target, headers=headers)
            with urllib.request.urlopen(req, timeout=5, context=ctx) as resp:
                data = resp.read().decode('utf-8', errors='ignore')
                print(f"[{target}] Respondeu {resp.status}, tamanho: {len(data)} bytes")
                # Se for json
                if p == "/status-json.xsl":
                    try:
                        j = json.loads(data)
                        icestats = j.get("icestats", {})
                        sources = icestats.get("source", [])
                        if isinstance(sources, dict):
                            sources = [sources]
                        for src in sources:
                            listenurl = src.get("listenurl") or src.get("mount")
                            title = src.get("server_name") or src.get("title") or src.get("server_description") or listenurl
                            genre = src.get("genre") or ""
                            print(f"  -> Mount JSON: {listenurl} | {title} | {genre}")
                            found_mounts.append((listenurl, title, genre))
                    except Exception as err:
                        print("Erro ao parsear json:", err)
                else:
                    # Parse HTML do status.xsl
                    mounts = re.findall(r'href=[\"\']?(/[^\"\'\s>]+)[\"\']?', data)
                    titles = re.findall(r'Stream Title:\s*</td><td class=\"streamdata\">([^<]+)</td>', data)
                    print(f"  -> Encontrados {len(mounts)} links no HTML")
                    for m in set(mounts):
                        if any(ext in m for ext in ['.mp3', '.aac', '.ogg', '/']) and not any(ign in m for ign in ['.css', '.xsl', '.js', '.png', '.jpg']):
                            full_stream_url = s.rstrip('/') + m
                            print(f"  -> Mount HTML: {full_stream_url}")
                            found_mounts.append((full_stream_url, m.replace('/', ''), ''))
        except Exception as e:
            # print(f"[{target}] Falhou: {e}")
            pass

print(f"\nTotal de pontos de transmissão encontrados em fabricahost: {len(found_mounts)}")
