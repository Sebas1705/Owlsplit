#!/usr/bin/env bash
# Writes the Firebase config files of one Doppler config (dev by default) to the
# paths the build expects. Both files are git-ignored.
set -euo pipefail
CONFIG="${1:-dev}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

doppler secrets get GOOGLE_SERVICES_JSON --project owlsplit --config "$CONFIG" --plain \
  > "$ROOT/composeApp/google-services.json"
doppler secrets get GOOGLE_SERVICE_INFO_PLIST --project owlsplit --config "$CONFIG" --plain \
  > "$ROOT/iosApp/iosApp/GoogleService-Info.plist"

echo "Firebase config for '$CONFIG' written to composeApp/ and iosApp/iosApp/"
