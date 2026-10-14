# P2 · Firebase dev + GitLive smoke test — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Create the development Firebase project, store its configuration in Doppler and prove with an instrumented test that the GitLive KMP SDK performs anonymous authentication, a write and a real-time listener against real Firestore from Android, and that it builds for iOS.

**Architecture:** New `data:remote` module, which will host Firestore access in P6. In P2 it only contains the GitLive dependencies and an instrumented test that initializes Firebase with options passed as instrumentation arguments (no `google-services.json` in the module). The app applies the `google-services` plugin only when the file exists, so CI keeps building without secrets.

**Tech Stack:** `dev.gitlive:firebase-auth`/`firebase-firestore` 2.7.0, Google Services plugin 4.5.0, AndroidX Test (runner 1.7.0, ext-junit 1.3.0), firebase-tools (`npx`), Doppler CLI.

**Platform:** KMP (Android + iOS)

**Depends on:** P1 done.

---

## Context for the executor

- Same environment as P1 (`JAVA_HOME`, `ANDROID_HOME`). Repo at `~/Documents/Repos/Owlsplit`.
- Actions on the Google/Firebase account are done with the user's session in `npx firebase-tools`. **Steps marked 👤 are done by the user** (interactive sign-in and enabling providers in the console); the agent asks the user to do them and waits for confirmation.
- If the id `owlsplit-dev` is taken, use `owlsplit-dev-<4 letters>` and substitute it in **every** command and in `docs/FIREBASE.md`.
- Per-environment identifiers (fixed here and used by P5–P11):

| Environment | Suffix | Android applicationId / iOS bundle id |
|---|---|---|
| development | `.dev` | `es.sebas1705.owlsplit.dev` |
| staging | `.stg` | `es.sebas1705.owlsplit.stg` |
| production | — | `es.sebas1705.owlsplit` |

## File map

| Action | Path |
|---|---|
| Modify | `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `composeApp/build.gradle.kts`, `.gitignore`, `docs/FIREBASE.md`, `MODULE_MAP.md`, `docs/plans/README.md` |
| Create | `data/remote/build.gradle.kts` |
| Create | `data/remote/src/androidInstrumentedTest/kotlin/es/sebas1705/owlsplit/data/remote/FirebaseSmokeTest.kt` |
| Create | `firebase/firebase.json`, `firebase/.firebaserc`, `firebase/firestore.rules`, `firebase/firestore.indexes.json` |
| Create | `scripts/doppler-sync-local.sh`, `scripts/run-firebase-smoke.sh` |

---

### Task 1: Firebase project `owlsplit-dev`

- [ ] **Step 1: 👤 Sign in to firebase-tools**

Ask the user to run this in their terminal and confirm:
```bash
npx firebase-tools login
```
Check: `npx firebase-tools login:list` shows their account.

- [ ] **Step 2: Create the project**

```bash
npx firebase-tools projects:create owlsplit-dev --display-name "Owlsplit Dev"
```
Expected: `Your Firebase project is ready!`. Spark plan (no billing).

- [ ] **Step 3: Create the Firestore database in Europe**

```bash
npx firebase-tools firestore:databases:create "(default)" --location eur3 --project owlsplit-dev
```
Expected: `Successfully created projects/owlsplit-dev/databases/(default)`.

- [ ] **Step 4: 👤 Enable anonymous authentication**

Ask the user: Firebase console → project *Owlsplit Dev* → Authentication → *Comenzar* (Get started) → *Sign-in method* → **Anónimo** (Anonymous) → Habilitar (Enable) → Guardar (Save). (Google and Apple are enabled in P8.)

- [ ] **Step 5: Register the development Android and iOS apps**

```bash
npx firebase-tools apps:create ANDROID "Owlsplit Dev" --package-name es.sebas1705.owlsplit.dev --project owlsplit-dev
npx firebase-tools apps:create IOS "Owlsplit Dev" --bundle-id es.sebas1705.owlsplit.dev --project owlsplit-dev
npx firebase-tools apps:list --project owlsplit-dev
```
Expected: two apps listed; note their **App IDs** (`1:…:android:…` and `1:…:ios:…`).

- [ ] **Step 6: Download the configurations to a temporary directory** (never into the repo)

```bash
T="$(mktemp -d)"
npx firebase-tools apps:sdkconfig ANDROID <ANDROID_APP_ID> --project owlsplit-dev > "$T/google-services.json"
npx firebase-tools apps:sdkconfig IOS <IOS_APP_ID> --project owlsplit-dev > "$T/GoogleService-Info.plist"
grep -o '"current_key": "[^"]*"' "$T/google-services.json" | head -1
```
Expected: a `current_key` (API key) is shown. If the first line of the file is a CLI message instead of JSON, delete it.

### Task 2: Doppler

- [ ] **Step 1: Create the Doppler project and load the `dev` secrets**

```bash
doppler projects create owlsplit --description "Owlsplit app config"
doppler secrets set --project owlsplit --config dev \
  GOOGLE_SERVICES_JSON="$(cat "$T/google-services.json")" \
  GOOGLE_SERVICE_INFO_PLIST="$(cat "$T/GoogleService-Info.plist")" \
  FIREBASE_PROJECT_ID="owlsplit-dev" \
  FIREBASE_ANDROID_APP_ID="<ANDROID_APP_ID>" \
  FIREBASE_IOS_APP_ID="<IOS_APP_ID>" \
  FIREBASE_API_KEY="$(grep -o '"current_key": "[^"]*"' "$T/google-services.json" | head -1 | cut -d'"' -f4)"
doppler secrets --project owlsplit --config dev --only-names
rm -rf "$T"
```
Expected: the six names listed. The `stg` and `prd` configs exist and are empty (filled in P11).

- [ ] **Step 2: Local sync script**

Create `scripts/doppler-sync-local.sh`:
```bash
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
```

```bash
chmod +x scripts/doppler-sync-local.sh
ls iosApp/iosApp/ >/dev/null && ./scripts/doppler-sync-local.sh dev
git status --short
```
Expected: the script writes both files and `git status` does **not** show them (they are already in `.gitignore` since P1). If `iosApp/iosApp` does not exist, find the real folder with `ls iosApp` and fix the path in the script.

- [ ] **Step 3: Commit**

```bash
git add scripts/doppler-sync-local.sh
git commit -m "chore: add Doppler sync script for Firebase config"
```

### Task 3: Per-environment application id and google-services plugin

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `composeApp/build.gradle.kts`

- [ ] **Step 1: Catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:
```toml
gitlive-firebase        = "2.7.0"
google-services         = "4.5.0"
androidx-test-runner    = "1.7.0"
androidx-test-core      = "1.7.0"
androidx-test-ext-junit = "1.3.0"
```
to `[libraries]`:
```toml
gitlive-firebase-auth      = { module = "dev.gitlive:firebase-auth",      version.ref = "gitlive-firebase" }
gitlive-firebase-firestore = { module = "dev.gitlive:firebase-firestore", version.ref = "gitlive-firebase" }
androidx-test-runner       = { module = "androidx.test:runner",           version.ref = "androidx-test-runner" }
androidx-test-core         = { module = "androidx.test:core",             version.ref = "androidx-test-core" }
androidx-test-ext-junit    = { module = "androidx.test.ext:junit",        version.ref = "androidx-test-ext-junit" }
```
and to `[plugins]`:
```toml
google-services = { id = "com.google.gms.google-services", version.ref = "google-services" }
```

- [ ] **Step 2: Declare the plugin in the root project**

In `build.gradle.kts`, inside `plugins { }`, add:
```kotlin
    alias(libs.plugins.google.services)       apply false
```

- [ ] **Step 3: Id suffix and conditional google-services in `composeApp/build.gradle.kts`**

Right after the `require(appEnvironment in templetryProfiles) { … }` block, add:
```kotlin
// One Firebase app per environment: development and staging install side by side
// with production thanks to the applicationId suffix.
val applicationIdSuffixByEnv = mapOf("development" to ".dev", "staging" to ".stg", "production" to "")

android {
    defaultConfig {
        applicationIdSuffix = applicationIdSuffixByEnv.getValue(appEnvironment).ifEmpty { null }
    }
}

// The Firebase config comes from Doppler (scripts/doppler-sync-local.sh) and is git-ignored.
// Without it the app still builds (CI, fresh clones) but Firebase is not initialised.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.warn("composeApp/google-services.json missing: building without Firebase config")
}
```

- [ ] **Step 4: Build with and without the configuration**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | grep -E "BUILD|google-services"
mv composeApp/google-services.json /tmp/gs.json
./gradlew :composeApp:assembleDebug 2>&1 | grep -E "BUILD|google-services"
mv /tmp/gs.json composeApp/google-services.json
```
Expected: both `BUILD SUCCESSFUL`; the second shows the `google-services.json missing` warning. If the first fails with `No matching client found for package name 'es.sebas1705.owlsplit.dev'`, the Android app from Task 1 was registered with another package: fix it.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts composeApp/build.gradle.kts
git commit -m "build: per-environment application id and optional google-services"
```

### Task 4: `data:remote` module with GitLive

**Files:**
- Create: `data/remote/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, replace `include(":data:local")` with:
```kotlin
include(":data:local", ":data:remote")
```

- [ ] **Step 2: `data/remote/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kmp.data)
}

kotlin {
    androidTarget {
        // Instrumented tests share the `test` source-set tree so they can live in
        // androidInstrumentedTest and run on a device with connectedDebugAndroidTest.
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
```

- [ ] **Step 3: Build Android**

```bash
./gradlew :data:remote:assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`. If it fails because of a Kotlin metadata or AGP incompatibility with GitLive 2.7.0, **stop and report**: that is exactly the risk this plan measures (see Task 8).

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts data/remote/build.gradle.kts
git commit -m "build: add data:remote module with GitLive Firebase"
```

### Task 5: Temporary Firestore rules for the test

**Files:**
- Create: `firebase/firebase.json`, `firebase/.firebaserc`, `firebase/firestore.rules`, `firebase/firestore.indexes.json`

- [ ] **Step 1: firebase-tools configuration**

`firebase/firebase.json`:
```json
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "emulators": {
    "firestore": { "port": 8080 },
    "auth": { "port": 9099 },
    "singleProjectMode": true
  }
}
```
`firebase/.firebaserc`:
```json
{
  "projects": {
    "dev": "owlsplit-dev"
  }
}
```
`firebase/firestore.indexes.json`:
```json
{ "indexes": [], "fieldOverrides": [] }
```

- [ ] **Step 2: Minimal rules** (P5 replaces them with the real ones)

`firebase/firestore.rules`:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // P2 smoke test only: each user may read and write its own document.
    match /smoke/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

- [ ] **Step 3: Deploy to dev**

```bash
cd firebase && npx firebase-tools deploy --only firestore --project dev && cd ..
```
Expected: `Deploy complete!`.

- [ ] **Step 4: Commit**

```bash
git add firebase
git commit -m "chore: firebase config and temporary smoke-test rules"
```

### Task 6: Instrumented smoke test

**Files:**
- Create: `data/remote/src/androidInstrumentedTest/kotlin/es/sebas1705/owlsplit/data/remote/FirebaseSmokeTest.kt`
- Create: `scripts/run-firebase-smoke.sh`

- [ ] **Step 1: Write the test**

```kotlin
package es.sebas1705.owlsplit.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
data class SmokeDoc(val nonce: String)

/**
 * Proves the GitLive SDK works end to end against the real dev project:
 * anonymous sign-in, a write, and a real-time listener that sees it.
 *
 * Firebase options arrive as instrumentation arguments (see scripts/run-firebase-smoke.sh),
 * so no google-services.json is needed in this module.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseSmokeTest {

    private fun arg(name: String): String =
        requireNotNull(InstrumentationRegistry.getArguments().getString(name)) {
            "Missing instrumentation argument '$name' — run scripts/run-firebase-smoke.sh"
        }

    @Test
    fun anonymousAuthWriteAndListen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = Firebase.initialize(
            context,
            FirebaseOptions(
                applicationId = arg("firebaseAppId"),
                apiKey = arg("firebaseApiKey"),
                projectId = arg("firebaseProjectId"),
            ),
            name = "smoke-${Random.nextInt(1_000_000)}",
        )
        val auth = Firebase.auth(app)
        val firestore = Firebase.firestore(app)

        val uid = assertNotNull(auth.signInAnonymously().user).uid
        val doc = firestore.collection("smoke").document(uid)
        val nonce = Random.nextLong().toString()

        try {
            doc.set(SmokeDoc(nonce))
            val seen = withTimeout(20_000) {
                doc.snapshots.first { it.exists && it.data<SmokeDoc>().nonce == nonce }
            }
            assertEquals(nonce, seen.data<SmokeDoc>().nonce)
        } finally {
            doc.delete()
            auth.currentUser?.delete()
        }
    }
}
```

- [ ] **Step 2: Script to run it with the Doppler values**

`scripts/run-firebase-smoke.sh`:
```bash
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
```
```bash
chmod +x scripts/run-firebase-smoke.sh
```

- [ ] **Step 3: Run it with the emulator started** (the one from P1, `owlsplit_api36`)

```bash
"$ANDROID_HOME/platform-tools/adb" devices
./scripts/run-firebase-smoke.sh 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL` and `Finished 1 tests`. The report is at `data/remote/build/reports/androidTests/connected/debug/index.html`.

Possible failures:

| Symptom | Cause and action |
|---|---|
| `PERMISSION_DENIED` on `set` | Rules not deployed or wrong `projectId`: repeat Task 5 and check `FIREBASE_PROJECT_ID` |
| `ADMIN_ONLY_OPERATION` / `OPERATION_NOT_ALLOWED` on `signInAnonymously` | Anonymous provider not enabled: repeat Task 1, Step 4 |
| `TimeoutCancellationException` | The listener receives nothing: SDK bug → Task 8 |
| `NoSuchMethodError` / `ClassNotFoundException` from `com.google.firebase` | GitLive ↔ Firebase BoM incompatibility → Task 8 |

- [ ] **Step 4: Check that nothing is left in Firestore**

```bash
npx firebase-tools firestore:delete smoke --recursive --project owlsplit-dev --force 2>&1 | tail -1
```
Expected: there were no documents (the test deletes its own) or it deletes them.

- [ ] **Step 5: Commit**

```bash
git add data/remote/src scripts/run-firebase-smoke.sh
git commit -m "test: GitLive smoke test against the dev Firebase project"
```

### Task 7: iOS build with GitLive in CI

- [ ] **Step 1: Make `composeApp` depend on `data:remote`** (so the iOS framework links GitLive)

In `composeApp/build.gradle.kts`, inside `commonMain.dependencies`, after `implementation(project(":data:local"))`, add:
```kotlin
            implementation(project(":data:remote"))
```

- [ ] **Step 2: Build Android and open a PR so the iOS job runs**

```bash
./gradlew :composeApp:assembleDebug | tail -1
git switch -c p2/firebase-smoke
git add composeApp/build.gradle.kts
git commit -m "build: link data:remote into the app"
git push -u origin p2/firebase-smoke
gh pr create --title "P2: Firebase dev + GitLive smoke test" --body "Implements docs/plans/2026-10-07-p02-firebase-smoke.md"
gh pr checks --watch
```
Expected: `Android` and `iOS framework` green. The frameworks are **static** (`isStatic = true`), so the Firebase iOS SDK symbols are not resolved here but when the app is linked in Xcode; the job only checks that the GitLive Kotlin code compiles for iOS. If it still fails (for example `Undefined symbols … FIR…` or a GitLive cinterop error), **stop and report** with the log: it feeds the Task 8 decision. Adding the Firebase iOS SDK to the `iosApp` project (Swift Package Manager) needs Xcode and is part of the P11 checklist.

- [ ] **Step 3: Merge**

With the checks green:
```bash
gh pr merge --merge --delete-branch
git switch main && git pull
```

### Task 8: GitLive decision

- [ ] **Step 1: Record the result**

If Tasks 4, 6 and 7 passed: append to `docs/adr/0002-firebase-gitlive.md`:
```markdown
## Validation (P2, <date>)
Smoke test passed with GitLive 2.7.0: anonymous auth, write and listener on Android against `owlsplit-dev`; iOS framework built in CI. Running on an iOS device is pending a Mac (P11 checklist).
```
If any of them failed in a way that cannot be fixed: **do not continue with P6**. Write the exact error in `docs/adr/0002-firebase-gitlive.md` under `## Validation` and tell the user the two ways out given in the ADR: (a) try GitLive `3.0.0-alpha02`; (b) implement `data:remote` on top of the Firestore REST API with Ktor. The choice is the user's.

- [ ] **Step 2: Update the documentation**

- `docs/FIREBASE.md`, projects table: `development` row with the real id and the Android and iOS App IDs.
- `MODULE_MAP.md`: add `| data:remote | Firebase (GitLive): auth and Firestore | P6, P8 |`.
- `docs/plans/README.md`: status of P2 → `Done`.

```bash
git add docs MODULE_MAP.md
git commit -m "docs: record P2 smoke test result"
git push
```

## Final verification of P2

- `./scripts/run-firebase-smoke.sh` → 1 test passed against `owlsplit-dev`.
- The app builds with and without `google-services.json`; with it, it installs as `es.sebas1705.owlsplit.dev`.
- Green CI on the PR, including the iOS job.
- No secrets in git: `git log -p | grep -c "current_key"` → `0`.
