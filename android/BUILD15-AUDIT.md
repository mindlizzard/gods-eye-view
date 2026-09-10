# GOD'S EYE Android native audit - Build 15

## Root causes found

1. The Map3DView was owned by a Composable and Build 14 could recreate it with a Compose `key`.
   Google documents that only one active Map3DView is supported and map state is shared/persistent
   across Map3DView instances. The view is now owned once by MainActivity.

2. Lifecycle forwarding was incomplete. The old host did not forward `onSaveInstanceState()` or
   `onLowMemory()`. Build 15 forwards the full Map3DView lifecycle directly from the Activity.

3. Live aircraft rendering competed with camera gestures. Build 13/14 could call same-ID
   `addMarker()` many times per second while pinch/rotate/tilt was active. Build 15 performs zero
   marker mutations during camera movement.

4. The previous "camera-aware" attempt used the wrong API names. Maps 3D 0.2.2 has
   `setCameraChangedListener()` and `getCamera()`. Build 15 uses those actual 0.2.2 APIs.

5. The fixed 55/20/35 object caps were not camera-aware. Build 15 uses camera range and center:
   globe views use tiny distributed samples; local views use the nearest contacts.

6. Build 14 incorrectly reduced maxAltitude because official samples often use 1,000 km.
   Google's API documentation allows global camera range up to about 63,000 km. Build 15 restores
   global viewing and adds a one-tap globe camera at 20,000 km range.

7. The OpenSky civilian feed is still intentionally bounded to Western Europe in this port.
   That is a data-coverage limitation, not a map limitation. Worldwide civilian aircraft should
   be added later with camera-bounded queries or a cache/proxy instead of hammering the global
   OpenSky endpoint from every phone.

## Stability policy

- Camera moving: 0 marker add/remove/update operations.
- Globe range >= 8,000 km: no interpolated background-aircraft renderer updates.
- Lower ranges: renderer updates are budgeted.
- The predicted positions still advance internally, so contacts catch up after the camera stops.
- Far-side objects remain occluded.

## Diagnostic line

Open Layers. The cyan line shows:
`CAMERA/TILES/IDLE · camera range in km · native object count`

If a future build appears frozen, that line lets us distinguish a camera/render issue from
an app/UI issue without ADB or root.
