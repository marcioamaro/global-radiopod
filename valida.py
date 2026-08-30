import asyncio
import json
import os
import sys
import time
from typing import Any, Dict, List
import aiohttp

# Configurações do Validador
INPUT_JSON_PATH = r"d:/global-radiopod/all_radio_sources.json"
OUTPUT_REPORT_PATH = r"d:/global-radiopod/validation_report.json"

MAX_CONCURRENT_TASKS = 25       # Quantidade de streams testados simultaneamente
CONNECT_TIMEOUT_SEC = 8.0       # Tempo limite para conexão inicial TCP/TLS
STREAM_READ_TIMEOUT_SEC = 6.0   # Tempo limite para receber blocos de áudio contínuo
MIN_BYTES_TO_VALIDATE = 64 * 1024  # 64 KB de áudio recebido confirma que o fluxo está vivo

REQUEST_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    ),
    "Icy-MetaData": "1",
    "Accept": "*/*",
    "Connection": "keep-alive",
}

async def test_single_stream_url(session: aiohttp.ClientSession, url: str) -> Dict[str, Any]:
    """Testa se uma URL de rádio conecta e envia blocos de áudio contínuos."""
    start_time = time.time()
    result = {
        "url": url,
        "is_working": False,
        "status_code": None,
        "content_type": None,
        "icy_name": None,
        "icy_br": None,
        "bytes_received": 0,
        "elapsed_ms": 0,
        "error_message": None,
    }

    try:
        timeout = aiohttp.ClientTimeout(
            sock_connect=CONNECT_TIMEOUT_SEC,
            sock_read=STREAM_READ_TIMEOUT_SEC,
            total=CONNECT_TIMEOUT_SEC + STREAM_READ_TIMEOUT_SEC + 2.0
        )
        
        async with session.get(url, headers=REQUEST_HEADERS, timeout=timeout, allow_redirects=True) as response:
            result["status_code"] = response.status
            result["content_type"] = response.headers.get("Content-Type", "")
            result["icy_name"] = response.headers.get("icy-name")
            result["icy_br"] = response.headers.get("icy-br")

            if response.status not in (200, 206):
                result["error_message"] = f"HTTP Status {response.status}"
                result["elapsed_ms"] = int((time.time() - start_time) * 1000)
                return result

            # Lê blocos contínuos de áudio até atingir a cota mínima
            bytes_count = 0
            while bytes_count < MIN_BYTES_TO_VALIDATE:
                chunk = await response.content.read(16 * 1024)
                if not chunk:
                    break
                bytes_count += len(chunk)

            result["bytes_received"] = bytes_count
            result["elapsed_ms"] = int((time.time() - start_time) * 1000)

            if bytes_count >= MIN_BYTES_TO_VALIDATE:
                result["is_working"] = True
            elif bytes_count > 0 and ("m3u8" in url or "application/vnd.apple.mpegurl" in result["content_type"]):
                # Manifestos HLS são pequenos e válidos
                result["is_working"] = True
            else:
                result["error_message"] = f"Stream parou prematuramente ({bytes_count} bytes recebidos)"

    except asyncio.TimeoutError:
        result["error_message"] = "Timeout de conexão ou leitura de áudio"
        result["elapsed_ms"] = int((time.time() - start_time) * 1000)
    except aiohttp.ClientConnectorError as e:
        result["error_message"] = f"Erro de conexão (Host inalcançável/SSL): {str(e)}"
        result["elapsed_ms"] = int((time.time() - start_time) * 1000)
    except Exception as e:
        result["error_message"] = f"Exceção: {type(e).__name__} - {str(e)}"
        result["elapsed_ms"] = int((time.time() - start_time) * 1000)

    return result

async def validate_station(
    semaphore: asyncio.Semaphore,
    session: aiohttp.ClientSession,
    station: Dict[str, Any]
) -> Dict[str, Any]:
    """Testa todas as fontes (primária e alternativas) de uma estação de rádio."""
    async with semaphore:
        all_urls = station.get("all_stream_urls", [])
        tested_urls_results = []
        working_urls = []

        for url in all_urls:
            url_res = await test_single_stream_url(session, url)
            tested_urls_results.append(url_res)
            if url_res["is_working"]:
                working_urls.append(url)

        is_station_operable = len(working_urls) > 0
        primary_working = tested_urls_results[0]["is_working"] if tested_urls_results else False

        return {
            "id": station.get("id"),
            "name": station.get("name"),
            "state": station.get("state"),
            "city": station.get("city"),
            "is_operable": is_station_operable,
            "primary_source_working": primary_working,
            "working_urls_count": len(working_urls),
            "total_urls_count": len(all_urls),
            "recommended_order": working_urls + [u["url"] for u in tested_urls_results if not u["is_working"]],
            "sources_detail": tested_urls_results
        }

async def main():
    if not os.path.exists(INPUT_JSON_PATH):
        print(f"[ERRO] Arquivo de entrada não encontrado: {INPUT_JSON_PATH}")
        sys.exit(1)

    print(f"Carregando fontes de: {INPUT_JSON_PATH}")
    with open(INPUT_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    stations = data.get("stations", [])
    total_stations = len(stations)
    print(f"Iniciando validação assíncrona de {total_stations} emissoras...")

    semaphore = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    connector = aiohttp.TCPConnector(limit=MAX_CONCURRENT_TASKS, ssl=False)

    start_total_time = time.time()
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = [validate_station(semaphore, session, st) for st in stations]
        results = await asyncio.gather(*tasks)

    elapsed_total = time.time() - start_total_time

    # Compila estatísticas
    operable_stations = [r for r in results if r["is_operable"]]
    fully_offline_stations = [r for r in results if not r["is_operable"]]
    stations_needing_fallback = [r for r in results if r["is_operable"] and not r["primary_source_working"]]

    summary = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "validation_duration_seconds": round(elapsed_total, 2),
        "total_stations_tested": total_stations,
        "operable_stations_count": len(operable_stations),
        "operable_percentage": f"{(len(operable_stations) / total_stations * 100):.1f}%",
        "fully_offline_stations_count": len(fully_offline_stations),
        "stations_rescued_by_alternatives": len(stations_needing_fallback),
        "stations": results
    }

    with open(OUTPUT_REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print("\n" + "=" * 60)
    print("RELATÓRIO DE VALIDAÇÃO DE STREAMS CONCLUÍDO")
    print("=" * 60)
    print(f"Tempo total de execução: {elapsed_total:.2f}s")
    print(f"Total de rádios testadas: {total_stations}")
    print(f"Rádios 100% funcionais (com ao menos 1 URL ativa): {len(operable_stations)} ({summary['operable_percentage']})")
    print(f"Rádios salvas por URLs alternativas (primária falhou): {len(stations_needing_fallback)}")
    print(f"Rádios totalmente fora do ar (falha remota total): {len(fully_offline_stations)}")
    print(f"\nRelatório detalhado salvo em: {OUTPUT_REPORT_PATH}")

    if fully_offline_stations:
        print("\nExemplo de emissoras sem nenhuma fonte ativa (requer novas URLs):")
        for st in fully_offline_stations[:5]:
            print(f" - [{st['state']}] {st['name']} ({st['sources_detail'][0].get('error_message')})")

if __name__ == "__main__":
    asyncio.run(main())