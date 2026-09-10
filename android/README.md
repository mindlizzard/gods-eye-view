# God's Eye Native Android

This folder is the native Android rewrite of God's Eye View.

## Architecture

- Kotlin
- Jetpack Compose
- Google Maps 3D SDK for Android
- No WebView and no local web server
- Existing web project stays available as a reference for data-source behavior

The Maps 3D Android SDK is currently experimental/pre-GA, so the map engine is deliberately isolated behind `Map3DHost` so it can be replaced later if needed.

## API key

Create `android/secrets.properties` locally:

```properties
MAPS3D_API_KEY=YOUR_KEY_HERE
```

Do not commit this file.

For GitHub Actions, add a repository secret named:

`MAPS3D_API_KEY`

Without a real key the APK can compile, but the 3D map will show an initialization error at runtime.

## Build

GitHub Actions builds a debug APK automatically when `android/**` changes on the `android-native` branch.

The uploaded artifact is named:

`GodsEye-debug-apk`

## Planned native layers

1. Aircraft / military aircraft
2. Satellites and ISS
3. AIS ships
4. Earthquakes / fires / infrastructure
5. UAP reports and historical sightings
6. Correlation engine: UAP vs aircraft vs satellites vs time/location
7. Timeline/replay and saved investigations
