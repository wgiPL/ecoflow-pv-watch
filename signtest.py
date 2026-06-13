#!/usr/bin/env python3
"""
Brute-force podpisu EcoFlow dla GET /iot-open/sign/device/quota/all?sn=...
Próbuje kilka wariantów budowania sign-stringa i mówi, który zwraca code=0.

    export ECOFLOW_ACCESS_KEY=...
    export ECOFLOW_SECRET_KEY=...
    export ECOFLOW_SN=<twój_numer_seryjny>   # numer seryjny Streama
    python3 signtest.py
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

ACCESS_KEY = os.environ["ECOFLOW_ACCESS_KEY"]
SECRET_KEY = os.environ["ECOFLOW_SECRET_KEY"]
SN = os.environ.get("ECOFLOW_SN")
PATH = "/iot-open/sign/device/quota/all"

if not SN:
    print("Ustaw ECOFLOW_SN (numer seryjny Streama).", file=sys.stderr)
    sys.exit(1)


def hmac_hex(secret, msg):
    return hmac.new(secret.encode(), msg.encode(), hashlib.sha256).hexdigest()


# Każdy wariant dostaje (access, nonce, ts, sn) i zwraca sign-string.
VARIANTS = {
    "S1 append: sn&access&nonce&ts":
        lambda a, n, t, sn: f"sn={sn}&accessKey={a}&nonce={n}&timestamp={t}",
    "S2 sort-all: access&nonce&sn&ts":
        lambda a, n, t, sn: f"accessKey={a}&nonce={n}&sn={sn}&timestamp={t}",
    "S3 bez param: access&nonce&ts":
        lambda a, n, t, sn: f"accessKey={a}&nonce={n}&timestamp={t}",
    "S4 public-first: access&nonce&ts&sn":
        lambda a, n, t, sn: f"accessKey={a}&nonce={n}&timestamp={t}&sn={sn}",
    "S5 tylko param: sn":
        lambda a, n, t, sn: f"sn={sn}",
}

BASES = [
    os.environ.get("ECOFLOW_BASE_URL", "https://api.ecoflow.com").rstrip("/"),
    "https://api-e.ecoflow.com",
    "https://api-a.ecoflow.com",
]
# unikalne, zachowując kolejność
seen = set()
BASES = [b for b in BASES if not (b in seen or seen.add(b))]


def try_call(base, name, builder):
    nonce = str(random.randint(100000, 999999))
    ts = str(int(time.time() * 1000))
    sign_str = builder(ACCESS_KEY, nonce, ts, SN)
    sig = hmac_hex(SECRET_KEY, sign_str)
    url = f"{base}{PATH}?" + urllib.parse.urlencode({"sn": SN})
    headers = {
        "accessKey": ACCESS_KEY,
        "nonce": nonce,
        "timestamp": ts,
        "sign": sig,
        "Content-Type": "application/json;charset=UTF-8",
    }
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            body = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        body = {"code": f"HTTP {e.code}", "message": e.read().decode("utf-8", "replace")[:120]}
    except Exception as e:  # noqa: BLE001
        body = {"code": "ERR", "message": str(e)[:120]}
    return body.get("code"), body.get("message"), body


def main():
    winner = None
    for base in BASES:
        print(f"\n##### BASE: {base} #####")
        for name, builder in VARIANTS.items():
            code, msg, body = try_call(base, name, builder)
            flag = "  <<<<< OK!" if code == "0" else ""
            print(f"  [{name}]  code={code}  msg={msg}{flag}")
            if code == "0" and winner is None:
                winner = (base, name, body)
        if winner:
            break

    if winner:
        base, name, body = winner
        print(f"\n\n========== ZWYCIĘZCA ==========")
        print(f"BASE   : {base}")
        print(f"WARIANT: {name}")
        print(f"\n--- pełna odpowiedź quota/all ---")
        print(json.dumps(body, indent=2, ensure_ascii=False))
    else:
        print("\n\nŻaden wariant nie przeszedł. Wklej output - pomyślimy dalej.")


if __name__ == "__main__":
    main()
