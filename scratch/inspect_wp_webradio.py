import urllib.request
import json
import re

def check_wp():
    # 1. Check REST API types
    url = "https://www.webradio.com.br/wp-json/wp/v2/types"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print("Post types:", list(data.keys()))
    except Exception as e:
        print("Types error:", e)

    # 2. Check HTML for radio streams / player configuration
    url_home = "https://www.webradio.com.br"
    req_home = urllib.request.Request(url_home, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req_home, timeout=10) as resp:
        html = resp.read().decode("utf-8", errors="ignore")
        # Look for mp3, m3u8, stream, shoutcast, icecast
        streams = re.findall(r'(https?://[^\s\'\"\<\>]+\.(?:mp3|aac|m3u8)[^\s\'\"\<\>]*)', html)
        print("Direct audio streams found on homepage:", streams[:10])
        # Look for data-stream, data-src, stream_url
        data_streams = re.findall(r'data-[a-z\-]*stream[a-z\-]*=[\'\"]([^\'\"]+)[\'\"]', html)
        print("Data stream attributes:", data_streams[:10])
        # Look for script player configs
        player_matches = re.findall(r'(https?://[a-zA-Z0-9\.\_\:\-\/]+(?:stream|live|cast|:8[0-9]{3}|:9[0-9]{3})[^\s\'\"\<\>]*)', html)
        print("Other candidate stream URLs:", set(player_matches[:15]))

check_wp()
