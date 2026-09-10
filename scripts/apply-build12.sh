#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git add \
  android/app/src/main/java/com/mindlizzard/godseye/LiveLayerController.kt \
  android/app/src/main/java/com/mindlizzard/godseye/MainActivity.kt \
  android/BUILD12.md \
  scripts/apply-build12.sh

if git diff --cached --quiet; then
  echo "Geen build-12 wijzigingen gevonden."
  exit 0
fi

git commit -m "fix(android): renderer-safe motion and camera-aware LOD"
git push

echo
echo "Build 12 gepusht. GitHub Actions start automatisch."
echo "Zeg daarna in ChatGPT: check"
