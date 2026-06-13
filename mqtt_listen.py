#!/usr/bin/env python3
"""
Łączy się z MQTT EcoFlow, subskrybuje topic quota Streama i wypisuje
przychodzące wiadomości przez ~40 s, żeby odkryć nazwy pól telemetrii.

    export ECOFLOW_ACCESS_KEY=...
    export ECOFLOW_SECRET_KEY=...
    export ECOFLOW_SN=<twój_numer_seryjny>
    python3 mqtt_listen.py
"""
import hashlib, hmac, json, os, random, ssl, time, urllib.request
import paho.mqtt.client as mqtt

AK = os.environ["ECOFLOW_ACCESS_KEY"]
SK = os.environ["ECOFLOW_SECRET_KEY"]
SN = os.environ["ECOFLOW_SN"]
BASE = os.environ.get("ECOFLOW_BASE_URL", "https://api.ecoflow.com").rstrip("/")
LISTEN_SECONDS = int(os.environ.get("LISTEN_SECONDS", "40"))


def get_cert():
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
        return json.loads(r.read().decode())["data"]


PV_HINTS = ("pv", "inv", "watt", "power", "energy", "gen", "sun", "solar", "mppt", "bat", "soc")
seen_keys = {}


def flatten(d, prefix=""):
    out = {}
    for k, v in d.items():
        key = f"{prefix}.{k}" if prefix else k
        if isinstance(v, dict):
            out.update(flatten(v, key))
        else:
            out[key] = v
    return out


def on_connect(client, userdata, flags, rc, props=None):
    acct = userdata["account"]
    topics = [
        f"/open/{acct}/{SN}/quota",
        f"/open/{acct}/{SN}/status",
    ]
    for t in topics:
        client.subscribe(t)
        print(f"  subskrybuję: {t}")


def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
    except Exception:
        print(f"[{msg.topic}] (nie-JSON) {msg.payload[:200]!r}")
        return
    flat = flatten(payload)
    for k, v in flat.items():
        seen_keys[k] = v
    print(f"\n[{msg.topic}] {json.dumps(payload, ensure_ascii=False)[:600]}")


def main():
    cert = get_cert()
    acct = cert["certificateAccount"]
    client = mqtt.Client(
        client_id=f"open-{acct}-{random.randint(1000,9999)}",
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        protocol=mqtt.MQTTv311,
    )
    client.username_pw_set(acct, cert["certificatePassword"])
    client.tls_set(cert_reqs=ssl.CERT_REQUIRED, tls_version=ssl.PROTOCOL_TLS_CLIENT)
    client.user_data_set({"account": acct})
    client.on_connect = on_connect
    client.on_message = on_message

    print(f"Łączę z {cert['url']}:{cert['port']} (TLS)…")
    client.connect(cert["url"], int(cert["port"]), keepalive=30)
    client.loop_start()
    time.sleep(LISTEN_SECONDS)
    client.loop_stop()
    client.disconnect()

    print("\n\n========== WSZYSTKIE POLA (spłaszczone) ==========")
    for k in sorted(seen_keys):
        mark = "  <<<" if any(h in k.lower() for h in PV_HINTS) else ""
        print(f"  {k} = {seen_keys[k]}{mark}")
    if not seen_keys:
        print("  (nic nie przyszło — Stream może wysyłać rzadko; zwiększ LISTEN_SECONDS)")


if __name__ == "__main__":
    main()
