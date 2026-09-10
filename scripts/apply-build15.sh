#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git add \
  android/app/src/main/java/com/mindlizzard/godseye/MainActivity.kt \
  android/app/src/main/java/com/mindlizzard/godseye/LiveLayerController.kt \
  android/BUILD15-AUDIT.md \
  scripts/apply-build15.sh

if git diff --cached --quiet; then
  echo "Geen build-15 wijzigingen gevonden."
  exit 0
fi

git commit -m "fix(android): audit map lifecycle and isolate renderer from camera gestures"
git push

echo
echo "Build 15 gepusht. GitHub Actions start automatisch."
echo "Zeg daarna in ChatGPT: check"
