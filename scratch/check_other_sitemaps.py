import urllib.request

for num in [2, 3, 4, 5]:
    url = f"https://www.radios.com.br/sitemap_radio{num}.xml"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            print(f"sitemap_radio{num}.xml exists! Status:", resp.status)
    except Exception as e:
        print(f"sitemap_radio{num}.xml:", e)
