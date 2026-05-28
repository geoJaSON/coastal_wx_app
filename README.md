# Coastal WX

A personal Android weather app focused on US coastal and tropical conditions.

## Data sources

- **Tomorrow.io** — hourly + 5-day forecast (temperature, wind, gust, humidity, dewpoint, UV, visibility, pressure, precipitation, sunrise/sunset). Requires a personal API key.
- **National Weather Service** (`api.weather.gov`) — official active alerts by point. No API key required.
- **NOAA National Hurricane Center** (`nhc.noaa.gov`) — current Atlantic and Pacific storms.
- **NOAA Tides & Currents** (`tidesandcurrents.noaa.gov`) — tide predictions from the nearest station.
- **OpenStreetMap Nominatim** — forward and reverse geocoding for the city search box and the GPS "Current Location" label.

No Google Play Services, no Firebase, no analytics, no telemetry. Device location uses the AOSP `LocationManager` so the app runs on GMS-free Android.

## Run locally

**Prerequisites:** Android Studio.

1. Open the project in Android Studio and let it import.
2. Create a `.env` file in the project root with your Tomorrow.io key:
   ```
   GEMINI_API_KEY=ignored
   TOMORROW_API_KEY=your_key_here
   ```
   The key is loaded via the Secrets Gradle plugin, but you'll set it again at runtime inside Settings (it's stored in `EncryptedSharedPreferences` keyed to the device).
3. Build and run on an emulator or device. Debug builds work with no extra setup — AGP generates a debug keystore automatically.
4. *(Optional, for a properly-signed release APK):* generate an upload keystore and set these env vars before running `./gradlew assembleRelease`:
   ```
   KEYSTORE_PATH=/path/to/my-upload-key.jks
   STORE_PASSWORD=...
   KEY_ALIAS=upload
   KEY_PASSWORD=...
   ```
   If the keystore isn't present, the release build still succeeds but is debug-signed — fine for sideloading, not for Play Store upload.

## Tomorrow.io free-tier budget

The free tier is 500 calls/day. The app makes one forecast call per active location per refresh. With the default 15-minute interval that's 96 calls/day — plenty of headroom even if you switch between locations several times a day.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | API calls |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | "Use my current location" button only — never queried in the background |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Optional status-bar temperature notification |
| `POST_NOTIFICATIONS` | Severe weather alerts and storm proximity warnings |
