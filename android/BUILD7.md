# God's Eye Android - Build 7 live layers

This patch adds the first real native intelligence layers on top of the working Maps 3D globe.

Live in this patch:
- OpenSky civilian flights (Western Europe starter bbox)
- adsb.lol military aircraft
- USGS earthquakes
- ISS live position
- The Space Devs upcoming launches

UI:
- Native live-layer drawer with toggles
- Per-layer loading/error/count status
- Clickable map contacts with a detail card
- ISS quick toggle on the bottom rail
- Original upstream layer roadmap visible in the drawer

The remaining upstream data layers are intentionally ported in batches to keep each Android build testable.
