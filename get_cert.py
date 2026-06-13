#!/usr/bin/env python3
"""Pobiera dane dostępowe MQTT z EcoFlow IoT Open (GET /iot-open/sign/certification)."""
import hashlib, hmac, json, os, random, time, urllib.request

AK = os.environ["ECOFLOW_ACCESS_KEY"]
SK = os.environ["ECOFLOW_SECRET_KEY"]
BASE = os.environ.get("ECOFLOW_BASE_URL", "https://api.ecoflow.com").rstrip("/")

nonce = str(random.randint(100000, 999999))
ts = str(int(time.time() * 1000))
sign_str = f"accessKey={AK}&nonce={nonce}&timestamp={ts}"
sig = hmac.new(SK.encode(), sign_str.encode(), hashlib.sha256).hexdigest()

req = urllib.request.Request(
    BASE + "/iot-open/sign/certification",
    headers={"accessKey": AK, "nonce": nonce, "timestamp": ts, "sign": sig},
    method="GET",
)
with urllib.request.urlopen(req, timeout=20) as r:
    out = json.loads(r.read().decode())

# nie drukuj pełnego hasła do logów
data = out.get("data") or {}
if "certificatePassword" in data:
    p = data["certificatePassword"]
    data["certificatePassword"] = p[:3] + "***" + p[-2:] if len(p) > 6 else "***"
print(json.dumps(out, indent=2, ensure_ascii=False))
