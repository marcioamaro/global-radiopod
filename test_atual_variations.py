import urllib.request
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Icy-MetaData': '1'
}

candidates = [
    "https://ice.fabricahost.com.br/radioatua1941",
    "http://ice.fabricahost.com.br/radioatua1941",
    "https://ice.fabricahost.com.br/radioatual941",
    "http://ice.fabricahost.com.br/radioatual941",
    "https://ice.fabricahost.com.br/radioatual",
    "http://ice.fabricahost.com.br/radioatual",
    "https://ice.fabricahost.com.br/atual941",
    "http://ice.fabricahost.com.br/atual941",
    "https://ice.fabricahost.com.br/atualfm",
    "http://ice.fabricahost.com.br/atualfm",
    "https://f100.fabricahost.com.br/radioatua1941",
    "https://f100.fabricahost.com.br/radioatual941",
    "https://f100.fabricahost.com.br/radioatual",
    "https://f100.fabricahost.com.br/atual941",
    "http://ice.fabricahost.com.br:8000/radioatua1941",
    "http://ice.fabricahost.com.br:8000/radioatual941",
    "http://ice.fabricahost.com.br:8000/stream",
    "https://ice.fabricahost.com.br/live",
    "https://ice.fabricahost.com.br/radioatua1941.aac",
    "https://ice.fabricahost.com.br/radioatua1941.mp3",
    "https://ice.fabricahost.com.br/radioatual941.aac",
    "https://ice.fabricahost.com.br/radioatual941.mp3"
]

print("Testando todas as variações de URL da Rádio Atual no Fabricahost...")
for c in candidates:
    try:
        req = urllib.request.Request(c, headers=headers)
        with urllib.request.urlopen(req, timeout=4, context=ctx) as resp:
            ct = resp.headers.get("Content-Type")
            server = resp.headers.get("Server")
            icy = resp.headers.get("icy-name")
            print(f"[SUCESSO] {c} -> HTTP {resp.status} | Content-Type: {ct} | Icy: {icy} | Server: {server}")
    except urllib.error.HTTPError as e:
        # print(f"[HTTP {e.code}] {c}")
        pass
    except Exception as e:
        # print(f"[FAIL] {c} -> {e}")
        pass
