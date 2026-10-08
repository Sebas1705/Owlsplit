#!/usr/bin/env bash
# Writes the Firebase config files of the `owlsplit` project (Doppler owlsplit/prd) to the paths
# the build expects. Both files are git-ignored.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

doppler secrets get GOOGLE_SERVICES_JSON --project owlsplit --config prd --plain \
  > "$ROOT/composeApp/google-services.json"
mkdir -p "$ROOT/iosApp/iosApp"
doppler secrets get GOOGLE_SERVICE_INFO_PLIST --project owlsplit --config prd --plain \
  > "$ROOT/iosApp/iosApp/GoogleService-Info.plist"

echo "Firebase config of owlsplit written to composeApp/ and iosApp/iosApp/"
