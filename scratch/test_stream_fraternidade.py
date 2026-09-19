import urllib.request
import ssl

url = 'https://s02.transmissaodigital.com:7130/stream'
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request(url, headers={'User-Agent': 'VLC/3.0.18 LibVLC/3.0.18', 'Icy-MetaData': '1'})
try:
    with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
        print("Status:", resp.status)
        print("Headers:", dict(resp.headers))
        chunk = resp.read(1024)
        print(f"Read {len(chunk)} audio bytes successfully!")
except Exception as e:
    print("Error:", e)
