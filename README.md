# EcoFlow PV Watch

Produkcja PV z EcoFlow Stream Microinverter na Galaxy Watch 8 (Wear OS).

**Architektura:** apka na telefonie trzyma klucze EcoFlow IoT Open, podpisuje
requesty i cache'uje; apka na zegarku to czysty UI rozmawiający z telefonem
przez Wear OS Data Layer API (lazy fetch).

## Status

- [x] Weryfikacja API + algorytm podpisu (patrz `API_NOTES.md` — podpis GET
      **wbrew dokumentacji** nie zawiera parametrów zapytania!)
- [x] Odkrycie nazw pól PV — telemetria leci przez **MQTT**, nie HTTP (zob. API_NOTES.md)
- [x] Krok 1: apka na telefon — pobiera przez MQTT, pokazuje total + PV1/PV2 na żywo ✅
- [x] Krok 2: moduł `:wear` + komunikacja Data Layer (zegarek ↔ telefon) ✅
- [x] Krok 3: polish — ikonka inwertera w tle, ikonki paneli, paski mocy, wygaszanie ✅
- [x] Ikony launchera (telefon + zegarek): bursztynowe słońce na granacie ✅
- [x] Krok 4: Tile (kafelek) — total + PV1/PV2, cache + odświeżanie z telefonu ✅ (zbudowany)

## Workflow dev (przypomnienie)
Zegarek przez WiFi-adb potrafi się rozłączać po uśpieniu. Reconnect:
`adb connect <IP>:<port>` (IP/port z ekranu Debugowanie bezprzewodowe na zegarku; parowanie zostaje zapisane). Telefon przez USB. Wgranie:
`adb -s <serial> install -r wear/build/outputs/apk/debug/wear-debug.apk`

## Setup

1. Skopiuj `secrets.properties.example` → `secrets.properties`, wpisz klucze
   z [developer.ecoflow.com](https://developer.ecoflow.com). Plik jest w
   `.gitignore` — nie commituj kluczy.
2. Otwórz projekt w Android Studio (File → Open → ten folder), poczekaj na sync.
3. Telefon: włącz Opcje programisty + Debugowanie USB, podłącz kablem
   (albo Wireless debugging), wybierz urządzenie i Run ▶ na konfiguracji `app`.

Build z terminala: `./gradlew assembleDebug`
APK ląduje w `app/build/outputs/apk/debug/app-debug.apk`.

## Narzędzia pomocnicze (Python, bez zależności)

- `discover.py` — dump listy urządzeń + pełnej telemetrii `quota/all`.
  Klucze przez env: `ECOFLOW_ACCESS_KEY`, `ECOFLOW_SECRET_KEY`
  (opcjonalnie `ECOFLOW_SN`, `ECOFLOW_BASE_URL`).
- `signtest.py` — brute-force wariantów podpisu (użyty do ustalenia algorytmu).

## Licencja / dystrybucja

Projekt osobisty; docelowo open source na GitHubie (sideload). Play Store
nieopłacalny: klucze EcoFlow IoT Open są per-developer, brak OAuth dla
końcowych użytkowników.
