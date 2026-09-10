# Build 9: moving contacts + tactical icons

Changes:
- Replace generic Google pin markers with lightweight native tactical icons.
- Civil aircraft: cyan aircraft silhouette.
- Military aircraft: amber military silhouette.
- Earthquakes: red quake symbol.
- ISS: purple satellite symbol.
- Upcoming launches: orange rocket symbol.
- Remove permanent labels from dense flight layers to reduce map clutter.
- Keep marker instances alive instead of deleting/recreating the entire layer.
- Dead-reckon aircraft every second using reported speed + track.
- Refresh military fixes every 20s, civilian OpenSky every 60s, ISS every 5s.
- Infer ISS heading from successive fixes, then animate it between API updates.

Design note:
At globe/regional zoom, 2D tactical symbols are deliberately used instead of hundreds
of simultaneous GLB aircraft models. 3D aircraft models should be introduced only for
nearby/selected contacts later, otherwise mobile GPU load and visual clutter become excessive.
