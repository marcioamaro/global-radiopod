import urllib.request
import re

url = 'https://tudoradio.com/dials/cidade/178-araras'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req, timeout=10) as resp:
    html = resp.read().decode('utf-8', errors='ignore')
    # search for /ouvir/ or /player/ or radio names
    matches = re.findall(r'href=["\']([^"\']*(?:ouvir|player|dials)[^"\']*)["\']', html)
    print("Matches:", set(matches[:20]))
    # look for Clube, Fraternidade, Gospel, Araras
    for line in html.split('\n'):
        if any(w in line.lower() for w in ['clube ararense', 'fraternidade', 'araras fm', 'gospel fm']):
            print("Line:", line.strip()[:120])
