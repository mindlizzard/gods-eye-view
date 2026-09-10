# Native Android feature parity plan

Goal: bring the native Android edition of God's Eye View to feature parity with the upstream browser project, then add Android-first UAP investigation features.

## Upstream data layers to port

1. Flights
2. Military flights
3. Earthquakes
4. Satellites
5. Rocket launches
6. Traffic
7. CCTV / public cameras
8. Radio
9. Bikeshare
10. AIS live vessels
11. Military installations
12. Military awareness
13. Datacenters
14. Dams
15. Submarine cables
16. NASA FIRMS active fires

## Upstream interaction / presentation features to port

- Cockpit view
- Nearby contacts roster
- Click-to-track + trails + metadata
- Annotations / whiteboard
- Per-class 3D aircraft models
- Sensor looks: CRT, NVG, FLIR / thermal, Noir, Snow
- Detection overlay
- Military HUD
- Global Context
- Scene director / cinematic camera tours
- Shareable scene state
- Reset Globe
- Voice / AI scene control where practical on Android

## Android-first additions

- UAP / UFO sightings layer
- Historical hotspot layer
- UAP case details
- Correlation engine: sighting vs aircraft vs satellites vs location/time
- Saved investigations
- Timeline / replay
- Dutch-first mobile UI
- Optional notification/watch features later

## Migration approach

The browser app uses CesiumJS and browser-specific modules. Android uses Kotlin, Jetpack Compose and the native Maps 3D SDK, so the data sources and behavior are ported rather than embedding the web app. Browser-only rendering features will get native equivalents instead of a WebView fallback.

Provider licensing, attribution, rate limits and API-key requirements remain in force in the native edition.
