# EcoFlow IoT Open API — ustalenia (Stream Microinverter)

Notatki z reverse-engineeringu zachowania API. Aktualizować w miarę odkryć.

## Urządzenie
- Model: **Stream Microinverter** (2 wejścia PV)
- SN: w `secrets.properties` (`ECOFLOW_SN`) lub auto-wykrywany z `device/list`
- `online: 0` gdy brak produkcji (Stream zasypia w nocy / przy braku słońca)

## Endpoint / region
- Działający base URL: **`https://api.ecoflow.com`** (global)
- (do sprawdzenia, czy EU `api-e.ecoflow.com` też działa — global wystarcza)

## Podpis (KLUCZOWE — wbrew dokumentacji)
Nagłówki każdego requestu: `accessKey`, `nonce`, `timestamp`, `sign`.

`sign = HMAC-SHA256(signStr, secretKey)` → hex (lowercase).

**Dla GET-ów `signStr` zawiera TYLKO pola publiczne, alfabetycznie:**
```
accessKey=<AK>&nonce=<losowy int>&timestamp=<ms>
```
Parametry zapytania (np. `sn`) idą **wyłącznie do query stringa**, NIE do podpisu.
Ustalone empirycznie (`signtest.py`): warianty z `sn` w podpisie → `8521 signature is wrong`.
Wariant bez parametrów → `code: 0 Success`.

> Uwaga: dokumentacja EcoFlow każe wrzucać parametry do podpisu (sortowane
> alfabetycznie z flattenowaniem zagnieżdżeń). Dla naszych GET-ów to NIE działa.
> Przy POST/PUT (set quota) trzeba będzie sprawdzić osobno — może być inaczej.

## Wywołania, których używamy
- `GET /iot-open/sign/device/list` — lista urządzeń (sn, deviceName, online). Działa.
- `GET /iot-open/sign/device/quota/all?sn=<SN>` — **dla Streama ZAWSZE pusty**
  (`Success` bez `data`, nawet gdy online + produkuje). Mikroinwertery EcoFlow
  raportują telemetrię WYŁĄCZNIE przez MQTT, nie przez HTTP quota. Nie używamy.
- `GET /iot-open/sign/certification` — zwraca dane dostępowe MQTT (patrz niżej).

## MQTT — JEDYNE źródło telemetrii Streama
1. `GET /iot-open/sign/certification` → `data`:
   - `certificateAccount` (login MQTT, np. `open-99d9...`)
   - `certificatePassword` (hasło, sesyjne)
   - `url` = `mqtt-e.ecoflow.com`, `port` = `8883`, `protocol` = `mqtts` (TLS)
2. Połącz MQTT over TLS (clientId musi być unikalny!).
3. Subskrybuj: `/open/{certificateAccount}/{sn}/quota`
   (jest też `/.../status` — głównie online/offline).
4. Wiadomości JSON. **PRZYROSTOWE**: pierwsza ~pełna, potem co ~2 s tylko
   zmienione pola; pełny snapshot co ~120 s. Trzeba akumulować stan, aż uzbiera
   się `powGetPv` i `powGetPv2`.
   - `displayPropertyIncrementalUploadPeriod` = 2000 ms (przyrost co 2 s)
   - `displayPropertyFullUploadPeriod` = 120000 ms (pełny co 2 min)

## Pola telemetryczne (Stream Microinverter) — USTALONE
- `powGetPv`  — moc panelu **PV1** [W]
- `powGetPv2` — moc panelu **PV2** [W]
- **Całkowita moc** = `powGetPv + powGetPv2` (≈ `gridConnectionPower`, różnica = straty DC→AC)
- `gridConnectionPower` — moc oddawana do sieci [W]
- `gridConnectionVol` / `gridConnectionFreq` / `gridConnectionAmp` — sieć: V / Hz / A
- `plugInInfoPvVol`  / `plugInInfoPvAmp`  — PV1: napięcie / prąd
- `plugInInfoPv2Vol` / `plugInInfoPv2Amp` — PV2: napięcie / prąd
- `invNtcTemp3` — temperatura inwertera [°C]
- `moduleWifiRssi` — sygnał WiFi [dBm]
- `gridCodeSelection` — np. `GRID_STD_CODE_POLAND`
- `gridConnectionSta` — np. `PANEL_FEED_GRID`
- (brak osobnego pola energii dziennej w strumieniu — do ewentualnego policzenia
  po stronie telefonu przez całkowanie mocy w czasie, jeśli zechcemy)

## Rate limit
- Dotyczy HTTP (cert/list). MQTT to push — łączymy się rzadko, na żądanie.
