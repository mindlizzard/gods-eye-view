#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="mindlizzard/gods-eye-view"

cd "$(git rev-parse --show-toplevel)"

echo "== God's Eye stable Android signing =="

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI ontbreekt. Installeer eerst: pkg install gh -y"
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js ontbreekt. Installeer eerst: pkg install nodejs -y"
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "OpenJDK/keytool installeren..."
  pkg install openjdk-21 -y
fi

SIGN_DIR="$HOME/.godseye-signing"
KEYSTORE="$SIGN_DIR/godseye.jks"
CREDS="$SIGN_DIR/credentials.env"

mkdir -p "$SIGN_DIR"
chmod 700 "$SIGN_DIR"

if [ ! -f "$KEYSTORE" ]; then
  echo "Nieuwe vaste signing key maken..."
  STORE_PASS="$(node -e "process.stdout.write(require('crypto').randomBytes(24).toString('hex'))")"
  KEY_PASS="$STORE_PASS"
  KEY_ALIAS="godseye"

  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype JKS \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 36500 \
    -dname "CN=GodsEye Android,O=Mindlizzard,C=NL" \
    -noprompt

  umask 077
  cat > "$CREDS" <<EOF
GODSEYE_KEYSTORE_PASSWORD='$STORE_PASS'
GODSEYE_KEY_ALIAS='$KEY_ALIAS'
GODSEYE_KEY_PASSWORD='$KEY_PASS'
EOF
  chmod 600 "$CREDS"
else
  echo "Bestaande vaste signing key gevonden."
  if [ ! -f "$CREDS" ]; then
    echo "FOUT: $CREDS ontbreekt. Stop om te voorkomen dat we een andere key gebruiken."
    exit 1
  fi
fi

# shellcheck disable=SC1090
source "$CREDS"

echo "Signing key veilig als GitHub Actions secrets opslaan..."
base64 < "$KEYSTORE" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_B64 -R "$REPO"
printf '%s' "$GODSEYE_KEYSTORE_PASSWORD" | gh secret set GODSEYE_KEYSTORE_PASSWORD -R "$REPO"
printf '%s' "$GODSEYE_KEY_ALIAS" | gh secret set GODSEYE_KEY_ALIAS -R "$REPO"
printf '%s' "$GODSEYE_KEY_PASSWORD" | gh secret set GODSEYE_KEY_PASSWORD -R "$REPO"

echo "Secrets ingesteld."

git add android/app/build.gradle.kts .github/workflows/android-apk.yml scripts/setup-android-stable-signing.sh android/FEATURE_PARITY.md

if git diff --cached --quiet; then
  echo "Geen nieuwe patchwijzigingen om te committen."
else
  git commit -m "fix(android): stable APK signing and feature parity plan"
  git push
fi

echo
echo "Klaar."
echo "LET OP: de eerstvolgende vaste-signed APK vereist nog EEN KEER verwijderen,"
echo "omdat je huidige APK met een tijdelijke GitHub-run key is ondertekend."
echo "Daarna kunnen volgende APK's gewoon als update eroverheen."
echo
echo "Backup lokaal bewaren:"
echo "  $SIGN_DIR"
