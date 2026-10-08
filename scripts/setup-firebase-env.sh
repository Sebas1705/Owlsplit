#!/usr/bin/env bash
# Copies the Firebase app configuration of the `owlsplit` project into Doppler (owlsplit/prd):
#  - GOOGLE_SERVICES_JSON: one google-services.json with both Android apps, the release one
#    (es.sebas1705.owlsplit) and the debug one (es.sebas1705.owlsplit.debug).
#  - GOOGLE_SERVICE_INFO_PLIST: the iOS app.
#  - FIREBASE_PROJECT_ID, FIREBASE_ANDROID_APP_ID (release app, for App Distribution),
#    FIREBASE_IOS_APP_ID, FIREBASE_API_KEY.
#
#   scripts/setup-firebase-env.sh
#
# Run it again after registering SHA fingerprints or enabling Google sign-in: google-services.json
# changes (the Google web client appears once Google is on). Needs doppler and npx firebase-tools,
# both logged in. Then `scripts/doppler-sync-local.sh` writes the files for local builds.
set -euo pipefail

PROJECT=owlsplit
ANDROID_APP=1:596963610720:android:44ad7ca230b5953346f305
ANDROID_DEBUG_APP=1:596963610720:android:61aa6038a2186e6a46f305
IOS_APP=1:596963610720:ios:c3a61a92a48f3cd446f305

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# On Windows Node can abort while exiting after writing the file: check the files, not the exit code.
npx --yes firebase-tools apps:sdkconfig android "$ANDROID_APP" --project "$PROJECT" --out "$TMP/release.json" >/dev/null 2>&1 || true
npx --yes firebase-tools apps:sdkconfig android "$ANDROID_DEBUG_APP" --project "$PROJECT" --out "$TMP/debug.json" >/dev/null 2>&1 || true
npx --yes firebase-tools apps:sdkconfig ios "$IOS_APP" --project "$PROJECT" --out "$TMP/GoogleService-Info.plist" >/dev/null 2>&1 || true
for f in release.json debug.json GoogleService-Info.plist; do
  [ -s "$TMP/$f" ] || { echo "Could not download $f of $PROJECT" >&2; exit 1; }
done

# One file, one client per package: the Google Services plugin picks the one of each build type.
node -e '
  const fs = require("fs");
  const [release, debug, out] = process.argv.slice(1);
  const merged = JSON.parse(fs.readFileSync(release));
  merged.client.push(...JSON.parse(fs.readFileSync(debug)).client);
  fs.writeFileSync(out, JSON.stringify(merged, null, 2) + "\n");
' "$TMP/release.json" "$TMP/debug.json" "$TMP/google-services.json"

API_KEY="$(node -e 'const j=require(process.argv[1]); process.stdout.write(j.client[0].api_key[0].current_key)' "$TMP/google-services.json")"

doppler secrets set GOOGLE_SERVICES_JSON --project owlsplit --config prd --silent < "$TMP/google-services.json" >/dev/null
doppler secrets set GOOGLE_SERVICE_INFO_PLIST --project owlsplit --config prd --silent < "$TMP/GoogleService-Info.plist" >/dev/null
doppler secrets set --project owlsplit --config prd --silent \
  FIREBASE_PROJECT_ID="$PROJECT" \
  FIREBASE_ANDROID_APP_ID="$ANDROID_APP" \
  FIREBASE_IOS_APP_ID="$IOS_APP" \
  FIREBASE_API_KEY="$API_KEY" >/dev/null

echo "Doppler owlsplit/prd now has the Firebase config of $PROJECT (Android release + debug, iOS)"
