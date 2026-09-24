"""Atualiza capas de rádios brasileiras apenas a partir de metadados da página oficial.

O script não baixa imagens para o repositório. Ele grava a URL declarada pela própria
emissora (Open Graph/Twitter Card), registra a página que a declarou e nunca aplica
a mesma capa nova a duas estações distintas.
"""

from __future__ import annotations

import argparse
import json
import re
import time
from collections import Counter
from pathlib import Path
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app" / "src" / "main" / "assets" / "radio_catalog.json"
REPORT = ROOT / "reports" / "official-brazil-radio-artwork.json"
USER_AGENT = "MediaPodOfficialArtworkAudit/0.3 (+https://github.com/marcioamaro/global-radiopod)"
GENERIC_HOSTS = {"tudoradio.com", "radios.com.br", "mytuner.mobi", "streema.com", "duckduckgo.com"}
AGGREGATOR_HOSTS = GENERIC_HOSTS | {"radio-ao-vivo.com", "vip-radios.fm", "online-radio.eu"}


def host(url: str) -> str:
    return urlparse(url).netloc.lower().removeprefix("www.")


def is_generic(url: str) -> bool:
    value = host(url)
    return any(value == item or value.endswith("." + item) for item in GENERIC_HOSTS)


def is_aggregator(url: str) -> bool:
    value = host(url)
    return any(value == item or value.endswith("." + item) for item in AGGREGATOR_HOSTS)


def page_image(url: str, timeout: float) -> tuple[str, str] | None:
    request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,application/xhtml+xml"})
    with urlopen(request, timeout=timeout) as response:
        final_url = response.geturl()
        html = response.read(800_000).decode("utf-8", "replace")
    patterns = (
        r'<meta[^>]+(?:property|name)=["\'](?:og:image|twitter:image)["\'][^>]+content=["\']([^"\']+)',
        r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+(?:property|name)=["\'](?:og:image|twitter:image)["\']',
    )
    for pattern in patterns:
        match = re.search(pattern, html, flags=re.IGNORECASE)
        if match:
            image = urljoin(final_url, match.group(1).replace("&amp;", "&"))
            if urlparse(image).scheme == "https":
                return final_url, image
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=120)
    parser.add_argument("--timeout", type=float, default=5)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()

    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    brazil = [station for station in data["stations"] if station.get("country_code") == "BR"]
    current = Counter(station.get("favicon_url", "") for station in brazil)
    candidates = sorted(
        (
            station for station in brazil
            if station.get("homepage")
            and not is_aggregator(station["homepage"])
            and (not station.get("favicon_url") or is_generic(station["favicon_url"]) or current[station["favicon_url"]] > 1)
        ),
        key=lambda station: -station.get("votes", 0),
    )[: args.limit]

    proposed: list[dict[str, str]] = []
    failures: list[dict[str, str]] = []
    proposed_urls: set[str] = set()
    for station in candidates:
        try:
            found = page_image(station["homepage"], args.timeout)
            if not found:
                failures.append({"id": station["id"], "reason": "no_declared_image", "homepage": station["homepage"]})
                continue
            source, image = found
            if image in proposed_urls:
                failures.append({"id": station["id"], "reason": "duplicate_candidate", "homepage": source, "image": image})
                continue
            proposed_urls.add(image)
            proposed.append({"id": station["id"], "name": station["name"], "homepage": source, "image": image})
        except Exception as error:  # Auditoria deve continuar quando uma emissora falhar.
            failures.append({"id": station["id"], "reason": type(error).__name__, "homepage": station["homepage"]})
        time.sleep(0.2)

    updates = {item["id"]: item["image"] for item in proposed}
    removed = []
    if args.apply:
        for station in brazil:
            image = station.get("favicon_url", "")
            if station["id"] in updates:
                station["favicon_url"] = updates[station["id"]]
            elif image and is_generic(image) and current[image] > 1:
                station["favicon_url"] = ""
                removed.append(station["id"])
        CATALOG.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    REPORT.parent.mkdir(exist_ok=True)
    REPORT.write_text(json.dumps({
        "scope": "Brazil official artwork from station-declared metadata",
        "examined": len(candidates),
        "official_updates": proposed,
        "rejected": failures,
        "removed_repeated_generic_artwork": removed,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"examined={len(candidates)} official_updates={len(proposed)} removed_generic={len(removed)}")


if __name__ == "__main__":
    main()
