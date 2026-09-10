#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git add \
  android/app/src/main/java/com/mindlizzard/godseye/LiveLayerController.kt \
  android/app/src/main/res/drawable/ic_contact_aircraft.xml \
  android/app/src/main/res/drawable/ic_contact_military.xml \
  android/app/src/main/res/drawable/ic_contact_quake.xml \
  android/app/src/main/res/drawable/ic_contact_satellite.xml \
  android/app/src/main/res/drawable/ic_contact_rocket.xml \
  android/BUILD9.md \
  scripts/apply-build9.sh

if git diff --cached --quiet; then
  echo "Geen build-9 wijzigingen gevonden."
  exit 0
fi

git commit -m "feat(android): moving contacts and tactical map icons"
git push

echo
echo "Build 9 gepusht. GitHub Actions start automatisch."
echo "Zeg in ChatGPT: check"
