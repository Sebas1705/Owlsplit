#!/usr/bin/env bash
# Copies the Firebase app configuration of one environment into its Doppler config, with the
# same names as dev: GOOGLE_SERVICES_JSON, GOOGLE_SERVICE_INFO_PLIST, FIREBASE_PROJECT_ID,
# FIREBASE_ANDROID_APP_ID, FIREBASE_IOS_APP_ID, FIREBASE_API_KEY.
#
#   scripts/setup-firebase-env.sh stg|prd
#
# Run it again after adding SHA fingerprints or enabling Google sign-in: google-services.json
# changes (default_web_client_id appears once Google is on). Needs doppler and npx firebase-tools,
# both logged in.
set -euo pipefail

CONFIG="${1:?usage: setup-firebase-env.sh stg|prd}"
case "$CONFIG" in
  stg)
    PROJECT=owlsplit-stg
    ANDROID_APP=1:438831852571:android:4f65db079a0f2546dd1cbe
    IOS_APP=1:438831852571:ios:3e80230c81d80be5dd1cbe
    ;;
  prd)
    PROJECT=owlsplit
    ANDROID_APP=1:596963610720:android:44ad7ca230b5953346f305
    IOS_APP=1:596963610720:ios:c3a61a92a48f3cd446f305
    ;;
  *) echo "Unknown config '$CONFIG' (stg or prd)" >&2; exit 1 ;;
esac

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# On Windows Node can abort while exiting after writing the file: check the files, not the exit code.
npx --yes firebase-tools apps:sdkconfig android "$ANDROID_APP" --project "$PROJECT" --out "$TMP/google-services.json" >/dev/null 2>&1 || true
npx --yes firebase-tools apps:sdkconfig ios "$IOS_APP" --project "$PROJECT" --out "$TMP/GoogleService-Info.plist" >/dev/null 2>&1 || true
[ -s "$TMP/google-services.json" ] && [ -s "$TMP/GoogleService-Info.plist" ] || { echo "Could not download the app configs of $PROJECT" >&2; exit 1; }

API_KEY="$(node -e 'const j=require(process.argv[1]); process.stdout.write(j.client[0].api_key[0].current_key)' "$TMP/google-services.json")"

doppler secrets set GOOGLE_SERVICES_JSON --project owlsplit --config "$CONFIG" --silent < "$TMP/google-services.json" >/dev/null
doppler secrets set GOOGLE_SERVICE_INFO_PLIST --project owlsplit --config "$CONFIG" --silent < "$TMP/GoogleService-Info.plist" >/dev/null
doppler secrets set --project owlsplit --config "$CONFIG" --silent \
  FIREBASE_PROJECT_ID="$PROJECT" \
  FIREBASE_ANDROID_APP_ID="$ANDROID_APP" \
  FIREBASE_IOS_APP_ID="$IOS_APP" \
  FIREBASE_API_KEY="$API_KEY" >/dev/null

echo "Doppler owlsplit/$CONFIG now has the Firebase config of $PROJECT"
