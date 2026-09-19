import urllib.request
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

for u in ["https://casthttps.suaradionanet.net/10841/stream", "https://hts02.brascast.com:9292/stream"]:
    try:
        req = urllib.request.Request(u, headers={'User-Agent': 'VLC/3.0.18 LibVLC/3.0.18', 'Icy-MetaData': '1'})
        with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
            print("SUCCESS:", u)
            print("  status:", resp.status)
            print("  headers:", dict(resp.headers))
            chunk = resp.read(512)
            print("  chunk len:", len(chunk))
    except Exception as e:
        print("FAIL:", u, e)
