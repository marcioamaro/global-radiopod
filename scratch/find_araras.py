import requests
import re
import urllib3
urllib3.disable_warnings()

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
}

stations = [
    ("Rádio Araras FM", "https://ararasfm.com.br/"),
    ("Rádio Clube Ararense", "https://radioclube.com.br/"),
    ("Rádio Clube Ararense RCA1", "https://rca1.com.br/estudio-ao-vivo/"),
    ("Rádio Cidade das Árvores", "https://radiocidadedasarvores.com.br/"),
    ("Rural FM", "https://rural87.com.br/"),
    ("Rádio Interativa Online", "https://radiointerativaonline.com.br/"),
    ("Fraternidade FM", "https://fraternidadefm.com.br/")
]

for name, u in stations:
    print(f"\n--- {name} ({u}) ---")
    try:
        r = requests.get(u, headers=headers, timeout=10, verify=False)
        print(f"Status: {r.status_code}")
        html = r.text

        # Audio tag src
        audio_srcs = re.findall(r'<audio[^>]+src=["\'](.*?)["\']', html, re.IGNORECASE)
        source_srcs = re.findall(r'<source[^>]+src=["\'](.*?)["\']', html, re.IGNORECASE)
        iframes = re.findall(r'<iframe[^>]+src=["\'](.*?)["\']', html, re.IGNORECASE)
        js_files = re.findall(r'<script[^>]+src=["\'](.*?)["\']', html, re.IGNORECASE)
        # generic stream urls
        stream_urls = re.findall(r'https?://[a-zA-Z0-9\.\-_/:]+(?:/stream|/live|/live\.mp3|/stream\.mp3|/audio|:8\d{3}|:9\d{3}|:7\d{3}|:1\d{4})[^\s"\'<>]*', html, re.IGNORECASE)
        mp3_urls = re.findall(r'https?://[a-zA-Z0-9\.\-_/:]+\.(?:mp3|aac|m3u8)[^\s"\'<>]*', html, re.IGNORECASE)

        candidates = list(dict.fromkeys(audio_srcs + source_srcs + stream_urls + mp3_urls))
        for c in candidates:
            print(f"  Candidate: {c}")
        if not candidates:
            for ifr in iframes:
                print(f"  Iframe: {ifr}")
                try:
                    r_ifr = requests.get(ifr, headers=headers, timeout=8, verify=False)
                    sub_streams = re.findall(r'https?://[a-zA-Z0-9\.\-_/:]+(?:\.mp3|\.aac|/stream|:8\d{3}|:9\d{3}|:7\d{3})[^\s"\'<>]*', r_ifr.text)
                    for ss in list(dict.fromkeys(sub_streams)):
                        print(f"    Sub-stream: {ss}")
                except Exception as sub_e:
                    print(f"    Iframe error: {sub_e}")
        # Links with player/ouvir/aovivo
        player_links = re.findall(r'href=["\'](https?://[^\s"\'<>]+(?:player|aovivo|ao-vivo|ouvir|live|radio)[^\s"\'<>]*)["\']', html, re.IGNORECASE)
        for pl in list(dict.fromkeys(player_links))[:4]:
            print(f"  Player link: {pl}")
    except Exception as e:
        print(f"Error: {e}")
