#!/usr/bin/env bash
# One-off (P11b): creates the release keystore, stores it in Doppler (owlsplit/prd) and registers
# its fingerprints on the production Android app. Run it once, by hand: the keystore can never be
# replaced once a build signed with it is distributed.
#
#   scripts/setup-release-keystore.sh [path]      (default: ~/Documents/owlsplit-release.jks)
#
# Needs: keytool (JDK 21), doppler (logged in), npx firebase-tools (logged in).
# Afterwards add the printed SHA-256 to firebase/hosting/.well-known/assetlinks.json (package
# es.sebas1705.owlsplit) and redeploy Hosting to prd, so App Links work on release builds.
set -euo pipefail

KEYSTORE="${1:-$HOME/Documents/owlsplit-release.jks}"
ALIAS="owlsplit"
PROD_ANDROID_APP="1:596963610720:android:44ad7ca230b5953346f305"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

if [ -e "$KEYSTORE" ]; then
  echo "Refusing to overwrite $KEYSTORE" >&2
  exit 1
fi
if doppler secrets get SIGNING_KEYSTORE_BASE64 --project owlsplit --config prd --plain >/dev/null 2>&1; then
  echo "Doppler owlsplit/prd already has SIGNING_KEYSTORE_BASE64: the release key exists." >&2
  exit 1
fi

# PKCS12 uses one password for the store and the key.
PASSWORD="$(openssl rand -hex 24)"
"$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Owlsplit, O=Sebas1705, C=ES" >/dev/null

doppler secrets set --project owlsplit --config prd --silent \
  SIGNING_KEY_ALIAS="$ALIAS" SIGNING_STORE_PASSWORD="$PASSWORD" SIGNING_KEY_PASSWORD="$PASSWORD" >/dev/null
base64 -w0 "$KEYSTORE" | doppler secrets set SIGNING_KEYSTORE_BASE64 --project owlsplit --config prd --silent >/dev/null

FINGERPRINTS="$("$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass "$PASSWORD" -alias "$ALIAS")"
unset PASSWORD
SHA1="$(echo "$FINGERPRINTS" | awk '/SHA1:/ {print $2}')"
SHA256="$(echo "$FINGERPRINTS" | awk '/SHA256:/ {print $2}')"

for sha in "$SHA1" "$SHA256"; do
  # On Windows Node can abort while exiting after it succeeded: check the list instead of the exit code.
  npx --yes firebase-tools apps:android:sha:create "$PROD_ANDROID_APP" "$sha" --project owlsplit >/dev/null 2>&1 || true
done

echo "Release keystore: $KEYSTORE (keep a copy outside this PC)"
echo "Stored in Doppler owlsplit/prd: SIGNING_KEYSTORE_BASE64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD"
echo "SHA-1:   $SHA1"
echo "SHA-256: $SHA256"
echo "Registered both on the production Android app. Add the SHA-256 to assetlinks.json (es.sebas1705.owlsplit)."
