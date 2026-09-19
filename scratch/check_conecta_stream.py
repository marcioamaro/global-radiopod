import urllib.request
import json
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

try:
    url = 'https://api.instant.audio/data/streams/8/conecta-104-1'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
        print("API resp:", resp.read().decode('utf-8'))
except Exception as e:
    print("API Error:", e)

try:
    url = 'https://paineldj5.com.br:20135/stream'
    req = urllib.request.Request(url, headers={'User-Agent': 'VLC/3.0.18'})
    with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
        print("Stream status:", resp.status, resp.headers.get('content-type'))
        chunk = resp.read(1024)
        print("Stream chunk len:", len(chunk))
except Exception as e:
    print("Stream Error:", e)
