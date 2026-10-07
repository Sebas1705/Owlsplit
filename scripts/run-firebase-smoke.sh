#!/usr/bin/env bash
# Runs the GitLive smoke test on the connected device/emulator against owlsplit-dev.
set -euo pipefail
cd "$(dirname "$0")/.."
doppler run --project owlsplit --config dev -- bash -c '
  ./gradlew :data:remote:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.firebaseAppId="$FIREBASE_ANDROID_APP_ID" \
    -Pandroid.testInstrumentationRunnerArguments.firebaseApiKey="$FIREBASE_API_KEY" \
    -Pandroid.testInstrumentationRunnerArguments.firebaseProjectId="$FIREBASE_PROJECT_ID"
'
