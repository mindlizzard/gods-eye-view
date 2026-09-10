#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git add \
  android/app/src/main/java/com/mindlizzard/godseye/LiveLayerController.kt \
  android/BUILD12-HOTFIX.md \
  scripts/apply-build12-hotfix.sh

if git diff --cached --quiet; then
  echo "Geen build-12 hotfix-wijzigingen gevonden."
  exit 0
fi

git commit -m "fix(android): Maps3D 0.2.2 compatible mobile LOD"
git push

echo
echo "Build 12 hotfix gepusht. GitHub Actions start automatisch."
echo "Zeg daarna in ChatGPT: check"
