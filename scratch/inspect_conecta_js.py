import urllib.request

url = 'https://www.fmconecta.com.br/js/player_radio.js'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req, timeout=10) as resp:
    js = resp.read().decode('utf-8', errors='ignore')
    print("JS player content:")
    print(js[:1500])
