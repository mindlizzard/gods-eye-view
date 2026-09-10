#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git add \
  android/app/src/main/java/com/mindlizzard/godseye/MainActivity.kt \
  android/app/src/main/java/com/mindlizzard/godseye/LiveLayerController.kt \
  android/BUILD7.md \
  scripts/apply-build7.sh

if git diff --cached --quiet; then
  echo "Geen nieuwe build-7 wijzigingen gevonden."
  exit 0
fi

git commit -m "feat(android): native live intelligence layers"
git push

echo
echo "Build 7 is gepusht. GitHub Actions start automatisch."
echo "Zeg daarna in ChatGPT: check"
