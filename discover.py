#!/usr/bin/env python3
"""
EcoFlow IoT Open API - schema discovery tool.

Pobiera listę urządzeń i pełną telemetrię (quota/all) dla Streama,
żeby odkryć realne nazwy pól (moc PV, per-panel, energia dzienna itd.).

Klucze czytane ze zmiennych środowiskowych - NIC nie zapisujemy na dysk:

    export ECOFLOW_ACCESS_KEY="twoj_access_key"
    export ECOFLOW_SECRET_KEY="twoj_secret_key"
    python3 discover.py

Opcjonalnie:
    export ECOFLOW_BASE_URL="https://api-e.ecoflow.com"   # EU (domyślne)
    # USA/global:  https://api.ecoflow.com
    export ECOFLOW_SN="numer_seryjny"   # pomija listę, pyta wprost o to urządzenie
"""

import hashlib
import hmac
import json
import os
import random
import sys
import time
import urllib.parse
import urllib.request

BASE_URL = os.environ.get("ECOFLOW_BASE_URL", "https://api-e.ecoflow.com").rstrip("/")
ACCESS_KEY = os.environ.get("ECOFLOW_ACCESS_KEY")
SECRET_KEY = os.environ.get("ECOFLOW_SECRET_KEY")


def flatten(params, prefix=""):
    """Spłaszcza zagnieżdżone parametry wg reguł EcoFlow (a.b, a[0], a[0].b)."""
    items = {}
    if isinstance(params, dict):
        for k, v in params.items():
            key = f"{prefix}.{k}" if prefix else k
            items.update(flatten(v, key))
    elif isinstance(params, list):
        for i, v in enumerate(params):
            items.update(flatten(v, f"{prefix}[{i}]"))
    else:
        items[prefix] = params
    return items


def sign(params):
    """Buduje nagłówki z podpisem HMAC-SHA256 wg specyfikacji EcoFlow IoT Open."""
    nonce = str(random.randint(100000, 999999))
    timestamp = str(int(time.time() * 1000))

    # USTALONE EMPIRYCZNIE (signtest.py): dla GET-ów EcoFlow liczy podpis WYŁĄCZNIE
    # z accessKey&nonce&timestamp. Parametry zapytania (np. sn) idą tylko do query
    # stringa i NIE wchodzą do podpisu - mimo że dokumentacja twierdzi inaczej.
    parts = [f"accessKey={ACCESS_KEY}", f"nonce={nonce}", f"timestamp={timestamp}"]
    sign_str = "&".join(parts)
    _ = params  # parametry świadomie pomijane w podpisie dla GET

    signature = hmac.new(
        SECRET_KEY.encode("utf-8"),
        sign_str.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()

    return {
        "accessKey": ACCESS_KEY,
        "nonce": nonce,
        "timestamp": timestamp,
        "sign": signature,
        "Content-Type": "application/json;charset=UTF-8",
    }


def get(path, params=None):
    """GET do EcoFlow IoT Open API."""
    params = params or {}
    url = BASE_URL + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers=sign(params), method="GET")
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        return {"_http_error": e.code, "_body": body}
    except Exception as e:  # noqa: BLE001
        return {"_error": str(e)}


def highlight_keys(quota):
    """Wyłuskuje pola które wyglądają na związane z produkcją PV / mocą / energią."""
    if not isinstance(quota, dict):
        return
    needles = ("pv", "inv", "watt", "power", "energy", "gen", "sun", "solar", "mppt")
    hits = []
    for key, val in quota.items():
        low = key.lower()
        if any(n in low for n in needles):
            hits.append((key, val))
    if hits:
        print("\n=== KANDYDACI (pola z pv/inv/watt/power/energy/...) ===")
        for k, v in sorted(hits):
            print(f"  {k} = {v}")
    else:
        print("\n(nie znaleziono oczywistych pól PV - przejrzyj pełny JSON powyżej)")


def main():
    if not ACCESS_KEY or not SECRET_KEY:
        print("BŁĄD: ustaw ECOFLOW_ACCESS_KEY i ECOFLOW_SECRET_KEY w env.", file=sys.stderr)
        sys.exit(1)

    print(f"Base URL: {BASE_URL}\n")

    sn_env = os.environ.get("ECOFLOW_SN")
    serials = []

    if sn_env:
        serials = [sn_env]
        print(f"Używam SN z env: {sn_env}")
    else:
        print("=== /iot-open/sign/device/list ===")
        dev = get("/iot-open/sign/device/list")
        print(json.dumps(dev, indent=2, ensure_ascii=False))
        data = dev.get("data") if isinstance(dev, dict) else None
        if isinstance(data, list):
            for d in data:
                sn = d.get("sn") or d.get("deviceSn")
                if sn:
                    serials.append(sn)
        if not serials:
            print("\nNie wykryto żadnego SN. Jeśli widzisz błąd region/HTTP powyżej,")
            print("spróbuj: export ECOFLOW_BASE_URL=https://api.ecoflow.com")
            return

    for sn in serials:
        print(f"\n\n=== /iot-open/sign/device/quota/all  (sn={sn}) ===")
        q = get("/iot-open/sign/device/quota/all", {"sn": sn})
        print(json.dumps(q, indent=2, ensure_ascii=False))
        if isinstance(q, dict):
            highlight_keys(q.get("data", q))


if __name__ == "__main__":
    main()
