#!/usr/bin/env bash
# Runs FirestoreSyncEmulatorTest on the connected Android emulator against the Firestore and Auth
# emulators (project demo-owlsplit, real rules). The Android emulator reaches them on 10.0.2.2.
set -euo pipefail
cd "$(dirname "$0")/../firebase"
# emulators:exec runs the script with the platform shell (cmd on Windows).
if [[ "${OS:-}" == "Windows_NT" ]]; then GRADLE='..\gradlew.bat'; else GRADLE='../gradlew'; fi
npx -y firebase-tools emulators:exec --only firestore,auth --project demo-owlsplit \
  "$GRADLE -p .. :data:remote:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=es.sebas1705.owlsplit.data.remote.FirestoreSyncEmulatorTest"
