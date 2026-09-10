# Build 12: renderer-safe live motion + mobile LOD

Why build 11 looked static:
- `Marker.setPosition()` compiles in Maps 3D 0.2.2, but the current renderer does not
  reliably redraw that object in place.
- Google's current Maps 3D Compose helper explicitly treats SDK objects as largely immutable
  and updates a marker by calling `addMarker()` again with the same underlying marker ID.

Why zooming far out could freeze:
- We were asking an individual native Maps 3D Marker for hundreds of contacts, then touching
  all of them four times per second.
- `isDrawnWhenOccluded=true` also asked the renderer to draw contacts through the globe.

Build 12:
- same-ID `addMarker()` upserts for live positions
- 1 Hz real-world dead reckoning for visible moving contacts
- camera-aware marker caps (LOD)
- nearest contacts to camera center are retained
- camera LOD changes are debounced by 450 ms
- far-side globe contacts are occluded
- layer UI shows visible contacts versus total feed contacts

This deliberately trades "every raw feed point as an individual Map3D Marker" for a responsive
native mobile globe. Later, a selected aircraft can get a higher-rate 3D model/trail without
forcing every background contact to update at that rate.
