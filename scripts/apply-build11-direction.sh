#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git add \
  android/app/src/main/java/com/mindlizzard/godseye/LiveLayerController.kt \
  android/app/src/main/res/drawable/ic_contact_aircraft_000.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_023.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_045.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_068.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_090.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_113.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_135.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_158.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_180.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_203.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_225.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_248.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_270.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_293.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_315.xml \
  android/app/src/main/res/drawable/ic_contact_aircraft_338.xml \
  android/app/src/main/res/drawable/ic_contact_military_000.xml \
  android/app/src/main/res/drawable/ic_contact_military_023.xml \
  android/app/src/main/res/drawable/ic_contact_military_045.xml \
  android/app/src/main/res/drawable/ic_contact_military_068.xml \
  android/app/src/main/res/drawable/ic_contact_military_090.xml \
  android/app/src/main/res/drawable/ic_contact_military_113.xml \
  android/app/src/main/res/drawable/ic_contact_military_135.xml \
  android/app/src/main/res/drawable/ic_contact_military_158.xml \
  android/app/src/main/res/drawable/ic_contact_military_180.xml \
  android/app/src/main/res/drawable/ic_contact_military_203.xml \
  android/app/src/main/res/drawable/ic_contact_military_225.xml \
  android/app/src/main/res/drawable/ic_contact_military_248.xml \
  android/app/src/main/res/drawable/ic_contact_military_270.xml \
  android/app/src/main/res/drawable/ic_contact_military_293.xml \
  android/app/src/main/res/drawable/ic_contact_military_315.xml \
  android/app/src/main/res/drawable/ic_contact_military_338.xml \
  android/app/src/main/res/drawable/ic_contact_quake.xml \
  android/app/src/main/res/drawable/ic_contact_satellite.xml \
  android/app/src/main/res/drawable/ic_contact_rocket.xml \
  android/BUILD11.md \
  scripts/apply-build11-direction.sh

if git diff --cached --quiet; then
  echo "Geen build-11 richting-wijzigingen gevonden."
  exit 0
fi

git commit -m "feat(android): small moving aircraft with true-track heading"
git push

echo
echo "Build 11 met vliegrichting gepusht. GitHub Actions start automatisch."
echo "Zeg daarna in ChatGPT: check"
