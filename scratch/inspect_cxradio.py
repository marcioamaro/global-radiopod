import urllib.request
import re

url = 'https://www.cxradio.com.br/radios/brasil/sp/araras'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req, timeout=10) as resp:
    html = resp.read().decode('utf-8', errors='ignore')
    # find all a tags
    a_tags = re.findall(r'<a[^>]+href=["\']([^"\']+)["\'][^>]*>(.*?)</a>', html, re.DOTALL)
    for href, text in a_tags:
        clean_text = re.sub(r'<[^>]+>', '', text).strip()
        if clean_text and ('radio' in href.lower() or 'fm' in clean_text.lower() or 'araras' in clean_text.lower()):
            print(f"{clean_text} -> {href}")
