import urllib.request
import json
import re

MIRRORS = [
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json"
]

BRAZIL_STATE_NAMES = [
    "Acre", "Alagoas", "Amapa", "Amapá", "Amazonas", "Bahia", "Ceara", "Ceará",
    "Distrito Federal", "Espirito Santo", "Espírito Santo", "Goias", "Goiás",
    "Maranhao", "Maranhão", "Mato Grosso", "Mato Grosso do Sul", "Minas Gerais",
    "Para", "Pará", "Paraiba", "Paraíba", "Parana", "Paraná", "Pernambuco",
    "Piaui", "Piauí", "Rio de Janeiro", "Rio Grande do Norte", "Rio Grande do Sul",
    "Rondonia", "Rondônia", "Roraima", "Santa Catarina", "Sao Paulo", "São Paulo",
    "Sergipe", "Tocantins"
]

def check():
    url = f"{MIRRORS[0]}/stations/search?limit=10000&hidebroken=true"
    req = urllib.request.Request(url, headers={"User-Agent": "GlobalRadioPod/2.0"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print("Total stations in query:", len(data))
            
            br_found = []
            for s in data:
                cc = (s.get("countrycode") or "").upper()
                cname = (s.get("country") or "").lower()
                st = s.get("state") or ""
                url = s.get("url_resolved") or s.get("url") or ""
                
                # Check if clearly Brazilian even if cc != BR
                is_br = False
                if cc == "BR" or "brazil" in cname or "brasil" in cname:
                    is_br = True
                elif any(st_name.lower() == st.strip().lower() for st_name in BRAZIL_STATE_NAMES):
                    is_br = True
                elif ".com.br" in url.lower() or ".radio.br" in url.lower() or ".br/" in url.lower() or ".br:" in url.lower():
                    is_br = True
                    
                if is_br:
                    br_found.append(s)
                    
            print("Total Brazilian stations identified in this slice:", len(br_found))
    except Exception as e:
        print("Error:", e)

if __name__ == "__main__":
    check()
