#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git add \
  android/app/src/main/java/com/mindlizzard/godseye/MainActivity.kt \
  android/BUILD14.md \
  scripts/apply-build14.sh

if git diff --cached --quiet; then
  echo "Geen build-14 wijzigingen gevonden."
  exit 0
fi

git commit -m "fix(android): cap unstable far zoom and add renderer reset"
git push

echo
echo "Build 14 gepusht. GitHub Actions start automatisch."
echo "Zeg daarna in ChatGPT: check"
