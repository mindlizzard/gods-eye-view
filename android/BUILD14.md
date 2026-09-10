# Build 14: safe Maps3D camera envelope + hard renderer reset

Field evidence from Build 13:
- The Compose UI remains responsive when the map gets stuck.
- Layer toggles still add/remove contacts on the frozen frame.
- Therefore the whole app is not hanging; the Maps 3D camera/gesture renderer is wedged.
- This also occurs with contacts hidden, so continuing to reduce live-marker counts is not the primary fix.

Important configuration difference:
- Our app allowed `maxAltitude = 10,000,000 m`.
- Google's current official Maps 3D Android samples consistently use `maxAltitude = 1,000,000 m`.
- Maps 3D Android 0.2.2 is still Experimental Preview.

Build 14:
- max camera altitude: 10,000 km -> 1,000 km
- initial camera range: 2,000 km -> 1,000 km
- `3D ↻` button now fully recreates the Map3DView if the renderer ever wedges
- all enabled live layers are restored automatically after recreation

Why aircraft look slow at Europe scale:
A jet around 900 km/h moves ~250 m/s. At a continent-scale view one screen pixel can
represent several kilometres, so true motion may be only about one pixel every 20-30 seconds.
We keep positions physically honest rather than speeding the data up.
