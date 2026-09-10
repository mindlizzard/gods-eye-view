# Build 11: small icons, smoother movement, true-track direction

Changes:
- Aircraft/military symbols reduced to 8dp.
- Aircraft symbol nose points along the reported true track.
- Direction is quantized into 16 headings (22.5-degree steps) to keep the Maps 3D marker layer light.
- Heading icon changes only when a fresh telemetry fix crosses into a new direction bucket.
- Aircraft movement is dead-reckoned at 4 Hz between network fixes.
- Real-world speed is preserved.
- Earthquake, ISS and rocket symbols are also reduced to phone-friendly sizes.
- ISS is no longer constrained by the old aircraft-oriented movement cap.

This mirrors the original God's Eye approach conceptually: position and heading come from the live flight feed,
while local motion continues smoothly between network refreshes.
