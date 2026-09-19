import urllib.request
import re

req = urllib.request.Request("https://portalradiorondonia.com", headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req, timeout=10) as resp:
    html = resp.read().decode("utf-8", errors="ignore")
    # find iframe or audio
    iframes = re.findall(r'<iframe[^>]*src=[\'\"]([^\'\"]+)[\'\"]', html)
    print("Iframes:", iframes)
    audios = re.findall(r'<audio[^>]*src=[\'\"]([^\'\"]+)[\'\"]', html)
    print("Audios:", audios)
    links = [m for m in re.findall(r'href=[\'\"]([^\'\"]+)[\'\"]', html) if "aovivo" in m or "ao-vivo" in m or "ouvir" in m]
    print("Live links:", links)
