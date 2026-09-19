import json
import subprocess

def escape_kt_string(val):
    if val is None:
        return ""
    val_str = str(val)
    val_str = val_str.replace("\\", "\\\\")
    val_str = val_str.replace('"', '\\"')
    val_str = val_str.replace("$", "\\$")
    val_str = val_str.replace("\n", " ").replace("\r", " ")
    return val_str

def main():
    # 1. Obter cabecalho CuratedData do git show HEAD~1
    git_out = subprocess.check_output(
        ["git", "show", "HEAD~1:app/src/main/java/com/example/data/repository/CuratedStations.kt"],
        encoding="utf-8"
    )
    marker = "private fun getStationsChunk_1"
    idx = git_out.find(marker)
    if idx == -1:
        raise ValueError("Marker private fun getStationsChunk_1 not found in HEAD~1")

    curated_data_header = git_out[:idx].rstrip()

    # 2. Carregar todas as 7548 radios
    with open("all_radio_sources.json", "r", encoding="utf-8") as f:
        data = json.load(f)
    stations = data["stations"] if isinstance(data, dict) and "stations" in data else data

    print(f"Total de estacoes carregadas de all_radio_sources.json: {len(stations)}")

    # 3. Particionar em chunks de 150 estacoes
    chunk_size = 150
    chunks = [stations[i:i + chunk_size] for i in range(0, len(stations), chunk_size)]
    num_chunks = len(chunks)

    # 4. Construir o codigo Kotlin
    curated_data_header += "\n\n    val CURATED_GLOBAL_STATIONS: List<RadioStation> by lazy {\n        CuratedStations.stations\n    }\n}\n\n"

    kt_lines = [curated_data_header]

    # Dividir os chunks em partes (maximo 9 chunks por parte para evitar ClassTooLargeException)
    chunks_per_part = 9
    parts = [chunks[i:i + chunks_per_part] for i in range(0, len(chunks), chunks_per_part)]
    num_parts = len(parts)

    for part_idx, part_chunks in enumerate(parts, 1):
        kt_lines.append(f"internal object CuratedStationsPart{part_idx} {{")
        chunk_start_num = (part_idx - 1) * chunks_per_part + 1
        chunk_func_names = []
        for c_offset, chunk in enumerate(part_chunks):
            chunk_num = chunk_start_num + c_offset
            func_name = f"getChunk_{chunk_num}"
            chunk_func_names.append(func_name)
            kt_lines.append(f"    fun {func_name}(): List<RadioStation> = listOf(")
            for s in chunk:
                s_id = escape_kt_string(s.get("id", ""))
                s_name = escape_kt_string(s.get("name", ""))
                s_stream = escape_kt_string(s.get("primary_stream_url") or s.get("streamUrl") or "")
                alts = s.get("alternative_stream_urls") or []
                alt_urls = [escape_kt_string(u) for u in alts if u and u != s_stream]
                alt_urls_kt = ", ".join([f'"{u}"' for u in alt_urls])

                s_fav = escape_kt_string(s.get("favicon_url") or s.get("favicon") or "")
                s_home = escape_kt_string(s.get("homepage", ""))

                tags_val = s.get("tags", "")
                if isinstance(tags_val, list):
                    s_tags = escape_kt_string(", ".join(tags_val))
                else:
                    s_tags = escape_kt_string(tags_val)

                s_country = escape_kt_string(s.get("country", "Brasil"))
                s_code = escape_kt_string(s.get("country_code") or s.get("countryCode") or "BR")
                s_state = escape_kt_string(s.get("state", ""))
                s_city = escape_kt_string(s.get("city", ""))
                s_codec = escape_kt_string(s.get("codec", "MP3"))
                s_bitrate = int(s.get("bitrate_kbps") or s.get("bitrate") or 128)
                s_votes = int(s.get("votes") or 500)

                kt_lines.append("        RadioStation(")
                kt_lines.append(f'            id = "{s_id}",')
                kt_lines.append(f'            name = "{s_name}",')
                kt_lines.append(f'            streamUrl = "{s_stream}",')
                if alt_urls:
                    kt_lines.append(f'            alternativeStreamUrls = listOf({alt_urls_kt}),')
                if s_fav:
                    kt_lines.append(f'            favicon = "{s_fav}",')
                if s_home:
                    kt_lines.append(f'            homepage = "{s_home}",')
                if s_tags:
                    kt_lines.append(f'            tags = "{s_tags}",')
                kt_lines.append(f'            country = "{s_country}",')
                kt_lines.append(f'            countryCode = "{s_code}",')
                kt_lines.append(f'            state = "{s_state}",')
                kt_lines.append(f'            city = "{s_city}",')
                kt_lines.append(f'            codec = "{s_codec}",')
                kt_lines.append(f'            bitrate = {s_bitrate},')
                kt_lines.append(f'            votes = {s_votes}')
                kt_lines.append("        ),")
            kt_lines.append("    )\n")

        kt_lines.append(f"    fun getStations(): List<RadioStation> = buildList {{")
        for fn in chunk_func_names:
            kt_lines.append(f"        addAll({fn}())")
        kt_lines.append("    }")
        kt_lines.append("}\n")

    kt_lines.append("object CuratedStations {")
    kt_lines.append("")
    kt_lines.append("    val stations: List<RadioStation> by lazy {")
    kt_lines.append("        buildList {")
    for part_idx in range(1, num_parts + 1):
        kt_lines.append(f"            addAll(CuratedStationsPart{part_idx}.getStations())")
    kt_lines.append("        }")
    kt_lines.append("    }")
    kt_lines.append("")
    kt_lines.append("    val stationsCount: Int get() = stations.size")
    kt_lines.append("}")
    kt_lines.append("")

    target_path = "app/src/main/java/com/example/data/repository/CuratedStations.kt"
    with open(target_path, "w", encoding="utf-8") as f:
        f.write("\n".join(kt_lines))
    print(f"Sucesso! {target_path} gerado com {len(stations)} estacoes em {num_chunks} blocos!")

if __name__ == "__main__":
    main()
