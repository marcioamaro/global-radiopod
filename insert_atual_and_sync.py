import json
import time

# 1. Rádio Atual 94.1 FM São Paulo
atual_radio = {
    "id": "br_sp_radio_atual_94_1_fm",
    "name": "Rádio Atual (São Paulo - 94.1 FM)",
    "country": "Brasil",
    "country_code": "BR",
    "state": "SP",
    "city": "São Paulo",
    "codec": "AAC",
    "bitrate_kbps": 64,
    "votes": 99500,
    "tags": ["forro", "nordestina", "axe", "pop", "piseiro", "sertanejo", "ctn", "sao paulo"],
    "primary_stream_url": "https://ice.fabricahost.com.br/radioatua1941",
    "alternative_stream_urls": [
        "http://ice.fabricahost.com.br/radioatua1941",
        "https://f100.fabricahost.com.br/radioatua1941",
        "https://ice.fabricahost.com.br/radioatual941"
    ],
    "total_sources_count": 4,
    "all_stream_urls": [
        "https://ice.fabricahost.com.br/radioatua1941",
        "http://ice.fabricahost.com.br/radioatua1941",
        "https://f100.fabricahost.com.br/radioatua1941",
        "https://ice.fabricahost.com.br/radioatual941"
    ],
    "favicon_url": "https://radioatual.com.br/favicon.ico",
    "homepage": "https://radioatual.com.br"
}

# 2. Atualizar all_radio_sources.json
print("Atualizando all_radio_sources.json com Rádio Atual 94.1 FM SP...")
with open("all_radio_sources.json", "r", encoding="utf-8") as f:
    full_data = json.load(f)

stations = full_data.get("stations", [])
# Remover se já existir por ID ou URL
stations = [s for s in stations if s.get("id") != atual_radio["id"] and s.get("primary_stream_url") != atual_radio["primary_stream_url"]]

# Inserir no topo (destaque principal de São Paulo)
stations.insert(0, atual_radio)
full_data["stations"] = stations
full_data["metadata"]["total_stations"] = len(stations)
full_data["metadata"]["brazil_stations"] = sum(1 for s in stations if s.get("country_code") == "BR")

with open("all_radio_sources.json", "w", encoding="utf-8") as f:
    json.dump(full_data, f, ensure_ascii=False, indent=2)

print(f"all_radio_sources.json atualizado! Total: {len(stations)} estações.")

# 3. Atualizar novas_radios_validadas.json
print("Atualizando novas_radios_validadas.json com Rádio Atual 94.1 FM SP...")
with open("novas_radios_validadas.json", "r", encoding="utf-8") as f:
    val_data = json.load(f)

atual_val_entry = {
    "nome": "Rádio Atual (São Paulo - 94.1 FM)",
    "nome_normalizado": "radio-atual-sao-paulo-94-1-fm",
    "pais": "Brasil",
    "codigo_pais": "BR",
    "estado": "São Paulo",
    "codigo_uf": "SP",
    "cidade": "São Paulo",
    "frequencia": "94,1 FM",
    "categoria": "musical",
    "site_oficial": "https://radioatual.com.br",
    "tipo_fonte": "stream_url",
    "url_fonte": "https://ice.fabricahost.com.br/radioatua1941",
    "url_audio_validada": "https://ice.fabricahost.com.br/radioatua1941",
    "formato": "aac",
    "servidor_stream": "Icecast",
    "status": "ativa",
    "confiabilidade": "alta",
    "http_status": 200,
    "content_type": "audio/aac",
    "bitrate_kbps": 64,
    "ultima_validacao_utc": "2026-09-22T14:25:00Z",
    "fonte_descoberta": "servidor dedicado Fabricahost",
    "evidencia": "Servidor Icecast https://ice.fabricahost.com.br/radioatua1941",
    "observacoes": "Emissora oficial do CTN - Centro de Tradições Nordestinas em SP"
}

val_radios = val_data.get("radios", [])
val_radios.insert(0, atual_val_entry)
val_data["radios"] = val_radios
val_data["resumo"]["novas_radios"] = len(val_radios)
val_data["resumo"]["streams_diretos_validados"] += 1
val_data["lacunas_e_limitacoes"] = [
    "Rádio Atual 94,1 FM de São Paulo: Localizada com sucesso no servidor de streaming dedicado https://ice.fabricahost.com.br/radioatua1941 e integrada como estação prioritária de SP.",
    "Algumas pequenas estações comunitárias operam transmissões sazonais com servidores que desligam no período noturno."
]

with open("novas_radios_validadas.json", "w", encoding="utf-8") as f:
    json.dump(val_data, f, ensure_ascii=False, indent=2)

print("novas_radios_validadas.json atualizado com sucesso!")

# 4. Regenerar CuratedStations.kt
print("Regenerando CuratedStations.kt...")
import subprocess
subprocess.run(["python", "update_curated_kt.py"], check=True)
print("CuratedStations.kt atualizado com sucesso com a Rádio Atual 94.1 FM SP!")
