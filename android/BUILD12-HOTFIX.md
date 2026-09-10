# Build 12 hotfix

The first Build 12 attempt used camera APIs from Google's newer Maps 3D sample
catalog that are not exposed by the project's current `play-services-maps3d:0.2.2`
binary. This hotfix removes those compile-time camera dependencies.

Performance protection remains:
- 55 civilian flights max
- 20 military flights max
- 35 earthquakes max
- deterministic feed sampling
- far-side markers are not forced through the globe
- same-ID marker upserts for visible movement
- renderer update budget per second (18 civilian, 10 military, 1 ISS)
- every contact's predicted position still advances each tick, so it catches up
  when its renderer slot is refreshed

This is intentionally conservative for a phone-native first pass.
